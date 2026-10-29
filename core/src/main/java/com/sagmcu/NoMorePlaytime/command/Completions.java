package com.sagmcu.NoMorePlaytime.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Tab completion shared by the commands. */
final class Completions {
	private static final Pattern DURATION_START = Pattern.compile("(\\d+[hms])*\\d*");

	private Completions() {
	}

	/** Player names (case-insensitive prefix match), optionally followed by the "global" target. */
	static CompletableFuture<Suggestions> names(SuggestionsBuilder builder, Collection<String> names, boolean includeGlobal) {
		String typed = builder.getRemainingLowerCase();
		if (includeGlobal && "global".startsWith(typed))
			builder.suggest("global");
		for (String name : names) {
			if (name.toLowerCase(Locale.ROOT).startsWith(typed))
				builder.suggest(name);
		}
		return builder.buildFuture();
	}

	/**
	 * A duration made of several words ("1h 2m 4s"): completes the word being typed with the units not used yet, offers
	 * example amounts for a fresh word and, where {@code allowFlag} is set, the "--add" flag.
	 */
	static CompletableFuture<Suggestions> duration(SuggestionsBuilder builder, boolean allowFlag) {
		String remaining = builder.getRemaining();
		int space = remaining.lastIndexOf(' ');
		String before = remaining.substring(0, space + 1).toLowerCase(Locale.ROOT);
		String word = remaining.substring(space + 1).toLowerCase(Locale.ROOT);
		SuggestionsBuilder wordBuilder = builder.createOffset(builder.getStart() + space + 1);

		boolean flagOffered = allowFlag && !before.contains("--add");
		String used = before + word;
		if (DURATION_START.matcher(word).matches()) {
			if (word.isEmpty()) {
				suggestUnit(wordBuilder, used, 'h', "1h");
				suggestUnit(wordBuilder, used, 'm', "30m");
				suggestUnit(wordBuilder, used, 's', "10s");
				if (flagOffered && !before.isBlank())
					wordBuilder.suggest("--add");
			} else if (Character.isDigit(word.charAt(word.length() - 1))) {
				suggestUnit(wordBuilder, used, 'h', word + "h");
				suggestUnit(wordBuilder, used, 'm', word + "m");
				suggestUnit(wordBuilder, used, 's', word + "s");
			}
		} else if (flagOffered && "--add".startsWith(word)) {
			wordBuilder.suggest("--add");
		}
		return wordBuilder.buildFuture();
	}

	private static void suggestUnit(SuggestionsBuilder builder, String used, char unit, String suggestion) {
		if (used.indexOf(unit) < 0)
			builder.suggest(suggestion);
	}
}
