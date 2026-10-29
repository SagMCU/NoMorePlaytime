package com.sagmcu.NoMorePlaytime.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sagmcu.NoMorePlaytime.Main;
import com.sagmcu.NoMorePlaytime.config.Config;
import com.sagmcu.NoMorePlaytime.permission.Permissions;
import com.sagmcu.NoMorePlaytime.platform.Source;
import com.sagmcu.NoMorePlaytime.time.TimeFormat;
import com.sagmcu.NoMorePlaytime.timer.PlaytimeManager;
import com.sagmcu.NoMorePlaytime.timer.PlaytimeManager.Snapshot;
import com.sagmcu.NoMorePlaytime.timer.PlaytimeManager.TimeEdit;

import java.util.function.Function;

/**
 * /nmp (also /nomoreplaytime), each subcommand with its own permission and operators by default:
 * help, set|add|remove &lt;player|global&gt; &lt;time&gt;, max &lt;time&gt; [--add], pause|resume|reset &lt;player|global&gt;, check &lt;player&gt;, reload.
 *
 * @param <S> the game's command source type
 */
public final class NmpCommand<S> {
	private enum Action {PAUSE, RESUME, RESET}

	private final Function<S, Source> wrap;

	public NmpCommand(Function<S, Source> wrap) {
		this.wrap = wrap;
	}

	public LiteralArgumentBuilder<S> build(String name) {
		return LiteralArgumentBuilder.<S>literal(name)
				.requires(source -> Main.manager() != null && Permissions.hasAnyNmpCommand(wrap.apply(source)))
				.then(literal("help", Permissions.HELP).executes(this::help))
				.then(literal("set", Permissions.SET).then(player("player", true).then(duration(false).executes(ctx -> edit(ctx, TimeEdit.SET)))))
				.then(literal("add", Permissions.ADD).then(player("player", true).then(duration(false).executes(ctx -> edit(ctx, TimeEdit.ADD)))))
				.then(literal("remove", Permissions.REMOVE).then(player("player", true).then(duration(false).executes(ctx -> edit(ctx, TimeEdit.REMOVE)))))
				.then(literal("max", Permissions.MAX).then(duration(true).executes(this::max)))
				.then(literal("pause", Permissions.PAUSE).then(player("target", true).executes(ctx -> act(ctx, Action.PAUSE))))
				.then(literal("resume", Permissions.RESUME).then(player("target", true).executes(ctx -> act(ctx, Action.RESUME))))
				.then(literal("reset", Permissions.RESET).then(player("target", true).executes(ctx -> act(ctx, Action.RESET))))
				.then(literal("check", Permissions.CHECK).then(player("player", false).executes(this::check)))
				.then(literal("reload", Permissions.RELOAD).executes(this::reload));
	}

	/** A subcommand that only sources holding the permission can see and use (operators by default). */
	private LiteralArgumentBuilder<S> literal(String name, String permission) {
		return LiteralArgumentBuilder.<S>literal(name)
				.requires(source -> Permissions.has(wrap.apply(source), permission, false));
	}

	private RequiredArgumentBuilder<S, String> player(String argument, boolean includeGlobal) {
		return RequiredArgumentBuilder.<S, String>argument(argument, StringArgumentType.word())
				.suggests((ctx, builder) -> Completions.names(builder, Main.manager().names(), includeGlobal));
	}

	private RequiredArgumentBuilder<S, String> duration(boolean allowFlag) {
		return RequiredArgumentBuilder.<S, String>argument("time", StringArgumentType.greedyString())
				.suggests((ctx, builder) -> Completions.duration(builder, allowFlag));
	}

	private int help(CommandContext<S> ctx) {
		Source source = wrap.apply(ctx.getSource());
		Config config = Main.manager().config();
		source.reply(config.chat(config.messages.helpHeader));
		for (String line : config.messages.help)
			source.reply(config.chat(line));
		return 1;
	}

