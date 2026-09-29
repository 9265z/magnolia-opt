package com.magnolia.chathelper;

import java.util.Locale;

enum UiLightSpeed {
	SLOW(55.0),
	NORMAL(105.0),
	FAST(180.0);

	private final double pixelsPerSecond;

	UiLightSpeed(double pixelsPerSecond) {
		this.pixelsPerSecond = pixelsPerSecond;
	}

	double pixelsPerSecond() {
		return pixelsPerSecond;
	}

	static UiLightSpeed parse(String value) {
		if (value == null) {
			return NORMAL;
		}
		try {
			return valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return NORMAL;
		}
	}
}
