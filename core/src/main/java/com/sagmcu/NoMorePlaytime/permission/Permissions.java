package com.sagmcu.NoMorePlaytime.permission;

import com.sagmcu.NoMorePlaytime.platform.Source;

import java.util.UUID;

/**
 * Permission nodes, answered by LuckPerms when it is installed. Without a verdict from LuckPerms a node falls back to its
 * default: operators for the admin commands, everybody for toggling your own timer bar.
 */
public final class Permissions {
	/** Grants every command below (and can still be overridden for a single one by setting that node to false). */
	public static final String ADMIN = "nomoreplaytime.admin";

	public static final String HELP = "nomoreplaytime.help";
	public static final String SET = "nomoreplaytime.set";
	public static final String ADD = "nomoreplaytime.add";
	public static final String REMOVE = "nomoreplaytime.remove";
	public static final String MAX = "nomoreplaytime.max";
	public static final String PAUSE = "nomoreplaytime.pause";
	public static final String RESUME = "nomoreplaytime.resume";
	public static final String RESET = "nomoreplaytime.reset";
	public static final String CHECK = "nomoreplaytime.check";
	public static final String RELOAD = "nomoreplaytime.reload";

	/** /timer on|off for yourself; everybody by default. */
	public static final String TOGGLE_TIMER = "nomoreplaytime.toggletimer";
	/** /timer on|off for another player. */
	public static final String TOGGLE_TIMER_OTHERS = "nomoreplaytime.toggletimer.others";

	private static final String[] NMP_COMMANDS = {HELP, SET, ADD, REMOVE, MAX, PAUSE, RESUME, RESET, CHECK, RELOAD};

	/** Set once a lookup finds that LuckPerms' classes are not there, so that it is not tried again. */
	private static boolean luckPermsMissing;

	private Permissions() {
	}

	/**
	 * @param everyoneByDefault whether players without a verdict from LuckPerms may use the node; otherwise only operators
	 *                          (and the console, command blocks...) may
	 */
	public static boolean has(Source source, String node, boolean everyoneByDefault) {
		UUID player = source.uuid();
		if (player != null) {
			Boolean verdict = query(player, node);
			if (verdict != null)
				return verdict;
			if (!node.equals(ADMIN) && Boolean.TRUE.equals(query(player, ADMIN)))
				return true;
		}
		return everyoneByDefault || source.isOp();
	}

	/** Whether the source may use at least one /nmp subcommand, which is what makes the command visible to it. */
	public static boolean hasAnyNmpCommand(Source source) {
		for (String node : NMP_COMMANDS) {
			if (has(source, node, false))
				return true;
		}
		return false;
	}

	/** LuckPerms' verdict for an online player: true or false when a node says so, null when it has none. */
	private static Boolean query(UUID player, String node) {
		if (luckPermsMissing)
			return null;
		try {
			return LuckPermsHook.query(player, node);
		} catch (LinkageError e) {
			luckPermsMissing = true;
			return null;
		} catch (IllegalStateException e) {
			// LuckPerms is installed but has not finished loading yet
			return null;
		}
	}
}
