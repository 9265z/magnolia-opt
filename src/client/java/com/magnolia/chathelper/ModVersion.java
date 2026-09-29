package com.magnolia.chathelper;

final class ModVersion {
	private ModVersion() {
	}

	static int compare(String left, String right) {
		String[] a = left.replaceFirst("^[vV]", "").split("[-+]", 2)[0].split("\\.");
		String[] b = right.replaceFirst("^[vV]", "").split("[-+]", 2)[0].split("\\.");
		for (int index = 0; index < Math.max(a.length, b.length); index++) {
			int av = index < a.length ? numericPrefix(a[index]) : 0;
			int bv = index < b.length ? numericPrefix(b[index]) : 0;
			int compared = Integer.compare(av, bv);
			if (compared != 0) {
				return compared;
			}
		}
		return 0;
	}

	private static int numericPrefix(String part) {
		String digits = part.replaceFirst("^(\\d+).*$", "$1");
		return digits.matches("\\d+") ? Integer.parseInt(digits) : 0;
	}
}
