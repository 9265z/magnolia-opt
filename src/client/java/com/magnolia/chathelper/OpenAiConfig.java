package com.magnolia.chathelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

final class OpenAiConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir()
			.resolve("magnolia-chat-helper")
			.resolve("openai.json");

	boolean enabled = true;
	String apiKey = "";
	String model = "gpt-6-luna";
	int timeoutSeconds = 7;

	static OpenAiConfig load() {
		if (!Files.exists(PATH)) {
			OpenAiConfig config = new OpenAiConfig();
			config.save();
			return config;
		}
		try (Reader reader = Files.newBufferedReader(PATH)) {
			OpenAiConfig config = GSON.fromJson(reader, OpenAiConfig.class);
			return config == null ? new OpenAiConfig() : config;
		} catch (IOException | RuntimeException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not load OpenAI configuration from {}", PATH, exception);
			return new OpenAiConfig();
		}
	}

	boolean ready() {
		return enabled && resolvedApiKey() != null;
	}

	String resolvedApiKey() {
		if (apiKey != null && !apiKey.isBlank()) {
			return apiKey.trim();
		}
		String environmentKey = System.getenv("OPENAI_API_KEY");
		return environmentKey == null || environmentKey.isBlank() ? null : environmentKey.trim();
	}

	String model() {
		return model == null || model.isBlank() ? "gpt-6-luna" : model.trim();
	}

	int timeoutSeconds() {
		return Math.max(3, Math.min(timeoutSeconds, 20));
	}

	static Path path() {
		return PATH;
	}

	static boolean saveApiKey(String rawKey) {
		String key = rawKey == null ? "" : rawKey.trim();
		if (key.length() < 20 || key.chars().anyMatch(Character::isWhitespace)) {
			return false;
		}
		OpenAiConfig config = load();
		config.enabled = true;
		config.apiKey = key;
		config.save();
		return true;
	}

	private void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not create OpenAI configuration at {}", PATH, exception);
		}
	}
}
