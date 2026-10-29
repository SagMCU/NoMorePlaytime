package com.sagmcu.NoMorePlaytime.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Every text players and admins can see. "&amp;" codes ("&amp;#RRGGBB" for hex) are translated and {placeholders} are filled in.
 * {@link #prefix} is put in front of every chat message, but not of the kick and join-denied screens.
 */
public final class Messages {
	public String prefix = "&7&l[&6&l!&7&l]&r ";

	/** Screen shown when a player runs out of time. */
	public String kick = "You've played enough today, see you tomorrow! :D";
	/** Screen shown to a player without time left who tries to join. */
	public String joinDenied = "You've played enough today, see you tomorrow! :D";
	/** {time} is the warning threshold that was just reached. */
	public String warning = "&7You only have &c{time} &7of playtime left!";

	public String set = "&7Set the playtime of &d{player} &7to &a{time}&7.";
	public String add = "&7Added &a{amount} &7to &d{player}&7, who now has &a{time} &7left.";
	public String remove = "&7Removed &c{amount} &7from &d{player}&7, who now has &a{time} &7left.";
	public String setGlobal = "&7Set the playtime of &a{count} &7players to &a{amount}&7.";
	public String addGlobal = "&7Added &a{amount} &7to the playtime of &a{count} &7players.";
	public String removeGlobal = "&7Removed &c{amount} &7from the playtime of &a{count} &7players.";
	public String reset ="&7Reset the timer of &d{player} &7to &a{time}&7.";
	public String resetGlobal = "&7Reset the timers of &a{count} &7players to &a{time}&7.";
	public String pause = "&7Paused the timer of &d{player}&7.";
	public String pauseGlobal = "&7Paused the timers of &a{count} &7players, players joining for the first time start paused too.";
	public String resume = "&7Resumed the timer of &d{player}&7.";
	public String resumeGlobal = "&7Resumed the timers of &a{count} &7players.";
	public String check = "&d{player} &7has &a{time} &7left &8(&e{percent}%&8)&7.";
	public String checkPaused = "&d{player} &7has &a{time} &7left &8(&e{percent}%&8) &c[paused]&7.";
	public String max = "&7The maximum playtime is now &a{time}&7.";
	public String reload = "&7Configuration reloaded.";

	public String barHidden = "&7The timer bar is now &chidden&7. Use &a/timer on &7to show it again.";
	public String barShown = "&7The timer bar is now &ashown&7.";
	public String barHiddenOther = "&7Hid the timer bar of &d{player}&7.";
	public String barShownOther = "&7Showed the timer bar of &d{player}&7.";

	public String unknownPlayer = "&cUnknown player &d{player}&c, they must have joined the server at least once.";
	public String invalidTime = "&cInvalid time &e{input}&c, use h, m and s, for example &e1h 2m 4s&c.";
	public String maxTooLow = "&cThe maximum playtime must be at least &e1s&c.";
	public String playerRequired = "&cSpecify a player: &e/timer {state} <player>&c.";

	public String helpHeader = "&7Available commands:";
	public List<String> help = new ArrayList<>(List.of(
			"&a/nmp set <player|global> <time> &7- Set the time left. &bExample: /nmp set player1 1h 2m 4s",
			"&a/nmp add <player|global> <time> &7- Add time. &bExample: /nmp add global 30m",
			"&a/nmp remove <player|global> <time> &7- Remove time. &bExample: /nmp remove player1 10m 30s",
			"&a/nmp max <time> [--add] &7- Change the maximum. Lowering it keeps each player's percentage left; raising it with --add also gives every player the extra time. &bExample: /nmp max 7h --add",
			"&a/nmp pause <player|global> &7- Pause a timer. &bExample: /nmp pause global",
			"&a/nmp resume <player|global> &7- Resume a timer. &bExample: /nmp resume player1",
			"&a/nmp reset <player|global> &7- Give the maximum time back and resume. &bExample: /nmp reset player1",
			"&a/nmp check <player> &7- Show the time left. &bExample: /nmp check player1",
			"&a/nmp reload &7- Reload the config. &bExample: /nmp reload",
			"&a/timer <on|off> [player] &7- Show or hide the timer bar. &bExample: /timer off",
			"&a/nmp help &7- Show this list. &bExample: /nmp help"));
}
