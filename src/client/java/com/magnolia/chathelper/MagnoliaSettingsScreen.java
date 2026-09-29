package com.magnolia.chathelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

final class MagnoliaSettingsScreen extends Screen {
	private static final FontDescription GLASS_FONT = new FontDescription.Resource(
			Identifier.fromNamespaceAndPath("magnolia_chat_helper", "glass"));
	private static final long ANIMATION_MS = 230L;
	private static final int GREEN = 0xFF72DD8C;
	private static final int RED_STATUS = 0xFFFF6B72;
	private static final int WHITE = 0xFFF4F5FA;
	private static final int MUTED = 0xFFAEB2C0;

	private final MagnoliaChatHelperClient helper;
	private final EnumMap<Page, List<AbstractWidget>> pageWidgets = new EnumMap<>(Page.class);
	private Page page = Page.DASHBOARD;
	private Page previousPage = Page.DASHBOARD;
	private long transitionStarted;
	private long openedAt;
	private String status = "Everything is ready";
	private ThemeButton masterButton;
	private ThemeButton autoSubmitButton;
	private ThemeButton questionButton;
	private ThemeButton welcomeButton;
	private ThemeButton learningButton;
	private ThemeButton revealLearningButton;
	private ThemeButton winnerLearningButton;
	private ThemeButton updateChecksButton;
	private EditBox answerBox;

	MagnoliaSettingsScreen(MagnoliaChatHelperClient helper) {
		super(Component.literal("Magnolia OPT"));
		this.helper = helper;
		for (Page value : Page.values()) pageWidgets.put(value, new ArrayList<>());
	}

	@Override
	protected void init() {
		if (openedAt == 0L) openedAt = System.currentTimeMillis();
		pageWidgets.values().forEach(List::clear);
		Layout layout = layout();
		addNavigation(layout);
		addAutomationWidgets(layout);
		addLearningWidgets(layout);
		addOpenAiWidgets(layout);
		addUpdateWidgets(layout);
		addAppearanceWidgets(layout);
		addRenderableWidget(new ThemeButton(layout.left + layout.panelWidth - 31, layout.top + 10,
				20, 20, ui("×"), this::onClose));
		updateWidgetVisibility();
	}

	private void addNavigation(Layout layout) {
		for (Page value : Page.values()) {
			int x;
			int y;
			int buttonWidth;
			if (layout.compact) {
				int columns = 4;
				int gap = 3;
				buttonWidth = (layout.panelWidth - 24 - gap * (columns - 1)) / columns;
				x = layout.left + 12 + (value.ordinal() % columns) * (buttonWidth + gap);
				y = layout.top + 39 + (value.ordinal() / columns) * 22;
			} else {
				x = layout.left + 10;
				y = layout.top + 53 + value.ordinal() * 27;
				buttonWidth = layout.sidebarWidth - 20;
			}
			addRenderableWidget(new ThemeButton(x, y, buttonWidth, 19, ui(value.label),
					() -> switchPage(value), () -> page == value));
		}
	}

