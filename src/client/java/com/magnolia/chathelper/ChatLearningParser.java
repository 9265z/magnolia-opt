package com.magnolia.chathelper;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

final class ChatLearningParser {
	private static final Pattern ROUND_WINNER = Pattern.compile("(?i)\\b([a-z0-9_]{1,20})\\s+was\\s+first!");
	private static final Pattern TRAILING_PLAYER = Pattern.compile(
			"(?i)([a-z0-9_]{1,20})(?:\\s+\\[[^]]*])?\\s*$");

	private ChatLearningParser() {
	}

	static Optional<String> winnerName(String message) {
		var matcher = ROUND_WINNER.matcher(clean(message));
		return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
	}

	static String playerAnswer(String playerName, String decoratedMessage) {
		String clean = clean(decoratedMessage);
		int playerIndex = clean.toLowerCase(Locale.ROOT).indexOf(playerName.toLowerCase(Locale.ROOT));
		if (playerIndex < 0) {
			return null;
		}
		int start = playerIndex + playerName.length();
		int separator = separatorAfter(clean, start);
		if (separator < 0 || separator + 1 >= clean.length()) {
			return null;
		}
		String answer = clean.substring(separator + 1).trim();
		return answer.isBlank() || answer.length() > 128 ? null : answer;
	}

	static Optional<FormattedPlayerAnswer> formattedPlayerAnswer(String decoratedMessage) {
		String clean = clean(decoratedMessage);
		int separator = separatorAfter(clean, 0);
		if (separator <= 0 || separator + 1 >= clean.length()) {
			return Optional.empty();
		}
		var player = TRAILING_PLAYER.matcher(clean.substring(0, separator));
		if (!player.find()) {
			return Optional.empty();
		}
		String answer = clean.substring(separator + 1).trim();
		if (answer.isBlank() || answer.length() > 128) {
			return Optional.empty();
		}
		return Optional.of(new FormattedPlayerAnswer(player.group(1), answer));
	}

	private static int separatorAfter(String clean, int start) {
		for (char marker : new char[] {'»', '›', '>'}) {
			int found = clean.indexOf(marker, start);
			if (found >= 0) {
				return found;
			}
		}
		return clean.indexOf(':', start);
	}

	private static String clean(String value) {
		return value.replaceAll("§.", "").replaceAll("\\s+", " ").trim();
	}

	record FormattedPlayerAnswer(String playerName, String answer) {
	}
}
