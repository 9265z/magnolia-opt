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
import java.util.Set;

final class CraftResolver {
	private static final Set<String> LOWERCASE_WORDS = Set.of("a", "an", "and", "of", "on", "the", "to", "with");
	private final Map<String, List<String>> answers = new LinkedHashMap<>();

	static CraftResolver load() {
		CraftResolver resolver = new CraftResolver();
		try (InputStream stream = CraftResolver.class.getResourceAsStream("/assets/magnolia_chat_helper/recipes.tsv")) {
			if (stream == null) {
				MagnoliaChatHelperClient.LOGGER.warn("Bundled recipe index was not found");
				return resolver;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				reader.lines().forEach(resolver::addLine);
			}
		} catch (IOException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not load bundled recipe index", exception);
		}
		return resolver;
	}

	List<String> solve(GamePrompt prompt, int limit) {
		List<String> matches = answers.getOrDefault(prompt.key().substring("craft:".length()), List.of());
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
		answers.computeIfAbsent(columns[0], ignored -> new ArrayList<>()).add(displayName(columns[1]));
	}

	static String displayName(String value) {
		String[] words = value.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+");
		for (int index = 0; index < words.length; index++) {
			String word = words[index];
			if ("tnt".equals(word)) {
				words[index] = "TNT";
			} else if (index > 0 && LOWERCASE_WORDS.contains(word)) {
				words[index] = word;
			} else if (!word.isEmpty()) {
				words[index] = Character.toUpperCase(word.charAt(0)) + word.substring(1);
			}
		}
		return String.join(" ", words);
	}
}
