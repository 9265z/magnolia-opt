package com.magnolia.chathelper;

record ResponsiveUiLayout(int left, int top, int panelWidth, int panelHeight, int sidebarWidth,
		int contentX, int contentY, int contentWidth, int contentHeight, boolean compact) {
	static ResponsiveUiLayout calculate(int screenWidth, int screenHeight) {
		int margin = Math.max(6, Math.min(14, Math.min(screenWidth, screenHeight) / 28));
		int panelWidth = Math.max(1, Math.min(680, screenWidth - margin * 2));
		int panelHeight = Math.max(1, Math.min(380, screenHeight - margin * 2));
		int left = (screenWidth - panelWidth) / 2;
		int top = (screenHeight - panelHeight) / 2;
		boolean compact = panelWidth < 590 || panelHeight < 325;
		int sidebarWidth = compact ? 0 : 120;
		int contentX = compact ? left + 12 : left + sidebarWidth + 15;
		int contentY = compact ? top + 88 : top + 62;
		int contentWidth = compact ? panelWidth - 24 : panelWidth - sidebarWidth - 27;
		int contentHeight = Math.max(1, panelHeight - (contentY - top) - 27);
		return new ResponsiveUiLayout(left, top, panelWidth, panelHeight, sidebarWidth,
				contentX, contentY, contentWidth, contentHeight, compact);
	}
}
