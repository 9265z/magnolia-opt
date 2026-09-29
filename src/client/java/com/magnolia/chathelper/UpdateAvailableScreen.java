package com.magnolia.chathelper;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class UpdateAvailableScreen extends Screen {
	private static final int PANEL = 0xF2111111;
	private static final int BORDER = 0xFF69645A;
	private static final int GOLD = 0xFFFFC857;
	private static final int GREEN = 0xFF72D572;
	private static final int RED = 0xFFFF6B6B;
	private static final int MUTED = 0xFFA7A7A7;
	private final MagnoliaChatHelperClient helper;
	private final Screen parent;
	private Button updateButton;

	UpdateAvailableScreen(MagnoliaChatHelperClient helper, Screen parent) {
		super(Component.literal("Magnolia OPT Update"));
		this.helper = helper;
		this.parent = parent;
	}

	@Override
	protected void init() {
		int panelWidth = Math.min(410, width - 24);
		int panelHeight = Math.min(190, height - 24);
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		int innerWidth = panelWidth - 32;

		updateButton = addRenderableWidget(Button.builder(Component.literal("UPDATE NOW"), button -> {
			if (helper.updateState() == MagnoliaUpdater.State.ERROR) {
				helper.checkForUpdates();
			} else {
				helper.installAvailableUpdate();
			}
		}).bounds(left + 16, top + 125, (innerWidth - 8) / 2, 20)
				.tooltip(Tooltip.create(Component.literal("Download, verify, and replace only MagnoliaOPT.jar"))).build());
		addRenderableWidget(Button.builder(Component.literal("LATER"), button -> onClose())
				.bounds(left + 24 + (innerWidth - 8) / 2, top + 125, (innerWidth - 8) / 2, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		extractTransparentBackground(graphics);
		graphics.fill(0, 0, width, height, 0xB8000000);
		int panelWidth = Math.min(410, width - 24);
		int panelHeight = Math.min(190, height - 24);
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		MagnoliaUpdater.State state = helper.updateState();

		graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
		graphics.outline(left, top, panelWidth, panelHeight, BORDER);
		graphics.fill(left, top, left + panelWidth, top + 3, state == MagnoliaUpdater.State.ERROR ? RED : GOLD);
		graphics.centeredText(font, "Magnolia OPT update", left + panelWidth / 2, top + 14, 0xFFFFFFFF);
		graphics.centeredText(font,
				"Installed " + helper.currentVersion() + "  →  Available " + helper.availableVersion(),
				left + panelWidth / 2, top + 36, GREEN);
		graphics.centeredText(font, "Updates come only from github.com/9265z/magnolia-opt.",
				left + panelWidth / 2, top + 57, MUTED);
		graphics.centeredText(font, "The JAR, SHA-256 digest, mod id, and version are verified first.",
				left + panelWidth / 2, top + 71, MUTED);
		graphics.centeredText(font, trim(helper.updateDetail(), panelWidth - 32),
				left + panelWidth / 2, top + 96, stateColor(state));
		graphics.centeredText(font, state == MagnoliaUpdater.State.INSTALLED
				? "Restart Minecraft when convenient to activate the update."
				: "Choose Later to keep using the installed version for this session.",
				left + panelWidth / 2, top + 109, state == MagnoliaUpdater.State.INSTALLED ? GOLD : MUTED);
		refreshButton(state);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void refreshButton(MagnoliaUpdater.State state) {
		if (updateButton == null) {
			return;
		}
		updateButton.active = state == MagnoliaUpdater.State.AVAILABLE || state == MagnoliaUpdater.State.ERROR;
		updateButton.setMessage(Component.literal(switch (state) {
			case CHECKING -> "CHECKING...";
			case DOWNLOADING -> "DOWNLOADING...";
			case INSTALLED -> "INSTALLED";
			case ERROR -> "RETRY";
			default -> "UPDATE NOW";
		}));
	}

	private static int stateColor(MagnoliaUpdater.State state) {
		return switch (state) {
			case INSTALLED, CURRENT, AVAILABLE -> GREEN;
			case ERROR -> RED;
			default -> GOLD;
		};
	}

	private String trim(String value, int pixelWidth) {
		if (font.width(value) <= pixelWidth) {
			return value;
		}
		return font.plainSubstrByWidth(value, Math.max(10, pixelWidth - font.width("..."))) + "...";
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
