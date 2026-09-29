package com.magnolia.chathelper;

final class RoundedUiMath {
	private static final int SAMPLES_PER_AXIS = 8;
	static final int SAMPLE_COUNT = SAMPLES_PER_AXIS * SAMPLES_PER_AXIS;

	private RoundedUiMath() {
	}

	static int cornerCoverage(int radius, int pixelX, int pixelY) {
		if (radius <= 0) return SAMPLE_COUNT;
		int inside = 0;
		double squaredRadius = radius * (double) radius;
		for (int sy = 0; sy < SAMPLES_PER_AXIS; sy++) {
			double y = pixelY + (sy + 0.5) / SAMPLES_PER_AXIS - radius;
			for (int sx = 0; sx < SAMPLES_PER_AXIS; sx++) {
				double x = pixelX + (sx + 0.5) / SAMPLES_PER_AXIS - radius;
				if (x * x + y * y <= squaredRadius) inside++;
			}
		}
		return inside;
	}

	static int scaleAlpha(int color, int coverage) {
		int clamped = Math.max(0, Math.min(SAMPLE_COUNT, coverage));
		int sourceAlpha = color >>> 24;
		int alpha = (sourceAlpha * clamped + SAMPLE_COUNT / 2) / SAMPLE_COUNT;
		return (alpha << 24) | (color & 0x00FFFFFF);
	}
}
