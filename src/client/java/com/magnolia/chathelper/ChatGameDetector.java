package com.magnolia.chathelper;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ChatGameDetector {
	private static final Pattern UNSCRAMBLE_KEYWORD = Pattern.compile(
			"(?i)\\b(?:unscramble|unshuffle|descramble|anagram)\\b");
	private static final Pattern UNSCRAMBLE_HEADER = Pattern.compile(
			"(?i)\\b(?:unscramble|unshuffle|descramble|anagram)\\s+(?:the\\s+)?(?:word|text|phrase)\\b");
	private static final Pattern UNREVERSE_HEADER = Pattern.compile(
			"(?i)\\b(?:unreverse|reverse)\\s+(?:the\\s+)?(?:word|text|phrase)\\b");
	private static final Pattern TYPE_KEYWORD = Pattern.compile(
			"(?i)\\b(?:type|write|repeat)\\b");
	private static final Pattern CHAT_GAME_MARKER = Pattern.compile("(?i)\\bchat\\s+games?\\b");
	private static final Pattern RANDOM_TEXT_HEADER = Pattern.compile("(?i)\\btype\\s+the\\s+random\\s+text\\b");
	private static final Pattern RANDOM_TEXT_VALUE = Pattern.compile("^[^a-zA-Z0-9]*([a-zA-Z0-9]{2,64})[^a-zA-Z0-9]*$");
	private static final Pattern CRAFT_HEADER = Pattern.compile("(?i)\\bname\\s+the\\s+crafted\\s+item\\b");
	private static final Pattern QUESTION_HEADER = Pattern.compile("(?i)\\banswer\\s+the\\s+question\\b");
	private static final Pattern IGNORED_EVENT = Pattern.compile(
			"(?i)(?:\\bgg\\s+wave\\b|\\btype\\s+gg\\b|\\b(?:just\\s+)?purchased\\b|"
					+ "\\bpurchase\\s+event\\b|\\bthank\\s+you\\s+for\\s+helping\\s+magnolia\\s+bloom\\b)");
	private static final Pattern QUOTED = Pattern.compile("[\\\"'`\\[({<]\\s*([a-z][a-z '-]{1,39}?)\\s*[\\\"'`\\])}>]", Pattern.CASE_INSENSITIVE);
	private static final Pattern WORD = Pattern.compile("[a-z]{2,32}", Pattern.CASE_INSENSITIVE);
	private static final Pattern REVEAL = Pattern.compile(
			"(?i)\\b(?:correct\\s+(?:answer|word)(?:\\s+(?:is|was))?|answer\\s+(?:is|was)|word\\s+(?:is|was))\\b\\s*[:=\\-]?\\s*[\\\"'`]?([a-z][a-z' -]{1,49}?)[\\\"'`]?(?=[.!]|$)");
	private static final Pattern LABELED_REVEAL = Pattern.compile(
			"(?i)\\banswer\\s*[»>:=-]+\\s*[\\\"'`]?([a-z0-9][a-z0-9' .-]{0,49}?)[\\\"'`]?(?=[.!]|$)");
	private static final Set<String> FILLERS = Set.of(
			"the", "word", "words", "letters", "letter", "is", "are", "in", "chat", "to", "win", "first", "type", "write", "repeat");
	private boolean waitingForCraftIngredients;
	private boolean waitingForQuestion;
	private boolean waitingForRandomText;
	private boolean waitingForUnscramble;
	private boolean waitingForUnreverse;

	Optional<GamePrompt> detect(String message) {
		String clean = normalizeDecorations(message);
		boolean hasChatGameMarker = CHAT_GAME_MARKER.matcher(clean).find();
		if (isIgnoredEvent(clean)) {
			return Optional.empty();
		}
		Matcher randomTextHeader = RANDOM_TEXT_HEADER.matcher(clean);
		if (randomTextHeader.find()) {
			waitingForRandomText = true;
			String value = extractRandomText(clean.substring(randomTextHeader.end()));
			if (value != null) {
				waitingForRandomText = false;
				return Optional.of(GamePrompt.type(value));
			}
			return Optional.empty();
		}
		if (waitingForRandomText) {
			String value = extractRandomText(clean);
			if (value != null) {
				waitingForRandomText = false;
				return Optional.of(GamePrompt.type(value));
			}
		}
		Matcher unreverseHeader = UNREVERSE_HEADER.matcher(clean);
		if (unreverseHeader.find()) {
			waitingForUnreverse = true;
			String value = extractPuzzleText(clean.substring(unreverseHeader.end()));
			if (value != null) {
				waitingForUnreverse = false;
				return Optional.of(GamePrompt.unreverse(value));
			}
			return Optional.empty();
		}
		if (waitingForUnreverse) {
			String value = extractPuzzleText(clean);
			if (value != null) {
				waitingForUnreverse = false;
				return Optional.of(GamePrompt.unreverse(value));
			}
		}
		Matcher unscrambleHeader = UNSCRAMBLE_HEADER.matcher(clean);
		if (unscrambleHeader.find()) {
			waitingForUnscramble = true;
			String value = extractPuzzleText(clean.substring(unscrambleHeader.end()));
			if (value != null) {
				waitingForUnscramble = false;
				return Optional.of(GamePrompt.scramble(value));
			}
			return Optional.empty();
		}
		if (waitingForUnscramble) {
			String value = extractPuzzleText(clean);
			if (value != null) {
				waitingForUnscramble = false;
				return Optional.of(GamePrompt.scramble(value));
			}
		}
		Matcher questionHeader = QUESTION_HEADER.matcher(clean);
		if (questionHeader.find()) {
			waitingForQuestion = true;
			String question = extractQuestion(clean.substring(questionHeader.end()));
			if (question != null) {
				waitingForQuestion = false;
				return Optional.of(GamePrompt.question(question));
			}
			return Optional.empty();
		}
		if (waitingForQuestion) {
			String question = extractQuestion(clean);
			if (question != null) {
				waitingForQuestion = false;
				return Optional.of(GamePrompt.question(question));
			}
		}

		Matcher craftHeader = CRAFT_HEADER.matcher(clean);
		if (craftHeader.find()) {
			waitingForCraftIngredients = true;
			String ingredients = extractIngredients(clean.substring(craftHeader.end()));
			if (ingredients != null) {
				waitingForCraftIngredients = false;
				return Optional.of(GamePrompt.craft(ingredients));
			}
			return Optional.empty();
		}
		if (waitingForCraftIngredients) {
			String ingredients = extractIngredients(clean);
			if (ingredients != null) {
				waitingForCraftIngredients = false;
				return Optional.of(GamePrompt.craft(ingredients));
			}
		}

		Matcher scrambleKeyword = UNSCRAMBLE_KEYWORD.matcher(clean);
		if (hasChatGameMarker && scrambleKeyword.find()) {
			String clue = extractClue(clean.substring(scrambleKeyword.end()), false);
			if (clue != null) {
				return Optional.of(GamePrompt.scramble(clue));
			}
		}

		Matcher typeKeyword = TYPE_KEYWORD.matcher(clean);
		if (hasChatGameMarker && typeKeyword.find()) {
			String clue = extractClue(clean.substring(typeKeyword.end()), false);
			if (clue != null) {
				return Optional.of(GamePrompt.type(clue));
			}
		}

		return Optional.empty();
	}

	boolean isIgnoredEvent(String message) {
		return IGNORED_EVENT.matcher(normalizeDecorations(message)).find();
	}

	private static String extractRandomText(String value) {
		String candidate = value.replaceFirst("(?i)\\s*be\\s+the\\s+first.*$", "").trim();
		Matcher matcher = RANDOM_TEXT_VALUE.matcher(candidate);
		return matcher.matches() ? matcher.group(1) : null;
	}

	private static String extractPuzzleText(String value) {
		String candidate = value
				.replaceFirst("(?i)\\s*be\\s+the\\s+first.*$", "")
				.replaceAll("^[^a-zA-Z]+|[^a-zA-Z]+$", "")
				.replaceAll("\\s+", " ")
				.trim();
		return candidate.matches("[a-zA-Z][a-zA-Z ']{1,63}") ? candidate : null;
	}

	private static String extractIngredients(String value) {
		String candidate = value
				.replaceFirst("(?i)\\s*be\\s+the\\s+first.*$", "")
				.replaceAll("^[^0-9]+|[^a-zA-Z]+$", "")
				.trim();
		if (!candidate.contains("+") || !candidate.matches("(?i)(?:\\d+\\s+[a-z][a-z ']*\\s*)(?:\\+\\s*\\d+\\s+[a-z][a-z ']*)+")) {
			return null;
		}
		return candidate.replaceAll("\\s+", " ");
	}

	private static String extractQuestion(String value) {
		String candidate = value
				.replaceFirst("(?i)\\s*be\\s+the\\s+first.*$", "")
				.replaceAll("^[^a-zA-Z0-9]+", "")
				.trim();
		int questionMark = candidate.indexOf('?');
		return questionMark >= 0 ? candidate.substring(0, questionMark + 1).trim() : null;
	}

	Optional<String> revealedAnswer(String message) {
		String clean = normalizeDecorations(message);
		Matcher labeled = LABELED_REVEAL.matcher(clean);
		if (labeled.find()) {
			return Optional.of(labeled.group(1).trim());
		}
		Matcher matcher = REVEAL.matcher(clean);
		return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
	}

	boolean isRoundFinished(String message) {
		String clean = normalizeDecorations(message).toLowerCase(Locale.ROOT);
		return LABELED_REVEAL.matcher(clean).find()
				|| REVEAL.matcher(clean).find()
				|| clean.contains(" was first!")
				|| clean.contains(" won the chat game")
				|| clean.contains(" answered correctly");
	}

	boolean looksLikeWinFor(String message, String playerName) {
		String lower = normalizeDecorations(message).toLowerCase(Locale.ROOT);
		return lower.contains(playerName.toLowerCase(Locale.ROOT))
				&& (lower.contains(" won") || lower.contains("correct") || lower.contains("solved"));
	}

	private static String extractClue(String remainder, boolean singleWord) {
		Matcher quoted = QUOTED.matcher(remainder);
		if (quoted.find()) {
			String value = quoted.group(1).trim();
			return singleWord ? lastUsefulWord(value) : value;
		}

		int separator = firstSeparator(remainder);
		String candidate = separator >= 0 ? remainder.substring(separator + 1) : remainder;
		candidate = candidate
				.replaceFirst("(?i)^\\s*(?:the\\s+)?(?:word|text|phrase|letters?)?\\s*(?:is|are)?\\s*", "")
				.replaceFirst("(?i)\\s+(?:in\\s+chat|to\\s+win|as\\s+fast.*|quickly.*)$", "")
				.replaceAll("(?i)^[^a-z0-9]+|[^a-z0-9]+$", "")
				.trim();

		if (candidate.isEmpty()) {
			return null;
		}
		return singleWord ? lastUsefulWord(candidate) : candidate;
	}

	private static String lastUsefulWord(String value) {
		Matcher words = WORD.matcher(value);
		String result = null;
		while (words.find()) {
			String word = words.group().toLowerCase(Locale.ROOT);
			if (!FILLERS.contains(word)) {
				result = word;
			}
		}
		return result;
	}

	private static int firstSeparator(String value) {
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character == ':' || character == '>' || character == '=' || character == '-') {
				return i;
			}
		}
		return -1;
	}

	private static String normalizeDecorations(String value) {
		return value.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
	}
}
