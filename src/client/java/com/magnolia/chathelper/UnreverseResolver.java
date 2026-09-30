package com.magnolia.chathelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Chooses the plausible interpretation when Magnolia reverses words or a whole phrase. */
final class UnreverseResolver {
	private static final int CONFIDENCE_MARGIN = 8;
	private final Set<String> phrases = new HashSet<>();
	private final Set<String> tokens = new HashSet<>();
	private final Map<String, Integer> firstTokens = new HashMap<>();
	private final Map<String, Integer> lastTokens = new HashMap<>();
	private final Map<String, Integer> bigrams = new HashMap<>();
	private final Map<String, Integer> trigrams = new HashMap<>();

	static UnreverseResolver load() {
		UnreverseResolver resolver = new UnreverseResolver();
		MinecraftPhraseLoader.addRegistryPhrases(resolver);
		resolver.addRecipePhrases();
		// Confirmed Magnolia phrases that are natural Minecraft terms but not registry IDs.
		resolver.addPhrase("Buried Treasure Map");
		return resolver;
	}

	static UnreverseResolver fromPhrases(List<String> knownPhrases) {
		UnreverseResolver resolver = new UnreverseResolver();
		knownPhrases.forEach(resolver::addPhrase);
		return resolver;
	}

	Decision solve(GamePrompt prompt) {
		List<String> candidates = prompt.unreverseAnswers();
		if (candidates.size() == 1) {
			return new Decision(candidates, candidates, true);
		}

		List<ScoredAnswer> ranked = candidates.stream()
				.map(answer -> new ScoredAnswer(answer, score(answer), phrases.contains(normalize(answer))))
				.sorted(Comparator.comparingInt(ScoredAnswer::score).reversed())
				.toList();
		ScoredAnswer best = ranked.getFirst();
		ScoredAnswer second = ranked.get(1);
		boolean confident = (best.exactMinecraftPhrase() && !second.exactMinecraftPhrase())
				|| (best.score() >= 8 && best.score() - second.score() >= CONFIDENCE_MARGIN);
		List<String> display = ranked.stream().map(ScoredAnswer::answer).toList();
		return new Decision(display, confident ? List.of(best.answer()) : List.of(), confident);
	}

	private int score(String answer) {
		String normalized = normalize(answer);
		if (phrases.contains(normalized)) {
			return 1_000;
		}
		String[] words = normalized.split(" ");
		int score = 0;
		for (String word : words) {
			score += tokens.contains(word) ? 1 : -3;
		}
		score += Math.min(6, firstTokens.getOrDefault(words[0], 0));
		score += Math.min(10, lastTokens.getOrDefault(words[words.length - 1], 0) * 2);
		for (int index = 0; index + 1 < words.length; index++) {
			score += Math.min(12, bigrams.getOrDefault(words[index] + " " + words[index + 1], 0) * 4);
		}
		for (int index = 0; index + 2 < words.length; index++) {
			score += Math.min(18, trigrams.getOrDefault(
					words[index] + " " + words[index + 1] + " " + words[index + 2], 0) * 6);
		}
		return score;
	}

	private void addRecipePhrases() {
		try (InputStream stream = UnreverseResolver.class.getResourceAsStream(
				"/assets/magnolia_chat_helper/recipes.tsv")) {
			if (stream == null) {
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				reader.lines().forEach(this::addRecipeLine);
			}
		} catch (IOException exception) {
			MagnoliaChatHelperClient.LOGGER.warn("Could not load Minecraft phrases for unreverse scoring", exception);
		}
	}

	private void addRecipeLine(String line) {
		if (line.isBlank() || line.startsWith("#")) {
			return;
		}
		String[] columns = line.split("\\t", 2);
		if (columns.length != 2) {
			return;
		}
		addPhrase(columns[1]);
		for (String ingredient : columns[0].split("\\+")) {
			addPhrase(ingredient.trim().replaceFirst("^\\d+\\s+", ""));
		}
	}

	void addPhrase(String rawPhrase) {
		String phrase = normalize(rawPhrase);
		if (phrase.isBlank() || !phrases.add(phrase)) {
			return;
		}
		String[] words = phrase.split(" ");
		for (String word : words) {
			tokens.add(word);
		}
		increment(firstTokens, words[0]);
		increment(lastTokens, words[words.length - 1]);
		for (int index = 0; index + 1 < words.length; index++) {
			increment(bigrams, words[index] + " " + words[index + 1]);
		}
		for (int index = 0; index + 2 < words.length; index++) {
			increment(trigrams, words[index] + " " + words[index + 1] + " " + words[index + 2]);
		}
	}

	private static void increment(Map<String, Integer> counts, String value) {
		counts.merge(value, 1, Integer::sum);
	}

	private static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "")
				.replaceAll("\\s+", " ");
	}

	record Decision(List<String> displayAnswers, List<String> automaticAnswers, boolean confident) {
		Decision {
			displayAnswers = List.copyOf(new ArrayList<>(displayAnswers));
			automaticAnswers = List.copyOf(new ArrayList<>(automaticAnswers));
		}
	}

	private record ScoredAnswer(String answer, int score, boolean exactMinecraftPhrase) {
	}
}
