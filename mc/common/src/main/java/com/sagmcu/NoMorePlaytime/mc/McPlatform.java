package com.sagmcu.NoMorePlaytime.mc;

import com.sagmcu.NoMorePlaytime.platform.Platform;
import com.sagmcu.NoMorePlaytime.platform.Source;
import com.sagmcu.NoMorePlaytime.text.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The part of the mod that touches Minecraft. It only uses classes and methods that are identical across the Minecraft
 * versions a build covers; the two things that differ between API generations live in {@link Compat}.
 */
public final class McPlatform implements Platform {
	private static final class Bar {
		final ServerBossEvent event;
		/** The level the player was in when they were last added, to notice dimension changes. */
		Object level;

		Bar(ServerBossEvent event) {
			this.event = event;
		}
	}

	private final MinecraftServer server;
	private final Map<UUID, Bar> bars = new HashMap<>();

	public McPlatform(MinecraftServer server) {
		this.server = server;
	}

	@Override
	public boolean isOp(UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		return player != null && Compat.isOp(player.createCommandSourceStack());
	}

	@Override
	public void send(UUID id, Text message) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null)
			player.sendSystemMessage(component(message));
	}

	@Override
	public void kick(UUID id, Text reason) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player != null)
			player.connection.disconnect(component(reason));
	}

	@Override
	public void playSound(UUID id, String soundId, float volume, float pitch) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		SoundEvent sound = Compat.sound(soundId);
		if (player == null || sound == null)
			return;
		player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.MASTER,
				player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
	}

	@Override
	public void showBar(UUID id, Text title, float progress, String color) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null)
			return;
		Component name = component(title);
		BossEvent.BossBarColor barColor = BossEvent.BossBarColor.valueOf(color);

		Bar bar = bars.get(id);
		if (bar == null) {
			bar = new Bar(Compat.bar(name, barColor, BossEvent.BossBarOverlay.PROGRESS));
			bars.put(id, bar);
		}
		bar.event.setName(name);
		bar.event.setColor(barColor);
		bar.event.setProgress(progress);

		// A respawn creates a new player object and a dimension change makes the client forget its boss bars.
		if (!bar.event.getPlayers().contains(player) || bar.level != player.level()) {
			bar.event.removeAllPlayers();
			bar.event.addPlayer(player);
			bar.level = player.level();
		}
	}

	@Override
	public void hideBar(UUID id) {
		Bar bar = bars.remove(id);
		if (bar != null)
			bar.event.removeAllPlayers();
	}

	public static Component component(Text text) {
		MutableComponent out = Component.empty();
		for (Text.Segment segment : text.segments()) {
			Style style = Style.EMPTY;
			if (segment.rgb() >= 0)
				style = style.withColor(segment.rgb());
			if (segment.bold())
				style = style.withBold(true);
			if (segment.italic())
				style = style.withItalic(true);
			if (segment.underlined())
				style = style.withUnderlined(true);
			if (segment.strikethrough())
				style = style.withStrikethrough(true);
			if (segment.obfuscated())
				style = style.withObfuscated(true);
			out.append(Component.literal(segment.text()).withStyle(style));
		}
		return out;
	}

	public static Source source(CommandSourceStack stack) {
		return new Source() {
			@Override
			public boolean isOp() {
				return Compat.isOp(stack);
			}

			@Override
			public UUID uuid() {
				ServerPlayer player = stack.getPlayer();
				return player == null ? null : player.getUUID();
			}

			@Override
			public void reply(Text message) {
				stack.sendSuccess(() -> component(message), false);
			}

			@Override
			public void fail(Text message) {
				stack.sendFailure(component(message));
			}
		};
	}
}
