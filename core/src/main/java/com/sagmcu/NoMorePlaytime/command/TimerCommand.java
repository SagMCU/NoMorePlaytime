package com.sagmcu.NoMorePlaytime.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sagmcu.NoMorePlaytime.Main;
import com.sagmcu.NoMorePlaytime.config.Config;
import com.sagmcu.NoMorePlaytime.permission.Permissions;
import com.sagmcu.NoMorePlaytime.platform.Source;
import com.sagmcu.NoMorePlaytime.timer.PlaytimeManager;

import java.util.UUID;
import java.util.function.Function;

/**
 * /timer on|off [player]: shows or hides the boss bar only, the timer itself keeps running. Everybody can do it for
 * themselves by default, and operators (or the holders of the permission) for any player that ever joined.
 *
 * @param <S> the game's command source type
 */
public final class TimerCommand<S> {
	private final Function<S, Source> wrap;

	public TimerCommand(Function<S, Source> wrap) {
		this.wrap = wrap;
	}

	public LiteralArgumentBuilder<S> build() {
		return LiteralArgumentBuilder.<S>literal("timer")
				.requires(source -> Main.manager() != null)
				.then(toggle("on", false))
				.then(toggle("off", true));
	}

	private LiteralArgumentBuilder<S> toggle(String name, boolean hide) {
		return LiteralArgumentBuilder.<S>literal(name)
				.requires(source -> Permissions.has(wrap.apply(source), Permissions.TOGGLE_TIMER, true))
				.executes(ctx -> self(ctx, hide))
				.then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
						.requires(source -> Permissions.has(wrap.apply(source), Permissions.TOGGLE_TIMER_OTHERS, false))
						.suggests((ctx, builder) -> Completions.names(builder, Main.manager().names(), false))
						.executes(ctx -> other(ctx, hide)));
	}

	private int self(CommandContext<S> ctx, boolean hide) {
		Source source = wrap.apply(ctx.getSource());
		PlaytimeManager manager = Main.manager();
		Config config = manager.config();

		UUID uuid = source.uuid();
		if (uuid == null) {
			source.fail(config.chat(config.messages.playerRequired, "state", hide ? "off" : "on"));
			return 0;
		}
		manager.setBarHidden(uuid, hide);
		source.reply(config.chat(hide ? config.messages.barHidden : config.messages.barShown));
		return 1;
	}

	private int other(CommandContext<S> ctx, boolean hide) {
		Source source = wrap.apply(ctx.getSource());
		PlaytimeManager manager = Main.manager();
		Config config = manager.config();
		String player = StringArgumentType.getString(ctx, "player");

		PlaytimeManager.Snapshot snapshot = manager.setBarHidden(player, hide);
		if (snapshot == null) {
			source.fail(config.chat(config.messages.unknownPlayer, "player", player));
			return 0;
		}
		source.reply(config.chat(hide ? config.messages.barHiddenOther : config.messages.barShownOther, "player", snapshot.name()));
		return 1;
	}
}
