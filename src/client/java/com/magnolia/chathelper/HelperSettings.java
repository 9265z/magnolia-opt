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

final class HelperSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path path = FabricLoader.getInstance().getConfigDir()
			.resolve("magnolia-chat-helper")
			.resolve("settings.json");
	private Data data = new Data();

	void load() {
		if (!Files.exists(path)) {
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			Data loaded = GSON.fromJson(reader, Data.class);
			if (loaded != null) {
				if (loaded.version < 5) {
					loaded.learnFromReveals = true;
					loaded.learnFromWinners = true;
				}
				if (loaded.version < 6) {
					loaded.updateChecksEnabled = true;
				}
				if (loaded.version < 7) {
					loaded.submissionChancePercent = 80;
				}
				if (loaded.version < 8) {
					loaded.uiStyle = "GLASS";
					loaded.uiColor = "PURPLE";
					loaded.version = 8;
				}
				if (loaded.version < 9) {
					loaded.uiLightSpeed = UiLightSpeed.NORMAL.name();
					loaded.version = 9;
				}
				loaded.submissionChancePercent = clampPercent(loaded.submissionChancePercent);
				loaded.uiStyle = validStyle(loaded.uiStyle);
				loaded.uiColor = validColor(loaded.uiColor);
				loaded.uiLightSpeed = validLightSpeed(loaded.uiLightSpeed);
				data = loaded;
				save();
			}
		} catch (IOException | RuntimeException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not load helper settings from {}", path, exception);
		}
	}

	boolean autoSubmitEnabled() {
		return data.autoSubmitEnabled;
	}

	void setAutoSubmitEnabled(boolean enabled) {
		data.autoSubmitEnabled = enabled;
		save();
	}

	int submissionChancePercent() {
		return clampPercent(data.submissionChancePercent);
	}

	void setSubmissionChancePercent(int percent) {
		data.submissionChancePercent = clampPercent(percent);
		save();
	}

	boolean autoAnswerQuestions() {
		return data.autoAnswerQuestions;
	}

	void setAutoAnswerQuestions(boolean enabled) {
		data.autoAnswerQuestions = enabled;
		save();
	}

	boolean learningEnabled() {
		return data.learningEnabled;
	}

	void setLearningEnabled(boolean enabled) {
		data.learningEnabled = enabled;
		save();
	}

	boolean learnFromReveals() {
		return data.learnFromReveals;
	}

	void setLearnFromReveals(boolean enabled) {
		data.learnFromReveals = enabled;
		save();
	}

	boolean learnFromWinners() {
		return data.learnFromWinners;
	}

	void setLearnFromWinners(boolean enabled) {
		data.learnFromWinners = enabled;
		save();
	}

	boolean autoWelcomeEnabled() {
		return data.autoWelcomeEnabled;
	}

	void setAutoWelcomeEnabled(boolean enabled) {
		data.autoWelcomeEnabled = enabled;
		save();
	}

	boolean updateChecksEnabled() {
		return data.updateChecksEnabled;
	}

	void setUpdateChecksEnabled(boolean enabled) {
		data.updateChecksEnabled = enabled;
		save();
	}

	String uiStyle() {
		return validStyle(data.uiStyle);
	}

	void setUiStyle(String style) {
		data.uiStyle = validStyle(style);
		save();
	}

	String uiColor() {
		return validColor(data.uiColor);
	}

	void setUiColor(String color) {
		data.uiColor = validColor(color);
		save();
	}

	String uiLightSpeed() {
		return validLightSpeed(data.uiLightSpeed);
	}

	void setUiLightSpeed(String speed) {
		data.uiLightSpeed = validLightSpeed(speed);
		save();
	}

	Path path() {
		return path;
	}

	private void save() {
		try {
			Files.createDirectories(path.getParent());
			Path temporary = path.resolveSibling("settings.json.tmp");
			try (Writer writer = Files.newBufferedWriter(temporary)) {
				GSON.toJson(data, writer);
			}
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			MagnoliaChatHelperClient.LOGGER.error("Could not save helper settings to {}", path, exception);
		}
	}

	private static int clampPercent(int percent) {
		return Math.max(0, Math.min(100, percent));
	}

	private static String validStyle(String style) {
		return "MINECRAFT".equalsIgnoreCase(style) ? "MINECRAFT" : "GLASS";
	}

	private static String validColor(String color) {
		if (color == null) {
			return "PURPLE";
		}
		return switch (color.toUpperCase(java.util.Locale.ROOT)) {
			case "RED", "BLUE", "PURPLE", "PINK", "BLACK", "WHITE" -> color.toUpperCase(java.util.Locale.ROOT);
			default -> "PURPLE";
		};
	}

	private static String validLightSpeed(String speed) {
		return UiLightSpeed.parse(speed).name();
	}

	private static final class Data {
		int version = 9;
		boolean autoSubmitEnabled = true;
		boolean autoAnswerQuestions = true;
		boolean learningEnabled = true;
		boolean learnFromReveals = true;
		boolean learnFromWinners = true;
		boolean autoWelcomeEnabled = true;
		boolean updateChecksEnabled = true;
		int submissionChancePercent = 80;
		String uiStyle = "GLASS";
		String uiColor = "PURPLE";
		String uiLightSpeed = UiLightSpeed.NORMAL.name();
	}
}
