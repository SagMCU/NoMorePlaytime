package com.sagmcu.NoMorePlaytime.platform;

import com.sagmcu.NoMorePlaytime.text.Text;

import java.util.UUID;

/** Everything the mod needs from the running game; implemented once per Minecraft API generation. */
public interface Platform {
	/** Whether the online player has operator permissions (an offline player is never an operator). */
	boolean isOp(UUID player);

	void send(UUID player, Text message);

	void kick(UUID player, Text reason);

	void playSound(UUID player, String soundId, float volume, float pitch);

	/** Creates the player's boss bar on first use, then updates it. {@code color} is a BossBarColor constant name. */
	void showBar(UUID player, Text title, float progress, String color);

	void hideBar(UUID player);
}
