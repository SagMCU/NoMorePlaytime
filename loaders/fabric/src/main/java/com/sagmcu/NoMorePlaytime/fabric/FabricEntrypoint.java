package com.sagmcu.NoMorePlaytime.fabric;

import com.sagmcu.NoMorePlaytime.Main;
import com.sagmcu.NoMorePlaytime.mc.McPlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

public final class FabricEntrypoint implements ModInitializer {
	@Override
	public void onInitialize() {
		Path directory = FabricLoader.getInstance().getConfigDir().resolve("nomoreplaytime");

		ServerLifecycleEvents.SERVER_STARTED.register(server -> Main.start(new McPlatform(server), directory));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Main.stop());
		ServerTickEvents.END_SERVER_TICK.register(server -> Main.tick());

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			Main.join(player.getUUID(), player.getScoreboardName());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Main.leave(handler.getPlayer().getUUID()));

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				Main.<CommandSourceStack>commands(McPlatform::source).forEach(dispatcher::register));
	}
}
