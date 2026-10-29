package com.sagmcu.NoMorePlaytime.time;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and prints durations written with h, m and s ("1h 2m 4s", "90m", "30s", ...). */
public final class TimeFormat {
	/** Longest accepted duration (100 years), so that sums of parsed durations can never overflow. */
	private static final long LIMIT_MS = 100L * 365 * 24 * 3600 * 1000;
	private static final Pattern TOKEN = Pattern.compile("(\\d+)([hms])");

	private TimeFormat() {
	}

	/**
	 * Any combination of hours, minutes and seconds, in any order and with or without spaces; each unit at most once.
	 *
	 * @throws IllegalArgumentException when the text is not such a duration
	 */
	public static long parse(String input) {
		String text = input.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
		if (text.isEmpty())
			throw new IllegalArgumentException("empty duration");

		Matcher matcher = TOKEN.matcher(text);
		Set<Character> seen = new HashSet<>();
		long total = 0;
		int position = 0;
		try {
			while (position < text.length()) {
				matcher.region(position, text.length());
				if (!matcher.lookingAt())
					throw new IllegalArgumentException("not a duration: " + input);
				char unit = matcher.group(2).charAt(0);
				if (!seen.add(unit))
					throw new IllegalArgumentException("repeated unit: " + input);
				long unitMs = unit == 'h' ? 3_600_000L : unit == 'm' ? 60_000L : 1_000L;
				total = Math.addExact(total, Math.multiplyExact(Long.parseLong(matcher.group(1)), unitMs));
				position = matcher.end();
			}
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException("duration too long: " + input);
		}
		if (total > LIMIT_MS)
			throw new IllegalArgumentException("duration too long: " + input);
		return total;
	}

	/** "00h 00m 00s", rounded up to whole seconds so that a timer only reads 00h 00m 00s when it is really over. */
	public static String format(long ms) {
		long seconds = (Math.max(ms, 0) + 999) / 1000;
		return String.format(Locale.ROOT, "%02dh %02dm %02ds", seconds / 3600, seconds % 3600 / 60, seconds % 60);
	}

	/** The form written back to the config: "5h", "1h 30m", "45s". */
	public static String formatCompact(long ms) {
		long seconds = Math.max(ms, 0) / 1000;
		StringBuilder out = new StringBuilder();
		if (seconds >= 3600)
			out.append(seconds / 3600).append("h ");
		if (seconds % 3600 >= 60)
			out.append(seconds % 3600 / 60).append("m ");
		if (seconds % 60 > 0 || out.isEmpty())
			out.append(seconds % 60).append("s ");
		return out.toString().trim();
	}
}
