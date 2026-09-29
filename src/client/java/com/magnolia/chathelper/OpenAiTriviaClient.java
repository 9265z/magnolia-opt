package com.magnolia.chathelper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class OpenAiTriviaClient {
	private static final URI RESPONSES_API = URI.create("https://api.openai.com/v1/responses");
	private static final String INSTRUCTIONS = "Answer the Minecraft trivia question for a fast in-game chat contest. "
			+ "Return only 1 to 3 short candidate answers, most likely first, one per line. "
			+ "Do not number them and do not explain. Each line must be exactly what the player should type.";

	private volatile OpenAiConfig config;
	private volatile HttpClient httpClient;
	private volatile String lastStatus = "Not tested yet";

	OpenAiTriviaClient(OpenAiConfig config) {
		reload(config);
	}

	void reload(OpenAiConfig newConfig) {
		config = newConfig;
		httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(newConfig.timeoutSeconds()))
				.build();
	}

	boolean ready() {
		return config.ready();
	}

	String status() {
		return lastStatus;
	}

	String model() {
		return config.model();
	}

	CompletableFuture<List<String>> answer(String question) {
		OpenAiConfig requestConfig = config;
		String apiKey = requestConfig.resolvedApiKey();
		if (!requestConfig.enabled || apiKey == null) {
			lastStatus = "API key missing or API disabled";
			return CompletableFuture.failedFuture(new IllegalStateException("OpenAI API is not configured"));
		}

		JsonObject body = new JsonObject();
		body.addProperty("model", requestConfig.model());
		body.addProperty("instructions", INSTRUCTIONS);
		body.addProperty("input", question);
		JsonObject reasoning = new JsonObject();
		reasoning.addProperty("effort", "none");
		body.add("reasoning", reasoning);
		body.addProperty("max_output_tokens", 256);
		body.addProperty("store", false);

		HttpRequest request = HttpRequest.newBuilder(RESPONSES_API)
				.timeout(Duration.ofSeconds(requestConfig.timeoutSeconds()))
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();

		lastStatus = "Request in progress";
		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					lastStatus = "HTTP " + response.statusCode();
					if (response.statusCode() < 200 || response.statusCode() >= 300) {
						throw new IllegalStateException(apiError(response.statusCode(), response.body()));
					}
					List<String> answers = parseAnswers(response.body());
					if (answers.isEmpty()) {
						throw new IllegalStateException(emptyOutputError(response.body()));
					}
					lastStatus = "Last request succeeded";
					return answers;
				})
				.whenComplete((answers, error) -> {
					if (error != null) {
						Throwable cause = error.getCause() == null ? error : error.getCause();
						lastStatus = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
					}
				});
	}

	static List<String> parseAnswers(String responseBody) {
		JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
		JsonArray output = root.has("output") && root.get("output").isJsonArray()
				? root.getAsJsonArray("output") : new JsonArray();
		StringBuilder text = new StringBuilder();
		for (JsonElement outputElement : output) {
			if (!outputElement.isJsonObject()) {
				continue;
			}
			JsonObject outputItem = outputElement.getAsJsonObject();
			if (!outputItem.has("content") || !outputItem.get("content").isJsonArray()) {
				continue;
			}
			for (JsonElement contentElement : outputItem.getAsJsonArray("content")) {
				if (!contentElement.isJsonObject()) {
					continue;
				}
				JsonObject content = contentElement.getAsJsonObject();
				if (content.has("text") && content.get("text").isJsonPrimitive()) {
					text.append(content.get("text").getAsString()).append('\n');
				}
			}
		}
		return sanitizeAnswers(text.toString());
	}

	private static List<String> sanitizeAnswers(String rawText) {
		Set<String> unique = new LinkedHashSet<>();
		for (String line : rawText.split("\\R")) {
			String answer = line
					.replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s*", "")
					.replaceAll("^[\\\"'`]+|[\\\"'`]+$", "")
					.trim();
			if (!answer.isEmpty() && answer.length() <= 80 && !answer.startsWith("/")) {
				unique.add(answer);
			}
			if (unique.size() == 3) {
				break;
			}
		}
		return new ArrayList<>(unique);
	}

	private static String apiError(int statusCode, String body) {
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			if (root.has("error") && root.get("error").isJsonObject()) {
				JsonObject error = root.getAsJsonObject("error");
				if (error.has("message")) {
					return "OpenAI API error " + statusCode + ": " + error.get("message").getAsString();
				}
			}
		} catch (RuntimeException ignored) {
			// Fall through to a status-only error; never print arbitrary response bodies into chat.
		}
		return "OpenAI API error " + statusCode;
	}

	private static String emptyOutputError(String body) {
		try {
			JsonObject root = JsonParser.parseString(body).getAsJsonObject();
			if (root.has("status") && "incomplete".equals(root.get("status").getAsString())) {
				String reason = "unknown reason";
				if (root.has("incomplete_details") && root.get("incomplete_details").isJsonObject()) {
					JsonObject details = root.getAsJsonObject("incomplete_details");
					if (details.has("reason")) {
						reason = details.get("reason").getAsString();
					}
				}
				return "OpenAI response was incomplete: " + reason;
			}
		} catch (RuntimeException ignored) {
			// Return a stable error without exposing an arbitrary response body.
		}
		return "OpenAI returned no usable answer";
	}
}
