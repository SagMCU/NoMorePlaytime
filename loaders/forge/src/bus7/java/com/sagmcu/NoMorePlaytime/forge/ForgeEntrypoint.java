package com.sagmcu.NoMorePlaytime.forge;

import com.sagmcu.NoMorePlaytime.Main;
import com.sagmcu.NoMorePlaytime.mc.McPlatform;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Path;

/** Forge 1.21.6 and later: every event class has its own BUS. */
@Mod("nomoreplaytime")
public final class ForgeEntrypoint {
	public ForgeEntrypoint() {
		// The server runs from its own folder, so this is the same config folder the loader uses.
		Path directory = Path.of("config", "nomoreplaytime");

		ServerStartedEvent.BUS.addListener(event -> Main.start(new McPlatform(event.getServer()), directory));
		ServerStoppingEvent.BUS.addListener(event -> Main.stop());
		TickEvent.ServerTickEvent.Post.BUS.addListener(event -> Main.tick());

		PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
			if (event.getEntity() instanceof ServerPlayer player)
				Main.join(player.getUUID(), player.getScoreboardName());
		});
		PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> Main.leave(event.getEntity().getUUID()));

		RegisterCommandsEvent.BUS.addListener(event ->
				Main.<CommandSourceStack>commands(McPlatform::source).forEach(event.getDispatcher()::register));
	}
}