	private void addAutomationWidgets(Layout layout) {
		int x = layout.contentX;
		int y = layout.contentY + (layout.compact ? 23 : 31);
		int gap = 6;
		int half = (layout.contentWidth - gap) / 2;
		masterButton = addToggle(Page.AUTOMATION, x, y, half, "Chat helper", helper.helperEnabled(), () -> {
			helper.setHelperEnabled(!helper.helperEnabled());
			status = helper.helperEnabled() ? "Chat helper is online" : "Chat helper is offline";
		});
		questionButton = addToggle(Page.AUTOMATION, x + half + gap, y, half, "Question games",
				helper.autoAnswerQuestions(), () -> {
					helper.setAutoAnswerQuestions(!helper.autoAnswerQuestions());
				status = helper.autoAnswerQuestions() ? "Question automation enabled" : "Questions are display-only";
			});
		autoSubmitButton = addToggle(Page.AUTOMATION, x, y + 24, half, "Auto submit",
				helper.autoSubmitEnabled(), () -> {
					helper.setAutoSubmitEnabled(!helper.autoSubmitEnabled());
				status = helper.autoSubmitEnabled() ? "Automatic submissions enabled" : "Answers are display-only";
			});
		welcomeButton = addToggle(Page.AUTOMATION, x + half + gap, y + 24, half, "Auto /welcome",
				helper.autoWelcomeEnabled(), () -> {
					helper.setAutoWelcomeEnabled(!helper.autoWelcomeEnabled());
				status = helper.autoWelcomeEnabled() ? "Auto /welcome enabled" : "Auto /welcome disabled";
			});
		int saveWidth = layout.compact ? half : layout.contentWidth;
		learningButton = addToggle(Page.AUTOMATION, x, y + 48, saveWidth, "Auto save answers",
				helper.learningEnabled(), () -> {
					helper.setLearningEnabled(!helper.learningEnabled());
				status = helper.learningEnabled() ? "Automatic answer saving enabled" : "Automatic answer saving disabled";
			});
		int chanceY = layout.compact ? y + 48 : layout.contentY + 145;
		addToPage(Page.AUTOMATION, new ThemeButton(x + layout.contentWidth - 90, chanceY, 42, 20,
				ui("−5%"), () -> adjustChance(-5)));
		addToPage(Page.AUTOMATION, new ThemeButton(x + layout.contentWidth - 44, chanceY, 44, 20,
				ui("+5%"), () -> adjustChance(5)));
	}

	private void addLearningWidgets(Layout layout) {
		int x = layout.contentX;
		int y = layout.contentY + (layout.compact ? 23 : 31);
		int gap = 6;
		int half = (layout.contentWidth - gap) / 2;
		revealLearningButton = addToggle(Page.LEARNING, x, y, half, "Server reveals",
				helper.learnFromReveals(), () -> {
					helper.setLearnFromReveals(!helper.learnFromReveals());
				status = helper.learnFromReveals() ? "Learning from server reveals" : "Server-reveal learning disabled";
			});
		winnerLearningButton = addToggle(Page.LEARNING, x + half + gap, y, half, "Round winners",
				helper.learnFromWinners(), () -> {
					helper.setLearnFromWinners(!helper.learnFromWinners());
				status = helper.learnFromWinners() ? "Learning from winning messages" : "Winner-message learning disabled";
			});
		int inputOffset = layout.compact ? 29 : 70;
		answerBox = addToPage(Page.LEARNING,
				new EditBox(font, x, y + inputOffset, layout.contentWidth, 20, ui("Manual answer")));
		answerBox.setMaxLength(128);
		answerBox.setHint(ui("Teach the current game an exact answer...").copy().withStyle(ChatFormatting.DARK_GRAY));
		int small = Math.max(54, layout.contentWidth / 5);
		int actionOffset = layout.compact ? 53 : 95;
		addToPage(Page.LEARNING, new ThemeButton(x, y + actionOffset, layout.contentWidth - small * 2 - 8, 20,
				ui("SAVE CURRENT"), () -> {
					status = helper.rememberFromGui(answerBox.getValue());
					if (status.startsWith("Saved")) answerBox.setValue("");
				}));
		addToPage(Page.LEARNING, new ThemeButton(x + layout.contentWidth - small * 2 - 4, y + actionOffset,
				small, 20, ui("FORGET"), () -> status = helper.forgetFromGui()));
		addToPage(Page.LEARNING, new ThemeButton(x + layout.contentWidth - small, y + actionOffset,
				small, 20, ui("COPY"), () -> status = helper.copyBestAnswer()));
	}

