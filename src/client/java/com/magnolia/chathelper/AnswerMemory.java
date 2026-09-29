package com.magnolia.chathelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class AnswerMemory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path path = FabricLoader.getInstance().getConfigDir()
			.resolve("magnolia-chat-helper")
			.resolve("memory.json");
	private Data data = new Data();

	void load() {
		if (!Files.exists(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			if (loaded != null && loaded.entries != null) {
				data = loaded;
			}
		} catch (IOException | RuntimeException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not load answer memory from {}", path, exception);
		}
	}

	Optional<String> answerFor(GamePrompt prompt) {
		MemoryEntry entry = data.entries.get(prompt.key());
		if (entry == null || entry.answer == null || entry.answer.isBlank() || !prompt.accepts(entry.answer)) {
			return Optional.empty();
		}
		entry.timesSeen++;
		return Optional.of(entry.answer);
	}

	void remember(GamePrompt prompt, String answer) {
		String normalized = prompt.preservesCase()
				? answer.trim()
				: answer.trim().toLowerCase(Locale.ROOT);
		MemoryEntry previous = data.entries.get(prompt.key());
		int seen = previous == null ? 1 : previous.timesSeen + 1;
		data.entries.put(prompt.key(), new MemoryEntry(prompt.clue(), normalized, seen, Instant.now().toString()));
		save();
	}

	boolean forget(GamePrompt prompt) {
		boolean removed = data.entries.remove(prompt.key()) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	int size() {
		return data.entries.size();
	}

	boolean enabled() {
		return data.enabled;
	}

	void setEnabled(boolean enabled) {
		data.enabled = enabled;
		save();
	}

	Path path() {
		return path;
	}

	private void save() {
		try {
			Files.createDirectories(path.getParent());
			Path temporary = path.resolveSibling("memory.json.tmp");
			try (Writer writer = Files.newBufferedWriter(temporary)) {
				GSON.toJson(data, writer);
			}
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not save answer memory to {}", path, exception);
		}
	}

	private static final class Data {
		int version = 1;
		boolean enabled = true;
		Map<String, MemoryEntry> entries = new LinkedHashMap<>();
	}

	private static final class MemoryEntry {
		String clue;
		String answer;
		int timesSeen;
		String updatedAt;

		MemoryEntry(String clue, String answer, int timesSeen, String updatedAt) {
			this.clue = clue;
			this.answer = answer;
			this.timesSeen = timesSeen;
			this.updatedAt = updatedAt;
		}
	}
}
