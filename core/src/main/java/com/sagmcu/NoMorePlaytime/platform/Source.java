package com.sagmcu.NoMorePlaytime.platform;

import com.sagmcu.NoMorePlaytime.text.Text;

import java.util.UUID;

/** Whoever ran a command: a player, the console, a command block... */
public interface Source {
	boolean isOp();

	/** The player's UUID, or null when the source is not a player. */
	UUID uuid();

	void reply(Text message);

	void fail(Text message);
}