	private void addOpenAiWidgets(Layout layout) {
		int x = layout.contentX;
		int y = layout.contentY + (layout.compact ? 57 : 119);
		int gap = 6;
		int half = (layout.contentWidth - gap) / 2;
		addToPage(Page.OPENAI, new ThemeButton(x, y, half, 20, ui("SETUP / CHANGE KEY"),
				() -> helper.openApiSetupScreen(this)));
		addToPage(Page.OPENAI, new ThemeButton(x + half + gap, y, half, 20, ui("RELOAD CONFIG"),
				() -> status = helper.reloadApiFromGui()));
		addToPage(Page.OPENAI, new ThemeButton(x, y + 25, layout.contentWidth, 20, ui("TEST CONNECTION"),
				() -> status = helper.testApiFromGui()));
	}

	private void addUpdateWidgets(Layout layout) {
		int x = layout.contentX;
		int y = layout.contentY + 31;
		updateChecksButton = addToggle(Page.UPDATES, x, y, layout.contentWidth, "Launch update checks",
				helper.updateChecksEnabled(), () -> {
					helper.setUpdateChecksEnabled(!helper.updateChecksEnabled());
				status = helper.updateChecksEnabled() ? "Launch update checks enabled" : "Launch update checks disabled";
			});
		int gap = 6;
		int half = (layout.contentWidth - gap) / 2;
		int actionY = layout.compact ? y + 59 : layout.contentY + 153;
		addToPage(Page.UPDATES, new ThemeButton(x, actionY, half, 20, ui("CHECK NOW"), () -> {
			helper.checkForUpdates();
			status = "Checking the public GitHub release...";
		}));
		addToPage(Page.UPDATES, new ThemeButton(x + half + gap, actionY, half, 20, ui("VIEW UPDATE"), () -> {
			if (helper.updateState() == MagnoliaUpdater.State.AVAILABLE) {
				minecraft.setScreenAndShow(new UpdateAvailableScreen(helper, this));
			} else status = helper.updateDetail();
		}));
	}

	private void addAppearanceWidgets(Layout layout) {
		int x = layout.contentX;
		int y = layout.contentY + (layout.compact ? 22 : 31);
		int gap = 6;
		int half = (layout.contentWidth - gap) / 2;
		addToPage(Page.APPEARANCE, new ThemeButton(x, y, half, 22, ui("GLASS"),
				() -> setUiStyle("GLASS"), this::glass));
		addToPage(Page.APPEARANCE, new ThemeButton(x + half + gap, y, half, 22, ui("MINECRAFT"),
				() -> setUiStyle("MINECRAFT"), () -> !glass()));
		String[] colors = {"RED", "BLUE", "PURPLE", "PINK", "BLACK", "WHITE"};
		int columns = layout.contentWidth >= 390 ? 6 : 3;
		int buttonGap = 4;
		int buttonWidth = (layout.contentWidth - buttonGap * (columns - 1)) / columns;
		for (int index = 0; index < colors.length; index++) {
			String color = colors[index];
			int bx = x + (index % columns) * (buttonWidth + buttonGap);
			int by = y + (layout.compact ? 32 : 82) + (index / columns) * (layout.compact ? 20 : 24);
			addToPage(Page.APPEARANCE, new ThemeButton(bx, by, buttonWidth, layout.compact ? 18 : 20,
					ui(color), () -> setUiColor(color), () -> color.equals(helper.uiColor())));
		}
	}

	private ThemeButton addToggle(Page targetPage, int x, int y, int width, String label,
			boolean enabled, Runnable action) {
		ThemeButton button = new ThemeButton(x, y, width, 20, toggleLabel(label, enabled), () -> {
			action.run();
			refreshLabels();
		});
		button.setTooltip(Tooltip.create(ui(label)));
		return addToPage(targetPage, button);
	}

	private <T extends AbstractWidget> T addToPage(Page targetPage, T widget) {
		pageWidgets.get(targetPage).add(widget);
		return addRenderableWidget(widget);
	}

	private void switchPage(Page destination) {
		if (destination == page) return;
		previousPage = page;
		page = destination;
		transitionStarted = System.currentTimeMillis();
		status = destination.subtitle;
		updateWidgetVisibility();
	}

	private void adjustChance(int delta) {
		helper.adjustSubmissionChance(delta);
		status = "Submission chance set to " + helper.submissionChancePercent() + "%";
	}

