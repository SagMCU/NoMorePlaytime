package com.sagmcu.NoMorePlaytime.mc;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.BossEvent;

/** The Minecraft API of 1.21.11: permission sets replace the numeric levels and ResourceLocation became Identifier. */
final class Compat {
	private Compat() {
	}

	/** Gamemaster permissions or higher, which is what ops have by default. */
	static boolean isOp(CommandSourceStack stack) {
		return stack.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/** The sound event for an id such as "minecraft:entity.pillager.ambient", or null when the id is malformed. */
	static SoundEvent sound(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? null : SoundEvent.createVariableRangeEvent(identifier);
	}

	static ServerBossEvent bar(Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
		return new ServerBossEvent(name, color, overlay);
	}
}
