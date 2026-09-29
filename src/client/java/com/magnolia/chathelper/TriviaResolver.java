package com.magnolia.chathelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TriviaResolver {
	private final Map<String, List<String>> answers = new LinkedHashMap<>();

	static TriviaResolver load() {
		TriviaResolver resolver = new TriviaResolver();
		try (InputStream stream = TriviaResolver.class.getResourceAsStream("/assets/magnolia_chat_helper/trivia.tsv")) {
			if (stream == null) {
				MagnoliaChatHelperClient.LOGGER.warn("Bundled trivia index was not found");
				return resolver;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				reader.lines().forEach(resolver::addLine);
			}
		} catch (IOException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not load bundled trivia index", exception);
		}
		return resolver;
	}

	List<String> solve(GamePrompt prompt, int limit) {
		List<String> matches = answers.getOrDefault(prompt.key().substring("question:".length()), List.of());
		return matches.subList(0, Math.min(limit, matches.size()));
	}

	private void addLine(String line) {
		if (line.isBlank() || line.startsWith("#")) {
			return;
		}
		String[] columns = line.split("\\t", 2);
		if (columns.length != 2) {
			return;
		}
		List<String> values = answers.computeIfAbsent(columns[0], ignored -> new ArrayList<>());
		for (String answer : columns[1].split("\\|")) {
			String normalized = answer.trim();
			if (!normalized.isEmpty() && !values.contains(normalized)) {
				values.add(normalized);
			}
		}
	}
}