	private void setUiStyle(String style) {
		helper.setUiStyle(style);
		status = style.equals("GLASS") ? "Glass mode: rounded, translucent, Lato SemiBold"
				: "Minecraft mode: square, opaque, native font";
		transitionStarted = System.currentTimeMillis();
		rebuildWidgets();
	}

	private void setUiColor(String color) {
		helper.setUiColor(color);
		status = color.substring(0, 1) + color.substring(1).toLowerCase(Locale.ROOT) + " accent selected";
		transitionStarted = System.currentTimeMillis();
		rebuildWidgets();
	}

	private void updateWidgetVisibility() {
		pageWidgets.forEach((targetPage, widgets) -> widgets.forEach(widget -> widget.visible = targetPage == page));
	}

	private void refreshLabels() {
		masterButton.setMessage(toggleLabel("Chat helper", helper.helperEnabled()));
		autoSubmitButton.setMessage(toggleLabel("Auto submit", helper.autoSubmitEnabled()));
		questionButton.setMessage(toggleLabel("Question games", helper.autoAnswerQuestions()));
		welcomeButton.setMessage(toggleLabel("Auto /welcome", helper.autoWelcomeEnabled()));
		learningButton.setMessage(toggleLabel("Auto save answers", helper.learningEnabled()));
		revealLearningButton.setMessage(toggleLabel("Server reveals", helper.learnFromReveals()));
		winnerLearningButton.setMessage(toggleLabel("Round winners", helper.learnFromWinners()));
		updateChecksButton.setMessage(toggleLabel("Launch update checks", helper.updateChecksEnabled()));
	}

