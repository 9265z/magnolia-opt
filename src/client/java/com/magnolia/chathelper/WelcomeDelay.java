package com.magnolia.chathelper;

import java.util.concurrent.ThreadLocalRandom;

final class WelcomeDelay {
	static final long MIN_MILLIS = 2_000L;
	static final long MAX_MILLIS = 4_000L;

	private WelcomeDelay() {
	}

	static long nextMillis() {
		return ThreadLocalRandom.current().nextLong(MIN_MILLIS, MAX_MILLIS + 1L);
	}
}
