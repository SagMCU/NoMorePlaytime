package com.sagmcu.NoMorePlaytime.mc;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;

/** The Minecraft API as it is from 1.21.1 to 1.21.10: permission levels are plain numbers and ids are ResourceLocations. */
final class Compat {
	private Compat() {
	}

	/** Permission level 2 (gamemaster) or higher, which is what ops have by default. */
	static boolean isOp(CommandSourceStack stack) {
		return stack.hasPermission(2);
	}

	/** The sound event for an id such as "minecraft:entity.pillager.ambient", or null when the id is malformed. */
	static SoundEvent sound(String id) {
		ResourceLocation location = ResourceLocation.tryParse(id);
		return location == null ? null : SoundEvent.createVariableRangeEvent(location);
	}

	static ServerBossEvent bar(Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
		return new ServerBossEvent(name, color, overlay);
	}
}
