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

	static double perimeterLength(int width, int height, int radius) {
		int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
		double straightWidth = Math.max(0, width - 2.0 * r);
		double straightHeight = Math.max(0, height - 2.0 * r);
		return 2.0 * (straightWidth + straightHeight) + 2.0 * Math.PI * r;
	}

	static Point perimeterPoint(int width, int height, int radius, double distance) {
		int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
		double horizontal = Math.max(0, width - 2.0 * r);
		double vertical = Math.max(0, height - 2.0 * r);
		double arc = Math.PI * r / 2.0;
		double perimeter = perimeterLength(width, height, r);
		double d = perimeter == 0.0 ? 0.0 : ((distance % perimeter) + perimeter) % perimeter;
		if (d < horizontal) return new Point(r + d, 0);
		d -= horizontal;
		if (d < arc) return arcPoint(width - r, r, r, -Math.PI / 2.0 + d / r);
		d -= arc;
		if (d < vertical) return new Point(width, r + d);
		d -= vertical;
		if (d < arc) return arcPoint(width - r, height - r, r, d / r);
		d -= arc;
		if (d < horizontal) return new Point(width - r - d, height);
		d -= horizontal;
		if (d < arc) return arcPoint(r, height - r, r, Math.PI / 2.0 + d / r);
		d -= arc;
		if (d < vertical) return new Point(0, height - r - d);
		d -= vertical;
		return arcPoint(r, r, r, Math.PI + d / Math.max(1, r));
	}

	private static Point arcPoint(double centerX, double centerY, double radius, double angle) {
		return new Point(centerX + Math.cos(angle) * radius, centerY + Math.sin(angle) * radius);
	}

	record Point(double x, double y) {
	}
}