	private int edit(CommandContext<S> ctx, TimeEdit edit) {
		Source source = wrap.apply(ctx.getSource());
		PlaytimeManager manager = Main.manager();
		Config config = manager.config();
		String player = StringArgumentType.getString(ctx, "player");
		String input = StringArgumentType.getString(ctx, "time");

		long ms;
		try {
			ms = TimeFormat.parse(input);
		} catch (IllegalArgumentException e) {
			source.fail(config.chat(config.messages.invalidTime, "input", input));
			return 0;
		}

		if (player.equalsIgnoreCase("global")) {
			int count = manager.editAll(edit, ms);
			String template = switch (edit) {
				case SET -> config.messages.setGlobal;
				case ADD -> config.messages.addGlobal;
				case REMOVE -> config.messages.removeGlobal;
			};
			source.reply(config.chat(template, "count", String.valueOf(count), "amount", TimeFormat.format(ms)));
			return 1;
		}

		Snapshot snapshot = manager.edit(player, edit, ms);
		if (snapshot == null)
			return unknown(source, config, player);

		String template = switch (edit) {
			case SET -> config.messages.set;
			case ADD -> config.messages.add;
			case REMOVE -> config.messages.remove;
		};
		source.reply(config.chat(template, "player", snapshot.name(), "amount", TimeFormat.format(ms),
				"time", TimeFormat.format(snapshot.remainingMs())));
		return 1;
	}

	private int max(CommandContext<S> ctx) {
		Source source = wrap.apply(ctx.getSource());
		PlaytimeManager manager = Main.manager();
		Config config = manager.config();
		String input = StringArgumentType.getString(ctx, "time");

		boolean add = false;
		StringBuilder time = new StringBuilder();
		for (String word : input.trim().split("\\s+")) {
			if (word.equalsIgnoreCase("--add"))
				add = true;
			else
				time.append(word).append(' ');
		}

		long ms;
		try {
			ms = TimeFormat.parse(time.toString());
		} catch (IllegalArgumentException e) {
			source.fail(config.chat(config.messages.invalidTime, "input", input));
			return 0;
		}
		if (ms < 1000) {
			source.fail(config.chat(config.messages.maxTooLow));
			return 0;
		}

		manager.setMax(ms, add);
		source.reply(config.chat(config.messages.max, "time", TimeFormat.format(ms)));
		return 1;
	}

	private int act(CommandContext<S> ctx, Action action) {
		Source source = wrap.apply(ctx.getSource());
		PlaytimeManager manager = Main.manager();
		Config config = manager.config();
		String target = StringArgumentType.getString(ctx, "target");

		if (target.equalsIgnoreCase("global")) {
			int count = switch (action) {
				case PAUSE -> manager.pauseAll();
				case RESUME -> manager.resumeAll();
				case RESET -> manager.resetAll();
			};
			String template = switch (action) {
				case PAUSE -> config.messages.pauseGlobal;
				case RESUME -> config.messages.resumeGlobal;
				case RESET -> config.messages.resetGlobal;
			};
			source.reply(config.chat(template, "count", String.valueOf(count), "time", TimeFormat.format(manager.maxMs())));
			return 1;
		}

		Snapshot snapshot = switch (action) {
			case PAUSE -> manager.pause(target);
			case RESUME -> manager.resume(target);
			case RESET -> manager.reset(target);
		};
		if (snapshot == null)
			return unknown(source, config, target);

		String template = switch (action) {
			case PAUSE -> config.messages.pause;
			case RESUME -> config.messages.resume;
			case RESET -> config.messages.reset;
		};
		source.reply(config.chat(template, "player", snapshot.name(), "time", TimeFormat.format(snapshot.remainingMs())));
		return 1;
	}

	private int check(CommandContext<S> ctx) {
		Source source = wrap.apply(ctx.getSource());
		PlaytimeManager manager = Main.manager();
		Config config = manager.config();
		String player = StringArgumentType.getString(ctx, "player");

		Snapshot snapshot = manager.check(player);
		if (snapshot == null)
			return unknown(source, config, player);
		source.reply(config.chat(snapshot.paused() ? config.messages.checkPaused : config.messages.check,
				"player", snapshot.name(), "time", TimeFormat.format(snapshot.remainingMs()),
				"percent", String.valueOf(snapshot.percent())));
		return 1;
	}

	private int reload(CommandContext<S> ctx) {
		PlaytimeManager manager = Main.manager();
		manager.reload();
		wrap.apply(ctx.getSource()).reply(manager.config().chat(manager.config().messages.reload));
		return 1;
	}

	private static int unknown(Source source, Config config, String player) {
		source.fail(config.chat(config.messages.unknownPlayer, "player", player));
		return 0;
	}
}
