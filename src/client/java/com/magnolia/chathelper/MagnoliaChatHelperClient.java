package com.magnolia.chathelper;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class MagnoliaChatHelperClient implements ClientModInitializer {
	static final Logger LOGGER = LoggerFactory.getLogger("magnolia_chat_helper");
	private static final long MIN_SUBMIT_DELAY_MS = 1_000;
	private static final long MAX_SUBMIT_DELAY_MS = 4_000;
	private static final Pattern WELCOME_BANNER_NAME = Pattern.compile("(?i)\\bwelcome,\\s*[-a-z0-9_]{1,20}!");
	private static final Pattern WELCOME_BANNER_DETAILS = Pattern.compile(
			"(?i)(?:delighted\\s+to\\s+have\\s+you\\s+here|you(?:'|’)?re\\s+adventurer\\s*#\\s*[0-9,]+)");
	private final ChatGameDetector detector = new ChatGameDetector();
	private AnswerMemory memory;
	private HelperSettings settings;
	private Unscrambler unscrambler;
	private UnreverseResolver unreverseResolver;
	private CraftResolver craftResolver;
	private TriviaResolver triviaResolver;
	private OpenAiTriviaClient openAi;
	private MagnoliaUpdater updater;
	private final Deque<String> guessQueue = new ArrayDeque<>();
	private boolean guessing;
	private boolean questionRoundOpen;
	private long nextGuessAt;
	private GamePrompt currentPrompt;
	private List<String> currentAnswers = List.of();
	private String lastProcessedMessage;
	private Instant lastProcessedAt = Instant.EPOCH;
	private boolean apiPromptShown;
	private int joinedTicks;
	private boolean versionNoticeShown;
	private boolean updatePromptShown;
	private boolean waitingForWelcomeDetails;
	private Instant welcomeBannerAt = Instant.EPOCH;
	private final PriorityQueue<PendingWelcome> pendingWelcomes =
			new PriorityQueue<>(Comparator.comparingLong(PendingWelcome::sendAt));
	private final Map<String, PlayerAnswer> recentPlayerAnswers = new LinkedHashMap<>();
	private PendingWinner pendingWinner;

	@Override
	public void onInitializeClient() {
		memory = new AnswerMemory();
		memory.load();
		settings = new HelperSettings();
		settings.load();
		updater = new MagnoliaUpdater();
		unscrambler = Unscrambler.load();
		unreverseResolver = UnreverseResolver.load();
		craftResolver = CraftResolver.load();
		triviaResolver = TriviaResolver.load();
		openAi = new OpenAiTriviaClient(OpenAiConfig.load());
		if (settings.updateChecksEnabled()) {
			updater.check();
		}

		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				onChatMessage(message.getString());
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receivedAt) -> {
			String text = message.getString();
			if (sender == null) {
				// Servers can deliver profileless/disguised chat through this event. There is
				// no GameProfile in that case, so learn from the formatted line instead.
				captureFormattedPlayerAnswer(text);
				return;
			}
			capturePlayerAnswer(sender.name(), text);
		});
		ClientSendMessageEvents.CHAT.register(message -> {
			captureLocalPlayerAnswer(message);
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::sendNextGuess);
		ClientTickEvents.END_CLIENT_TICK.register(this::sendPendingWelcome);
		ClientTickEvents.END_CLIENT_TICK.register(this::handleStartupNotices);
		registerCommands();
		LOGGER.info("Magnolia OPT loaded with {} remembered answers", memory.size());
	}

	private void onChatMessage(String message) {
		if (isDuplicate(message)) {
			return;
		}
		if (detector.isChatGameHeader(message)) {
			resetRoundContext();
		}
		captureFormattedPlayerAnswer(message);
		handleAutoWelcome(message);
		if (!memory.enabled() || detector.isIgnoredEvent(message)) {
			return;
		}

		boolean roundFinished = detector.isRoundFinished(message);
		if (settings.learnFromWinners()) {
			learnFromWinnerAnnouncement(message);
		}
		if (settings.learnFromReveals()) {
			detector.revealedAnswer(message).ifPresent(this::learnConfirmedAnswer);
		}
		if (roundFinished) {
			finishQuestionRound();
		}

		Optional<GamePrompt> detected = detector.detect(message);
		if (detected.isEmpty()) {
			return;
		}

		currentPrompt = detected.get();
		recentPlayerAnswers.clear();
		pendingWinner = null;
		if (currentPrompt.type() == GamePrompt.Type.QUESTION) {
			questionRoundOpen = true;
		}
		Optional<String> remembered = memory.answerFor(currentPrompt);
		if (remembered.isPresent()) {
			currentAnswers = List.of(remembered.get());
			showAnswers("remembered", currentAnswers);
			startGuessing(currentAnswers);
			return;
		}

		if (currentPrompt.type() == GamePrompt.Type.TYPE) {
			currentAnswers = List.of(currentPrompt.clue());
			showAnswers("type", currentAnswers);
			startGuessing(currentAnswers);
			return;
		}
		if (currentPrompt.type() == GamePrompt.Type.UNREVERSE) {
			UnreverseResolver.Decision decision = unreverseResolver.solve(currentPrompt);
			currentAnswers = decision.displayAnswers();
			showAnswers("unreverse", currentAnswers);
			if (decision.confident()) {
				startGuessing(decision.automaticAnswers());
			} else {
				localMessage(Component.literal("[Magnolia OPT] Both reverse directions look possible; auto-submit withheld.")
						.withStyle(ChatFormatting.GOLD));
			}
			return;
		}
		if (currentPrompt.type() == GamePrompt.Type.QUESTION) {
			currentAnswers = triviaResolver.solve(currentPrompt, 8);
			if (openAi.ready()) {
				askOpenAi(currentPrompt, currentAnswers);
			} else if (!currentAnswers.isEmpty()) {
				showAnswers("trivia", currentAnswers);
				startGuessing(currentAnswers);
			} else {
				askOpenAi(currentPrompt, List.of());
			}
			return;
		}
		if (currentPrompt.type() == GamePrompt.Type.CRAFT) {
			currentAnswers = craftResolver.solve(currentPrompt, 5);
			if (currentAnswers.isEmpty()) {
				localMessage(Component.literal("[Magnolia OPT] I don't know this recipe yet: ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.literal(currentPrompt.clue()).withStyle(ChatFormatting.YELLOW))
						.append(". I will learn it when Magnolia reveals the answer, or use /mag remember <answer>."));
			} else {
				showAnswers("recipe", currentAnswers);
				startGuessing(currentAnswers);
			}
			return;
		}

		currentAnswers = unscrambler.solve(currentPrompt.clue(), 5);
		if (currentAnswers.isEmpty()) {
			localMessage(Component.literal("[Magnolia OPT] No dictionary match for '")
					.withStyle(ChatFormatting.GOLD)
					.append(Component.literal(currentPrompt.clue()).withStyle(ChatFormatting.YELLOW))
					.append("'. Use /mag remember <answer> after solving it."));
		} else {
			showAnswers("dictionary", currentAnswers);
			startGuessing(currentAnswers);
		}
	}

	private void resetRoundContext() {
		questionRoundOpen = false;
		stopGuessing();
		currentPrompt = null;
		currentAnswers = List.of();
		recentPlayerAnswers.clear();
		pendingWinner = null;
	}

	private void handleAutoWelcome(String message) {
		if (!settings.autoWelcomeEnabled()) {
			waitingForWelcomeDetails = false;
			return;
		}
		String clean = message.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
		Instant now = Instant.now();
		boolean hasName = WELCOME_BANNER_NAME.matcher(clean).find();
		boolean hasDetails = WELCOME_BANNER_DETAILS.matcher(clean).find();
		if (hasName) {
			waitingForWelcomeDetails = true;
			welcomeBannerAt = now;
		}
		if (waitingForWelcomeDetails && Duration.between(welcomeBannerAt, now).toSeconds() <= 5 && hasDetails) {
			long delay = WelcomeDelay.nextMillis();
			pendingWelcomes.add(new PendingWelcome(System.currentTimeMillis() + delay, delay));
			waitingForWelcomeDetails = false;
		} else if (waitingForWelcomeDetails && Duration.between(welcomeBannerAt, now).toSeconds() > 5) {
			waitingForWelcomeDetails = false;
		}
	}

	private void sendPendingWelcome(Minecraft client) {
		PendingWelcome pending = pendingWelcomes.peek();
		if (pending == null || System.currentTimeMillis() < pending.sendAt() || client.getConnection() == null) {
			return;
		}
		pendingWelcomes.poll();
		if (settings.autoWelcomeEnabled()) {
			client.getConnection().sendCommand("welcome");
			String seconds = String.format(Locale.ROOT, "%.1f", pending.delayMillis() / 1_000.0);
			localMessage(Component.literal("[Magnolia OPT] Sent /welcome after " + seconds + " seconds.")
					.withStyle(ChatFormatting.GREEN));
		}
	}

	private void startGuessing(List<String> answers) {
		guessQueue.clear();
		if (answers.isEmpty()) {
			guessing = false;
			return;
		}
		if (!settings.autoSubmitEnabled()
				|| (currentPrompt != null && currentPrompt.type() == GamePrompt.Type.QUESTION
				&& !settings.autoAnswerQuestions())) {
			guessing = false;
			return;
		}
		int chance = settings.submissionChancePercent();
		if (ThreadLocalRandom.current().nextInt(100) >= chance) {
			guessing = false;
			localMessage(Component.literal("[Magnolia OPT] Auto-submit skipped this round ("
					+ (100 - chance) + "% skip chance).")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		guessQueue.addAll(answers);
		guessing = true;
		nextGuessAt = System.currentTimeMillis() + humanSubmitDelay(guessQueue.getFirst());
	}

	private static long humanSubmitDelay(String answer) {
		int length = answer.codePointCount(0, answer.length());
		long typingDelay = 1_100L + Math.min(length, 28) * 110L;
		long jitter = ThreadLocalRandom.current().nextLong(-250, 351);
		return Math.max(MIN_SUBMIT_DELAY_MS, Math.min(MAX_SUBMIT_DELAY_MS, typingDelay + jitter));
	}

	private void stopGuessing() {
		guessing = false;
		guessQueue.clear();
	}

	private void finishQuestionRound() {
		questionRoundOpen = false;
		stopGuessing();
	}

	private void capturePlayerAnswer(String playerName, String decoratedMessage) {
		if (!memory.enabled() || !settings.learningEnabled() || !settings.learnFromWinners() || currentPrompt == null) {
			return;
		}
		String answer = ChatLearningParser.playerAnswer(playerName, decoratedMessage);
		recordPlayerAnswer(playerName, answer);
	}

	private void captureFormattedPlayerAnswer(String message) {
		if (!memory.enabled() || !settings.learningEnabled() || !settings.learnFromWinners() || currentPrompt == null) {
			return;
		}
		ChatLearningParser.formattedPlayerAnswer(message).ifPresent(candidate -> {
			recordPlayerAnswer(candidate.playerName(), candidate.answer());
		});
	}

	private void captureLocalPlayerAnswer(String answer) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			recordPlayerAnswer(client.player.getName().getString(), answer);
		}
	}

	private void recordPlayerAnswer(String playerName, String answer) {
		if (!memory.enabled() || !settings.learningEnabled() || !settings.learnFromWinners()
				|| currentPrompt == null || answer == null || !currentPrompt.accepts(answer)) {
			return;
		}
		String playerKey = playerName.toLowerCase(Locale.ROOT);
		PlayerAnswer candidate = new PlayerAnswer(currentPrompt.key(), answer.trim(), Instant.now());
		recentPlayerAnswers.put(playerKey, candidate);
		if (pendingWinner != null && pendingWinner.playerName().equals(playerKey)
				&& pendingWinner.promptKey().equals(candidate.promptKey())
				&& Duration.between(pendingWinner.announcedAt(), candidate.receivedAt()).abs().toMillis() <= 1_500) {
			rememberWinnerAnswer(playerName, candidate);
		}
	}

	private void learnFromWinnerAnnouncement(String message) {
		if (!settings.learningEnabled() || currentPrompt == null) {
			return;
		}
		Optional<String> winner = ChatLearningParser.winnerName(message);
		if (winner.isEmpty()) {
			return;
		}
		String winnerKey = winner.get().toLowerCase(Locale.ROOT);
		pendingWinner = new PendingWinner(currentPrompt.key(), winnerKey, Instant.now());
		PlayerAnswer candidate = recentPlayerAnswers.get(winnerKey);
		if (candidate == null || !candidate.promptKey().equals(currentPrompt.key())
				|| Duration.between(candidate.receivedAt(), Instant.now()).toSeconds() > 45
				|| !currentPrompt.accepts(candidate.answer())) {
			return;
		}
		rememberWinnerAnswer(winner.get(), candidate);
	}

	private void rememberWinnerAnswer(String winnerName, PlayerAnswer candidate) {
		if (currentPrompt == null || !candidate.promptKey().equals(currentPrompt.key())
				|| !currentPrompt.accepts(candidate.answer())) {
			return;
		}
		memory.remember(currentPrompt, candidate.answer());
		currentAnswers = List.of(candidate.answer());
		pendingWinner = null;
		LOGGER.info("Replaced memory with confirmed answer '{}' for '{}' from winner {}",
				candidate.answer(), currentPrompt.clue(), winnerName);
	}

	private void askOpenAi(GamePrompt prompt, List<String> fallbackAnswers) {
		if (!openAi.ready()) {
			localMessage(Component.literal("[Magnolia OPT] Unknown question; OpenAI is not configured. ")
					.withStyle(ChatFormatting.GOLD)
					.append(Component.literal("Add your API key to " + OpenAiConfig.path() + " and run /mag api reload.")
							.withStyle(ChatFormatting.YELLOW)));
			return;
		}
		localMessage(Component.literal("[Magnolia OPT] Asking OpenAI: ")
				.withStyle(ChatFormatting.AQUA)
				.append(Component.literal(prompt.clue()).withStyle(ChatFormatting.GRAY)));
		openAi.answer(prompt.clue()).whenComplete((answers, error) -> Minecraft.getInstance().execute(() -> {
			if (!questionRoundOpen || currentPrompt == null || !currentPrompt.key().equals(prompt.key())) {
				return;
			}
			if (error != null) {
				Throwable cause = error.getCause() == null ? error : error.getCause();
				localMessage(Component.literal("[Magnolia OPT] " + cause.getMessage()).withStyle(ChatFormatting.RED));
				if (!fallbackAnswers.isEmpty()) {
					currentAnswers = fallbackAnswers;
					showAnswers("offline fallback", fallbackAnswers);
					startGuessing(fallbackAnswers);
				}
				return;
			}
			currentAnswers = answers;
			showAnswers("OpenAI", answers);
			startGuessing(answers);
		}));
	}

	private void runApiTest() {
		if (!openAi.ready()) {
			localMessage(Component.literal("[Magnolia OPT] API test cannot start: no key is configured. Edit "
					+ OpenAiConfig.path() + " and run /mag api reload.").withStyle(ChatFormatting.RED));
			return;
		}
		localMessage(Component.literal("[Magnolia OPT] Sending API test with " + openAi.model() + "...")
				.withStyle(ChatFormatting.AQUA));
		openAi.answer("How many frog variants are there in Minecraft?").whenComplete((answers, error) ->
				Minecraft.getInstance().execute(() -> {
					if (error != null) {
						Throwable cause = error.getCause() == null ? error : error.getCause();
						localMessage(Component.literal("[Magnolia OPT] API TEST FAILED: " + cause.getMessage())
								.withStyle(ChatFormatting.RED));
						return;
					}
					localMessage(Component.literal("[Magnolia OPT] API TEST PASSED: " + String.join(" | ", answers))
							.withStyle(ChatFormatting.GREEN));
				}));
	}

	private void sendNextGuess(Minecraft client) {
		if (!guessing || System.currentTimeMillis() < nextGuessAt || client.getConnection() == null) {
			return;
		}
		String guess = guessQueue.pollFirst();
		if (guess == null) {
			stopGuessing();
			return;
		}
		client.getConnection().sendChat(guess);
		captureLocalPlayerAnswer(guess);
		localMessage(Component.literal("[Magnolia OPT] Submitted guess: ")
				.withStyle(ChatFormatting.DARK_AQUA)
				.append(Component.literal(guess).withStyle(ChatFormatting.GREEN)));
		if (guessQueue.isEmpty()) {
			guessing = false;
		} else {
			nextGuessAt = System.currentTimeMillis() + humanSubmitDelay(guessQueue.getFirst());
		}
	}

	private boolean isDuplicate(String message) {
		Instant now = Instant.now();
		boolean duplicate = message.equals(lastProcessedMessage)
				&& Duration.between(lastProcessedAt, now).toMillis() < 250;
		lastProcessedMessage = message;
		lastProcessedAt = now;
		return duplicate;
	}

	private void learnConfirmedAnswer(String answer) {
		if (settings.learningEnabled() && currentPrompt != null && currentPrompt.accepts(answer)) {
			memory.remember(currentPrompt, answer);
			currentAnswers = List.of(answer);
			pendingWinner = null;
			LOGGER.info("Learned answer '{}' for '{}' from a server reveal", answer, currentPrompt.clue());
		}
	}

	private void showAnswers(String source, List<String> answers) {
		MutableComponent line = Component.literal("[Magnolia OPT] ").withStyle(ChatFormatting.AQUA)
				.append(Component.literal(source + ": ").withStyle(ChatFormatting.GRAY));
		for (int index = 0; index < answers.size(); index++) {
			if (index > 0) {
				line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(answerComponent(answers.get(index)));
		}
		localMessage(line);
		localMessage(Component.literal("  Click an answer to copy it. ").withStyle(ChatFormatting.DARK_GRAY)
				.append(fillComponent(answers.getFirst())));
	}

	private static Component answerComponent(String answer) {
		return Component.literal(answer).withStyle(style -> style
				.withColor(ChatFormatting.GREEN)
				.withBold(true)
				.withClickEvent(new ClickEvent.CopyToClipboard(answer))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Copy '" + answer + "'"))));
	}

	private static Component fillComponent(String answer) {
		return Component.literal("[FILL CHAT]").withStyle(style -> style
				.withColor(ChatFormatting.YELLOW)
				.withBold(true)
				.withClickEvent(new ClickEvent.SuggestCommand(answer))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Put '" + answer + "' in the chat box"))));
	}

	private void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("mag")
						.executes(context -> {
							showHelp();
							return 1;
						})
						.then(ClientCommands.literal("answer").executes(context -> {
							if (currentAnswers.isEmpty()) {
								context.getSource().sendError(Component.literal("No chat-game answer has been detected yet."));
								return 0;
							}
							showAnswers("current", currentAnswers);
							return 1;
						}))
						.then(ClientCommands.literal("remember")
								.then(ClientCommands.argument("answer", StringArgumentType.greedyString()).executes(context -> {
									if (!settings.learningEnabled()) {
										context.getSource().sendError(Component.literal("Learning and saving are disabled in /maggui."));
										return 0;
									}
									if (currentPrompt == null) {
										context.getSource().sendError(Component.literal("No chat-game prompt has been detected yet."));
										return 0;
									}
									String answer = normalizeManualAnswer(StringArgumentType.getString(context, "answer"));
									if (!currentPrompt.accepts(answer)) {
										context.getSource().sendError(Component.literal("That answer does not match the current word-game clue."));
										return 0;
									}
									memory.remember(currentPrompt, answer);
									currentAnswers = List.of(answer);
									context.getSource().sendFeedback(Component.literal("Remembered '" + answer + "' for '" + currentPrompt.clue() + "'."));
									return 1;
								})))
						.then(ClientCommands.literal("forget").executes(context -> {
							if (currentPrompt == null) {
								context.getSource().sendError(Component.literal("No chat-game prompt has been detected yet."));
								return 0;
							}
							boolean removed = memory.forget(currentPrompt);
							context.getSource().sendFeedback(Component.literal(removed ? "Forgot the current prompt." : "The current prompt was not in memory."));
							return removed ? 1 : 0;
						}))
						.then(ClientCommands.literal("toggle").executes(context -> {
							setHelperEnabled(!memory.enabled());
							context.getSource().sendFeedback(Component.literal("Magnolia OPT is now " + (memory.enabled() ? "ON" : "OFF") + "."));
							return 1;
						}))
						.then(ClientCommands.literal("welcome").executes(context -> {
							setAutoWelcomeEnabled(!autoWelcomeEnabled());
							context.getSource().sendFeedback(Component.literal("Auto /welcome is now "
									+ (autoWelcomeEnabled() ? "ON" : "OFF") + "."));
							return 1;
						}))
						.then(ClientCommands.literal("gui").executes(context -> {
							openSettingsScreen();
							return 1;
						}))
						.then(ClientCommands.literal("stats").executes(context -> {
							context.getSource().sendFeedback(Component.literal("Remembered answers: " + memory.size() + " | File: " + memory.path()));
							return 1;
						}))
						.then(ClientCommands.literal("api")
								.executes(context -> {
									context.getSource().sendFeedback(Component.literal((openAi.ready()
											? "OpenAI configured | model: " + openAi.model()
											: "OpenAI API key missing | config: " + OpenAiConfig.path())
											+ " | status: " + openAi.status()));
									return openAi.ready() ? 1 : 0;
								})
								.then(ClientCommands.literal("reload").executes(context -> {
									openAi.reload(OpenAiConfig.load());
									context.getSource().sendFeedback(Component.literal(openAi.ready()
											? "Reloaded OpenAI configuration successfully."
											: "Reloaded configuration, but no API key was found."));
									return openAi.ready() ? 1 : 0;
								}))
								.then(ClientCommands.literal("test").executes(context -> {
									runApiTest();
									return openAi.ready() ? 1 : 0;
								})))
						.then(ClientCommands.literal("update")
								.executes(context -> {
									context.getSource().sendFeedback(Component.literal(updater.detail()));
									if (updater.updateAvailable()) {
										openUpdateScreen();
									}
									return 1;
								})
								.then(ClientCommands.literal("check").executes(context -> {
									checkForUpdates();
									context.getSource().sendFeedback(Component.literal("Checking for a verified Magnolia OPT release..."));
									return 1;
								})))
		));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("maggui").executes(context -> {
					openSettingsScreen();
					return 1;
				})
		));
	}

	private void showHelp() {
		localMessage(Component.literal("[Magnolia OPT] Open /maggui, or use /mag answer, /mag remember <answer>, /mag forget, /mag toggle, /mag welcome, /mag stats, /mag api [reload|test], /mag update [check]")
				.withStyle(ChatFormatting.AQUA));
	}

	private void openSettingsScreen() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreenAndShow(new MagnoliaSettingsScreen(this)));
	}

	private void openUpdateScreen() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> client.setScreenAndShow(new UpdateAvailableScreen(this, null)));
	}

	private void handleStartupNotices(Minecraft client) {
		if (client.player == null || client.level == null) {
			joinedTicks = 0;
			pendingWelcomes.clear();
			return;
		}
		joinedTicks++;
		if (!versionNoticeShown && joinedTicks >= 20) {
			versionNoticeShown = true;
			String version = net.fabricmc.loader.api.FabricLoader.getInstance()
					.getModContainer("magnolia_chat_helper")
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("unknown");
			localMessage(Component.literal("[Magnolia OPT] v" + version + " active.")
					.withStyle(ChatFormatting.DARK_AQUA));
		}
		if (!updatePromptShown && updater.updateAvailable() && joinedTicks >= 40 && client.canInterruptScreen()) {
			updatePromptShown = true;
			client.setScreenAndShow(new UpdateAvailableScreen(this, null));
			return;
		}
		if (!apiPromptShown && !openAi.ready() && joinedTicks >= 60
				&& updater.state() != MagnoliaUpdater.State.CHECKING
				&& !updater.updateAvailable() && client.canInterruptScreen()) {
			apiPromptShown = true;
			client.setScreenAndShow(new ApiKeySetupScreen(this, null));
		}
	}

	boolean helperEnabled() {
		return memory.enabled();
	}

	void setHelperEnabled(boolean enabled) {
		memory.setEnabled(enabled);
		if (!enabled) {
			finishQuestionRound();
		}
	}

	boolean autoSubmitEnabled() {
		return settings.autoSubmitEnabled();
	}

	void setAutoSubmitEnabled(boolean enabled) {
		settings.setAutoSubmitEnabled(enabled);
		if (!enabled) {
			stopGuessing();
		}
	}

	int submissionChancePercent() {
		return settings.submissionChancePercent();
	}

	void adjustSubmissionChance(int delta) {
		settings.setSubmissionChancePercent(settings.submissionChancePercent() + delta);
	}

	boolean autoAnswerQuestions() {
		return settings.autoAnswerQuestions();
	}

	void setAutoAnswerQuestions(boolean enabled) {
		settings.setAutoAnswerQuestions(enabled);
		if (!enabled && currentPrompt != null && currentPrompt.type() == GamePrompt.Type.QUESTION) {
			stopGuessing();
		}
	}

	boolean learningEnabled() {
		return settings.learningEnabled();
	}

	void setLearningEnabled(boolean enabled) {
		settings.setLearningEnabled(enabled);
	}

	boolean learnFromReveals() {
		return settings.learnFromReveals();
	}

	void setLearnFromReveals(boolean enabled) {
		settings.setLearnFromReveals(enabled);
	}

	boolean learnFromWinners() {
		return settings.learnFromWinners();
	}

	void setLearnFromWinners(boolean enabled) {
		settings.setLearnFromWinners(enabled);
	}

	boolean autoWelcomeEnabled() {
		return settings.autoWelcomeEnabled();
	}

	boolean updateChecksEnabled() {
		return settings.updateChecksEnabled();
	}

	void setUpdateChecksEnabled(boolean enabled) {
		settings.setUpdateChecksEnabled(enabled);
		if (enabled) {
			checkForUpdates();
		}
	}

	String uiStyle() {
		return settings.uiStyle();
	}

	void setUiStyle(String style) {
		settings.setUiStyle(style);
	}

	String uiColor() {
		return settings.uiColor();
	}

	void setUiColor(String color) {
		settings.setUiColor(color);
	}

	String uiLightSpeed() {
		return settings.uiLightSpeed();
	}

	void setUiLightSpeed(String speed) {
		settings.setUiLightSpeed(speed);
	}

	void checkForUpdates() {
		updatePromptShown = false;
		updater.check();
	}

	void installAvailableUpdate() {
		updater.installAvailable();
	}

	MagnoliaUpdater.State updateState() {
		return updater.state();
	}

	String updateDetail() {
		return updater.detail();
	}

	String currentVersion() {
		return updater.currentVersion();
	}

	String availableVersion() {
		return updater.availableVersion();
	}

	void setAutoWelcomeEnabled(boolean enabled) {
		settings.setAutoWelcomeEnabled(enabled);
		if (!enabled) {
			waitingForWelcomeDetails = false;
			pendingWelcomes.clear();
		}
	}

	int memorySize() {
		return memory.size();
	}

	String currentPromptText() {
		return currentPrompt == null ? "Waiting for a game..." : currentPrompt.clue();
	}

	String bestAnswerText() {
		return currentAnswers.isEmpty() ? "None yet" : currentAnswers.getFirst();
	}

	String apiSummary() {
		return openAi.ready() ? openAi.model() + " • " + openAi.status() : "Not configured";
	}

	String rememberFromGui(String rawAnswer) {
		if (!settings.learningEnabled()) {
			return "Learning is OFF; enable it before saving";
		}
		if (currentPrompt == null) {
			return "No chat-game prompt has been detected yet";
		}
		String answer = normalizeManualAnswer(rawAnswer);
		if (answer.isEmpty()) {
			return "Type an answer first";
		}
		if (!currentPrompt.accepts(answer)) {
			return "That answer does not match the current clue";
		}
		memory.remember(currentPrompt, answer);
		currentAnswers = List.of(answer);
		return "Saved '" + answer + "' for this game";
	}

	private String normalizeManualAnswer(String answer) {
		return answer.trim();
	}

	String forgetFromGui() {
		if (currentPrompt == null) {
			return "No current prompt to forget";
		}
		return memory.forget(currentPrompt) ? "Forgot the saved answer for this game" : "This game was not in memory";
	}

	String showCurrentAnswersFromGui() {
		if (currentAnswers.isEmpty()) {
			return "No answer has been detected yet";
		}
		showAnswers("current", currentAnswers);
		return "Clickable answers shown in chat";
	}

	String copyBestAnswer() {
		if (currentAnswers.isEmpty()) {
			return "No answer has been detected yet";
		}
		Minecraft.getInstance().keyboardHandler.setClipboard(currentAnswers.getFirst());
		return "Copied '" + currentAnswers.getFirst() + "'";
	}

	String reloadApiFromGui() {
		openAi.reload(OpenAiConfig.load());
		return openAi.ready() ? "OpenAI configuration reloaded" : "No OpenAI API key found";
	}

	String testApiFromGui() {
		runApiTest();
		return openAi.ready() ? "API test sent; watch chat/status" : "Cannot test: API key is missing";
	}

	void openApiSetupScreen(net.minecraft.client.gui.screens.Screen parent) {
		Minecraft.getInstance().setScreenAndShow(new ApiKeySetupScreen(this, parent));
	}

	String saveApiKeyFromGui(String key) {
		if (!OpenAiConfig.saveApiKey(key)) {
			return "That key does not look valid. Paste the complete project key.";
		}
		openAi.reload(OpenAiConfig.load());
		return openAi.ready() ? "API key saved. OpenAI is online." : "The key could not be loaded.";
	}

	boolean apiReady() {
		return openAi.ready();
	}

	private static void localMessage(Component message) {
		Minecraft.getInstance().gui.chatListener().handleSystemMessage(message, false);
	}

	private record PlayerAnswer(String promptKey, String answer, Instant receivedAt) {
	}

	private record PendingWinner(String promptKey, String playerName, Instant announcedAt) {
	}

	private record PendingWelcome(long sendAt, long delayMillis) {
	}
}
