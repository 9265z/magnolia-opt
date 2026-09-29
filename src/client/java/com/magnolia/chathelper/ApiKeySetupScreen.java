package com.magnolia.chathelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;

final class ApiKeySetupScreen extends Screen {
	private static final URI API_KEYS_PAGE = URI.create("https://platform.openai.com/api-keys");
	private static final int PANEL = 0xF2111111;
	private static final int BORDER = 0xFF69645A;
	private static final int GOLD = 0xFFFFC857;
	private static final int GREEN = 0xFF72D572;
	private static final int MUTED = 0xFFA7A7A7;
	private final MagnoliaChatHelperClient helper;
	private final Screen parent;
	private EditBox keyBox;
	private String status = "Paste your project API key below.";
	private int statusColor = MUTED;

	ApiKeySetupScreen(MagnoliaChatHelperClient helper, Screen parent) {
		super(Component.literal("OpenAI API Setup"));
		this.helper = helper;
		this.parent = parent;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(390, width - 24);
		int panelHeight = Math.min(198, height - 24);
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		int innerWidth = panelWidth - 32;

		keyBox = new EditBox(font, left + 16, top + 80, innerWidth, 20, Component.literal("OpenAI API key"));
		keyBox.setMaxLength(256);
		keyBox.setHint(Component.literal("sk-...").withStyle(ChatFormatting.DARK_GRAY));
		keyBox.addFormatter((text, offset) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
		addRenderableWidget(keyBox);

		int third = (innerWidth - 8) / 3;
		addRenderableWidget(Button.builder(Component.literal("GET A KEY"), button ->
				ConfirmLinkScreen.confirmLinkNow(this, API_KEYS_PAGE))
				.bounds(left + 16, top + 106, third, 20)
				.tooltip(Tooltip.create(Component.literal("Open the official OpenAI Platform API keys page"))).build());
		addRenderableWidget(Button.builder(Component.literal("PASTE"), button -> {
			keyBox.setValue(minecraft.keyboardHandler.getClipboard().trim());
			status = "Key pasted. Choose Save Key.";
			statusColor = MUTED;
		}).bounds(left + 20 + third, top + 106, third, 20).build());
		addRenderableWidget(Button.builder(Component.literal("SAVE KEY"), button -> {
			String result = helper.saveApiKeyFromGui(keyBox.getValue());
			status = result;
			statusColor = helper.apiReady() ? GREEN : 0xFFFF6B6B;
			if (helper.apiReady()) {
				keyBox.setValue("");
			}
		}).bounds(left + 24 + third * 2, top + 106, third, 20).build());

		addRenderableWidget(Button.builder(Component.literal("NOT NOW"), button -> onClose())
				.bounds(left + 16, top + panelHeight - 28, innerWidth, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		graphics.fill(0, 0, width, height, 0xB8000000);
		int panelWidth = Math.min(390, width - 24);
		int panelHeight = Math.min(198, height - 24);
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;

		graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
		graphics.outline(left, top, panelWidth, panelHeight, BORDER);
		graphics.fill(left, top, left + panelWidth, top + 3, GOLD);
		graphics.centeredText(font, "OpenAI API setup", left + panelWidth / 2, top + 14, 0xFFFFFFFF);
		graphics.centeredText(font, "An API key is needed for questions the local memory cannot answer.",
				left + panelWidth / 2, top + 34, MUTED);
		graphics.centeredText(font, "API usage is billed separately from a ChatGPT subscription.",
				left + panelWidth / 2, top + 47, GOLD);
		graphics.centeredText(font, "Your key is saved locally and displayed as dots here.",
				left + panelWidth / 2, top + 60, MUTED);
		graphics.centeredText(font, status, left + panelWidth / 2, top + 133, statusColor);
		graphics.centeredText(font, "Official destination: platform.openai.com/api-keys",
				left + panelWidth / 2, top + 146, MUTED);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
