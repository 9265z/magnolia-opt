package com.magnolia.chathelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Unscrambler {
	private static final List<String> MINECRAFT_WORDS = List.of(
			"minecraft", "magnolia", "creeper", "zombie", "skeleton", "spider", "enderman", "villager",
			"diamond", "emerald", "redstone", "obsidian", "netherite", "pickaxe", "crafting", "enchanting",
			"overworld", "nether", "end", "biome", "stronghold", "dungeon", "mineshaft", "elytra", "shulker",
			"warden", "allay", "axolotl", "blaze", "ghast", "piglin", "hoglin", "slime", "magma", "anvil");

	private final Map<String, List<String>> bySignature = new LinkedHashMap<>();

	static Unscrambler load() {
		Unscrambler unscrambler = new Unscrambler();
		MINECRAFT_WORDS.forEach(unscrambler::add);
		try (InputStream stream = Unscrambler.class.getResourceAsStream("/assets/magnolia_chat_helper/words.txt")) {
			if (stream == null) {
				MagnoliaChatHelperClient.LOGGER.warn("Bundled word list was not found");
				return unscrambler;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				reader.lines().forEach(unscrambler::add);
			}
		} catch (IOException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not load bundled word list", exception);
		}
		unscrambler.loadRecipeVocabulary();
		return unscrambler;
	}

	List<String> solve(String scrambled, int limit) {
		List<String> matches = bySignature.getOrDefault(signature(scrambled), List.of());
		return matches.stream()
				.limit(limit)
				.map(match -> applyCapitalization(match, scrambled))
				.toList();
	}

	private void loadRecipeVocabulary() {
		try (InputStream stream = Unscrambler.class.getResourceAsStream("/assets/magnolia_chat_helper/recipes.tsv")) {
			if (stream == null) {
				return;
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
				reader.lines().forEach(line -> {
					String[] columns = line.split("\\t", 2);
					if (columns.length != 2) {
						return;
					}
					add(columns[1]);
					for (String ingredient : columns[0].split("\\+")) {
						add(ingredient.trim().replaceFirst("^\\d+\\s+", ""));
					}
				});
			}
		} catch (IOException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not load recipe vocabulary for unscrambling", exception);
		}
	}

	private void add(String value) {
		String word = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
		if (!word.matches("[a-z][a-z ']{1,63}")) {
			return;
		}
		List<String> matches = bySignature.computeIfAbsent(signature(word), ignored -> new ArrayList<>());
		if (!matches.contains(word)) {
			matches.add(word);
		}
	}

	private static String signature(String value) {
		char[] letters = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").toCharArray();
		Arrays.sort(letters);
		return new String(letters);
	}

	private static String applyCapitalization(String answer, String scrambled) {
		Map<Character, Integer> uppercase = new LinkedHashMap<>();
		for (int index = 0; index < scrambled.length(); index++) {
			char character = scrambled.charAt(index);
			if (character >= 'A' && character <= 'Z') {
				char lower = Character.toLowerCase(character);
				uppercase.merge(lower, 1, Integer::sum);
			}
		}
		StringBuilder result = new StringBuilder(answer.length());
		for (int index = 0; index < answer.length(); index++) {
			char character = answer.charAt(index);
			int remaining = uppercase.getOrDefault(character, 0);
			if (remaining > 0) {
				result.append(Character.toUpperCase(character));
				uppercase.put(character, remaining - 1);
			} else {
				result.append(character);
			}
		}
		return result.toString();
	}
}
