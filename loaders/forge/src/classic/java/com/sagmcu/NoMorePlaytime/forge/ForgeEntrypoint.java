package com.sagmcu.NoMorePlaytime.forge;

import com.sagmcu.NoMorePlaytime.Main;
import com.sagmcu.NoMorePlaytime.mc.McPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

/** Forge 1.21.1 to 1.21.5: the classic MinecraftForge.EVENT_BUS. */
@Mod("nomoreplaytime")
public final class ForgeEntrypoint {
	public ForgeEntrypoint() {
		// The server runs from its own folder, so this is the same config folder the loader uses.
		Path directory = Path.of("config", "nomoreplaytime");

		MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> Main.start(new McPlatform(event.getServer()), directory));
		MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> Main.stop());
		MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> Main.tick());

		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player)
				Main.join(player.getUUID(), player.getScoreboardName());
		});
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> Main.leave(event.getEntity().getUUID()));

		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
				Main.<CommandSourceStack>commands(McPlatform::source).forEach(event.getDispatcher()::register));
	}
}
