package com.sagmcu.NoMorePlaytime.neoforge;

import com.sagmcu.NoMorePlaytime.Main;
import com.sagmcu.NoMorePlaytime.mc.McPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Path;

@Mod("nomoreplaytime")
public final class NeoForgeEntrypoint {
	public NeoForgeEntrypoint() {
		// The server runs from its own folder, so this is the same config folder the loader uses.
		Path directory = Path.of("config", "nomoreplaytime");

		NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> Main.start(new McPlatform(event.getServer()), directory));
		NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> Main.stop());
		NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> Main.tick());

		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player)
				Main.join(player.getUUID(), player.getScoreboardName());
		});
		NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> Main.leave(event.getEntity().getUUID()));

		NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
				Main.<CommandSourceStack>commands(McPlatform::source).forEach(event.getDispatcher()::register));
	}
}