	private Component toggleLabel(String label, boolean enabled) {
		return ui(label + "  ").copy().append(ui(enabled ? "ON" : "OFF").copy()
				.withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	private Component ui(String text) {
		Component component = Component.literal(text);
		return glass() ? component.copy().withStyle(style -> style.withFont(GLASS_FONT)) : component;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		Layout layout = layout();
		Palette palette = palette();
		graphics.fill(0, 0, width, height, glass() ? 0x32040810 : 0xD0000000);
		panel(graphics, layout.left, layout.top, layout.panelWidth, layout.panelHeight, palette.shell, palette.border, 14);
		drawAnimatedEdge(graphics, layout, palette);
		if (!layout.compact) {
			panel(graphics, layout.left + 6, layout.top + 42, layout.sidebarWidth - 12,
					layout.panelHeight - 68, palette.sidebar, palette.border, 12);
		}
		graphics.text(font, ui("MAGNOLIA OPT"), layout.left + 15, layout.top + 14, palette.accent, true);
		graphics.text(font, ui(glass() ? "GLASS CONTROL CENTER" : "MINECRAFT CONTROL CENTER"),
				layout.left + 15, layout.top + 28, MUTED, false);
		drawNavigationMarker(graphics, layout, palette);
		drawHeader(graphics, layout, palette);
		switch (page) {
			case DASHBOARD -> drawDashboard(graphics, layout, palette);
			case AUTOMATION -> drawAutomation(graphics, layout, palette);
			case LEARNING -> drawLearning(graphics, layout, palette);
			case GAMES -> drawGames(graphics, layout, palette);
			case OPENAI -> drawOpenAi(graphics, layout, palette);
			case UPDATES -> drawUpdates(graphics, layout, palette);
			case APPEARANCE -> drawAppearance(graphics, layout, palette);
		}
		drawTransition(graphics, layout, palette);
		graphics.text(font, ui("deved by 9265z"), layout.left + 15,
				layout.top + layout.panelHeight - 17, 0xFF858997, false);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawHeader(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		if (layout.compact) return;
		graphics.text(font, ui(page.title), layout.contentX, layout.contentY - 24, WHITE, true);
		graphics.text(font, trim(page.subtitle, layout.contentWidth), layout.contentX, layout.contentY - 11, MUTED, false);
	}

	private void drawDashboard(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		int x = layout.contentX;
		int y = layout.contentY;
		int gap = 8;
		int half = (layout.contentWidth - gap) / 2;
		int metricHeight = layout.compact ? 43 : 54;
		metric(graphics, x, y, half, metricHeight, "MEMORY", Integer.toString(helper.memorySize()), "saved answers", palette);
		metric(graphics, x + half + gap, y, half, metricHeight, "AUTOMATION",
				helper.autoSubmitEnabled() ? helper.submissionChancePercent() + "%" : "OFF", "submission chance", palette);
		int gameY = y + metricHeight + 6;
		int gameHeight = Math.max(48, layout.contentHeight - metricHeight - 6);
		card(graphics, x, gameY, layout.contentWidth, gameHeight, "CURRENT CHAT GAME", palette);
		graphics.text(font, trim("Prompt  " + helper.currentPromptText(), layout.contentWidth - 18), x + 10, gameY + 25, MUTED, false);
		graphics.text(font, trim("Answer  " + helper.bestAnswerText(), layout.contentWidth - 18), x + 10, gameY + 42,
				helper.bestAnswerText().equals("None yet") ? MUTED : GREEN, false);
	}

	private void drawAutomation(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		int x = layout.contentX;
		int y = layout.contentY;
		if (layout.compact) {
			card(graphics, x - 6, y, layout.contentWidth + 12, layout.contentHeight, "AUTOMATION", palette);
			graphics.text(font, trim("70–80% recommended; no setting guarantees avoiding detection.", layout.contentWidth - 8),
					x + 2, y + layout.contentHeight - 10, palette.accent, false);
			return;
		}
		card(graphics, x - 6, y, layout.contentWidth + 12, 106, "AUTOMATION", palette);
		card(graphics, x - 6, y + 112, layout.contentWidth + 12, Math.max(66, layout.contentHeight - 112), "TIMING", palette);
		graphics.text(font, ui("Submission chance"), x + 2, y + 151, MUTED, false);
		graphics.text(font, ui(helper.submissionChancePercent() + "%"), x + layout.contentWidth - 137, y + 151, palette.accent, true);
		graphics.text(font, trim("70–80% recommended; no setting guarantees avoiding detection.", layout.contentWidth - 8),
				x + 2, y + 178, palette.accent, false);
		if (layout.contentHeight >= 205) {
			graphics.text(font, ui("Length-based delay: 1–4 seconds"), x + 2, y + 198, MUTED, false);
		}
	}

	private void drawLearning(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		int x = layout.contentX;
		int y = layout.contentY;
		if (layout.compact) {
			card(graphics, x - 6, y, layout.contentWidth + 12, layout.contentHeight, "MEMORY", palette);
		} else {
			card(graphics, x - 6, y, layout.contentWidth + 12, 62, "ANSWER SOURCES", palette);
			card(graphics, x - 6, y + 70, layout.contentWidth + 12, 84, "MANUAL MEMORY", palette);
			graphics.text(font, ui(helper.memorySize() + " answers saved • exact capitalization is preserved"),
					x + 2, y + 164, MUTED, false);
		}
	}

	private void drawGames(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		int x = layout.contentX;
		int y = layout.contentY;
		int gap = 7;
		int half = (layout.contentWidth - gap) / 2;
		int row = layout.compact ? 36 : 50;
		int cardHeight = layout.compact ? 34 : 44;
		example(graphics, x, y, half, cardHeight, "UNSCRAMBLE", "seiRn pmClu", "Resin Clump", palette);
		example(graphics, x + half + gap, y, half, cardHeight, "UNREVERSE", "deiruB erusaerT paM", "Buried Treasure Map", palette);
		example(graphics, x, y + row, half, cardHeight, "RANDOM TEXT", "AbC912xY", "AbC912xY", palette);
		example(graphics, x + half + gap, y + row, half, cardHeight, "CRAFTED ITEM", "9 Resin Clump", "Resin Block", palette);
		example(graphics, x, y + row * 2, layout.contentWidth, cardHeight, "QUESTION", "How many frog variants are there?", "3", palette);
	}

	private void drawOpenAi(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		int x = layout.contentX;
		int y = layout.contentY;
		int connectionHeight = layout.compact ? 51 : 82;
		card(graphics, x, y, layout.contentWidth, connectionHeight, "OPENAI CONNECTION", palette);
		graphics.text(font, ui("Connection"), x + 10, y + 25, MUTED, false);
		graphics.text(font, ui(helper.apiReady() ? "online" : "API key required"), x + 91, y + 25,
				helper.apiReady() ? GREEN : RED_STATUS, true);
		if (!layout.compact) graphics.text(font, trim("Model  " + helper.apiSummary(), layout.contentWidth - 20), x + 10, y + 44, WHITE, false);
		card(graphics, x, y + connectionHeight + 9, layout.contentWidth,
				Math.max(76, layout.contentHeight - connectionHeight - 9), "CONNECTION TOOLS", palette);
	}

	private void drawUpdates(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		int x = layout.contentX;
		int y = layout.contentY;
		int releaseHeight = layout.compact ? 49 : 78;
		card(graphics, x, y, layout.contentWidth, releaseHeight, "VERIFIED RELEASES", palette);
		graphics.text(font, ui("Installed  " + helper.currentVersion() + "    Available  " + helper.availableVersion()),
				x + 10, y + (layout.compact ? 35 : 58), WHITE, false);
		int safetyY = y + releaseHeight + 8;
		card(graphics, x, safetyY, layout.contentWidth, Math.max(48, layout.contentHeight - releaseHeight - 8), "UPDATE SAFETY", palette);
		graphics.text(font, trim(helper.updateDetail(), layout.contentWidth - 20), x + 10, safetyY + 25,
				helper.updateState() == MagnoliaUpdater.State.ERROR ? RED_STATUS : MUTED, false);
		if (!layout.compact) graphics.text(font, trim("SHA-256 + Fabric metadata verification • Update Now or Later", layout.contentWidth - 20),
				x + 10, safetyY + 42, GREEN, false);
	}

	private void drawAppearance(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		int x = layout.contentX;
		int y = layout.contentY;
		int styleHeight = layout.compact ? 48 : 74;
		card(graphics, x, y, layout.contentWidth, styleHeight, "UI STYLE", palette);
		graphics.text(font, ui(glass() ? "Selected: Glass • rounded • translucent • Lato SemiBold"
				: "Selected: Minecraft • square • opaque • native font"), x + 10, y + (layout.compact ? 36 : 59), palette.accent, false);
		int colorY = y + styleHeight + 8;
		card(graphics, x, colorY, layout.contentWidth, Math.max(50, layout.contentHeight - styleHeight - 8), "ACCENT COLOR", palette);
		graphics.text(font, ui("Selected: " + helper.uiColor().toLowerCase(Locale.ROOT)),
				x + 10, colorY + (layout.compact ? 44 : 66), palette.accent, true);
	}

	private void metric(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			String heading, String value, String detail, Palette palette) {
		card(graphics, x, y, w, h, heading, palette);
		graphics.text(font, ui(value), x + 10, y + 23, palette.accent, true);
		if (h >= 50) graphics.text(font, trim(detail, w - 20), x + 10, y + 40, MUTED, false);
	}

	private void example(GuiGraphicsExtractor graphics, int x, int y, int w, int height, String heading,
			String prompt, String answer, Palette palette) {
		card(graphics, x, y, w, height, heading, palette);
		graphics.text(font, trim(prompt, w - 16), x + 8, y + 18, MUTED, false);
		graphics.text(font, trim("→ " + answer, w - 16), x + 8, y + height - 11, GREEN, false);
	}

	private void card(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String heading, Palette palette) {
		panel(graphics, x, y, w, h, palette.card, palette.border, glass() ? 11 : 0);
		graphics.text(font, ui(heading), x + 10, y + 7, palette.accent, true);
	}

	private void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
			int fill, int border, int radius) {
		if (glass()) {
			roundFill(graphics, x, y, w, h, radius, border);
			roundFill(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(2, radius - 1), fill);
			if (w > radius * 2 + 4) {
				graphics.fill(x + radius + 2, y + 1, x + w - radius - 2, y + 2,
						withAlpha(WHITE, 22));
			}
		} else {
			graphics.fill(x, y, x + w, y + h, fill);
			graphics.outline(x, y, w, h, border);
			graphics.fill(x, y, x + 3, y + h, accent());
		}
	}

	private static void roundFill(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int radius, int color) {
		if (w <= 0 || h <= 0) return;
		int r = Math.min(radius, Math.min(w / 2, h / 2));
		graphics.fill(x + r, y, x + w - r, y + h, color);
		graphics.fill(x, y + r, x + w, y + h - r, color);
		for (int offset = 1; offset <= r; offset++) {
			int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - (r - offset) * (r - offset))));
			graphics.fill(x + inset, y + offset - 1, x + w - inset, y + offset, color);
			graphics.fill(x + inset, y + h - offset, x + w - inset, y + h - offset + 1, color);
		}
	}

	private void drawNavigationMarker(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		if (layout.compact) return;
		double eased = ease(animationProgress());
		int from = layout.top + 53 + previousPage.ordinal() * 27;
		int to = layout.top + 53 + page.ordinal() * 27;
		int y = (int) Math.round(from + (to - from) * eased);
		if (glass()) roundFill(graphics, layout.left + 6, y, 4, 19, 2, palette.accent);
		else graphics.fill(layout.left + 6, y, layout.left + 10, y + 19, palette.accent);
	}

	private void drawAnimatedEdge(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		if (!glass()) return;
		double seconds = (System.currentTimeMillis() - openedAt) / 1000.0;
		int travel = Math.max(1, layout.panelWidth - 150);
		int x = layout.left + 25 + (int) ((Math.sin(seconds * 1.55) * 0.5 + 0.5) * travel);
		graphics.fill(x - 28, layout.top, x + 28, layout.top + 1, withAlpha(palette.accent, 48));
		graphics.fill(x - 14, layout.top, x + 14, layout.top + 2, withAlpha(palette.accent, 112));
		graphics.fill(x - 4, layout.top, x + 4, layout.top + 2, withAlpha(WHITE, 125));
	}

	private void drawTransition(GuiGraphicsExtractor graphics, Layout layout, Palette palette) {
		double progress = animationProgress();
		if (progress >= 1.0) return;
		int alpha = (int) ((1.0 - progress) * 68.0);
		graphics.fill(layout.contentX, layout.contentY, layout.contentX + layout.contentWidth,
				layout.contentY + layout.contentHeight, alpha << 24);
		int sweep = layout.contentX + (int) (layout.contentWidth * ease(progress));
		graphics.fill(sweep - 8, layout.contentY, sweep + 8, layout.contentY + layout.contentHeight,
				withAlpha(palette.accent, 18));
		graphics.fill(sweep - 2, layout.contentY, sweep + 3, layout.contentY + layout.contentHeight,
				withAlpha(palette.accent, 105));
		graphics.fill(sweep, layout.contentY, sweep + 1, layout.contentY + layout.contentHeight,
				withAlpha(WHITE, 150));
	}

	private Component trim(String value, int pixelWidth) {
		Component component = ui(value);
		if (font.width(component) <= pixelWidth) return component;
		String shortened = value;
		while (shortened.length() > 4 && font.width(ui(shortened + "…")) > pixelWidth) {
			shortened = shortened.substring(0, shortened.length() - 1);
		}
		return ui(shortened + "…");
	}

	private double animationProgress() {
		if (transitionStarted == 0L) return 1.0;
		return Math.min(1.0, (System.currentTimeMillis() - transitionStarted) / (double) ANIMATION_MS);
	}

	private static double ease(double value) {
		return 1.0 - Math.pow(1.0 - value, 3.0);
	}

	private boolean glass() {
		return !"MINECRAFT".equals(helper.uiStyle());
	}

	private int accent() {
		return switch (helper.uiColor()) {
			case "RED" -> 0xFFFF6574;
			case "BLUE" -> 0xFF5CA9FF;
			case "PINK" -> 0xFFFF79C8;
			case "BLACK" -> 0xFF707785;
			case "WHITE" -> 0xFFF1F3FA;
			default -> 0xFFA985FF;
		};
	}

	private Palette palette() {
		int accent = accent();
		if (glass()) return new Palette(0x8A080C16, 0x52101825, 0x68182130, 0x6C72809B, accent);
		return new Palette(0xFF11110F, 0xFF181713, 0xFF211F1A, 0xFF4A463B, accent);
	}

	private Layout layout() {
		ResponsiveUiLayout value = ResponsiveUiLayout.calculate(width, height);
		return new Layout(value.left(), value.top(), value.panelWidth(), value.panelHeight(), value.sidebarWidth(),
				value.contentX(), value.contentY(), value.contentWidth(), value.contentHeight(), value.compact());
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private enum Page {
		DASHBOARD("HOME", "Dashboard", "Live status and current game"),
		AUTOMATION("AUTO", "Automation", "Control what Magnolia OPT sends and saves"),
		LEARNING("MEMORY", "Learning", "Choose the confirmed-answer sources"),
		GAMES("GAMES", "Game Library", "Every supported chat-game format"),
		OPENAI("OPENAI", "OpenAI", "Connection and API controls"),
		UPDATES("UPDATES", "Updates", "Verified releases with your approval"),
		APPEARANCE("LOOK", "Appearance", "Choose Glass or Minecraft and an accent color");

		private final String label;
		private final String title;
		private final String subtitle;

		Page(String label, String title, String subtitle) {
			this.label = label;
			this.title = title;
			this.subtitle = subtitle;
		}
	}

	private record Palette(int shell, int sidebar, int card, int border, int accent) {
	}

	private record Layout(int left, int top, int panelWidth, int panelHeight, int sidebarWidth,
			int contentX, int contentY, int contentWidth, int contentHeight, boolean compact) {
	}

	private final class ThemeButton extends AbstractWidget {
		private final Runnable action;
		private final BooleanSupplier selected;

		ThemeButton(int x, int y, int width, int height, Component message, Runnable action) {
			this(x, y, width, height, message, action, () -> false);
		}

		ThemeButton(int x, int y, int width, int height, Component message, Runnable action,
				BooleanSupplier selected) {
			super(x, y, width, height, message);
			this.action = action;
			this.selected = selected;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			if (active) action.run();
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			Palette palette = palette();
			boolean chosen = selected.getAsBoolean();
			int background = !active ? 0x44434855 : chosen
					? withAlpha(palette.accent, glass() ? 132 : 255)
					: isHoveredOrFocused() ? withAlpha(palette.accent, glass() ? 102 : 255)
					: glass() ? 0x52242A3B : 0xFF302D26;
			int border = chosen || isHoveredOrFocused() ? withAlpha(palette.accent, glass() ? 205 : 255) : palette.border;
			panel(graphics, getX(), getY(), width, height, background, border, glass() ? 10 : 0);
			if (glass() && (chosen || isHoveredOrFocused()) && width > 20) {
				int range = Math.max(1, width + 26);
				int shimmer = getX() - 13 + (int) (((System.currentTimeMillis() - openedAt) % 1700L) / 1700.0 * range);
				int shimmerLeft = Math.max(getX() + 2, shimmer - 5);
				int shimmerRight = Math.min(getX() + width - 2, shimmer + 5);
				if (shimmerRight > shimmerLeft) {
					graphics.fill(shimmerLeft, getY() + 2, shimmerRight, getY() + height - 2, withAlpha(WHITE, 22));
				}
			}
			graphics.centeredText(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2,
					active ? WHITE : MUTED);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
