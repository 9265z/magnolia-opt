package com.magnolia.chathelper;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record GamePrompt(Type type, String clue, String key) {
	private static final Pattern INGREDIENT = Pattern.compile("(?i)\\s*(\\d+)\\s+([a-z][a-z ']*?)\\s*");

	static GamePrompt scramble(String clue) {
		String exact = clue.trim().replaceAll("\\s+", " ");
		char[] letters = lettersOnly(exact).toCharArray();
		Arrays.sort(letters);
		return new GamePrompt(Type.SCRAMBLE, exact, "scramble:" + new String(letters));
	}

	static GamePrompt unreverse(String clue) {
		String exact = clue.trim().replaceAll("\\s+", " ");
		return new GamePrompt(Type.UNREVERSE, exact, "unreverse-v2:" + exact);
	}

	static GamePrompt type(String clue) {
		String exact = clue.trim().replaceAll("\\s+", " ");
		return new GamePrompt(Type.TYPE, exact, "type:" + exact);
	}

	static GamePrompt craft(String ingredients) {
		String normalized = ingredients.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		return new GamePrompt(Type.CRAFT, normalized, "craft:" + canonicalRecipeKey(normalized));
	}

	static GamePrompt question(String question) {
		String normalized = question.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		String keyText = normalized.replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
		return new GamePrompt(Type.QUESTION, normalized, "question:" + keyText);
	}

	boolean accepts(String answer) {
		if (type == Type.TYPE) {
			return clue.equals(answer.trim());
		}
		if (type == Type.SCRAMBLE) {
			return key.equals(scramble(answer).key) && caseSignature(clue).equals(caseSignature(answer));
		}
		if (type == Type.UNREVERSE) {
			return unreverseAnswers().contains(answer.trim());
		}
		return !answer.isBlank();
	}

	String unreverseAnswer() {
		return unreverseAnswers().getFirst();
	}

	List<String> unreverseAnswers() {
		String wordByWord = Arrays.stream(clue.split(" "))
				.map(word -> new StringBuilder(word).reverse().toString())
				.reduce((left, right) -> left + " " + right)
				.orElse("");
		String wholePhrase = new StringBuilder(clue).reverse().toString();
		return wordByWord.equals(wholePhrase) ? List.of(wordByWord) : List.of(wordByWord, wholePhrase);
	}

	boolean preservesCase() {
		return type == Type.TYPE || type == Type.SCRAMBLE || type == Type.UNREVERSE;
	}

	static String canonicalRecipeKey(String ingredients) {
		List<String> parts = Arrays.stream(ingredients.split("\\s*\\+\\s*"))
				.map(String::trim)
				.map(GamePrompt::canonicalIngredient)
				.filter(value -> !value.isEmpty())
				.sorted(Comparator.naturalOrder())
				.toList();
		return String.join("+", parts);
	}

	private static String canonicalIngredient(String value) {
		Matcher matcher = INGREDIENT.matcher(value);
		if (!matcher.matches()) {
			return "";
		}
		return Integer.parseInt(matcher.group(1)) + " " + matcher.group(2).trim().toLowerCase(Locale.ROOT);
	}

	private static String lettersOnly(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
	}

	private static String caseSignature(String value) {
		char[] letters = value.replaceAll("[^a-zA-Z]", "").toCharArray();
		Arrays.sort(letters);
		return new String(letters);
	}

	enum Type {
		SCRAMBLE,
		TYPE,
		UNREVERSE,
		CRAFT,
		QUESTION
	}
}
