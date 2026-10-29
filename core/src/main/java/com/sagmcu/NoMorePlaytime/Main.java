package com.sagmcu.NoMorePlaytime;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.sagmcu.NoMorePlaytime.command.NmpCommand;
import com.sagmcu.NoMorePlaytime.command.TimerCommand;
import com.sagmcu.NoMorePlaytime.config.Config;
import com.sagmcu.NoMorePlaytime.data.Database;
import com.sagmcu.NoMorePlaytime.platform.Platform;
import com.sagmcu.NoMorePlaytime.platform.Source;
import com.sagmcu.NoMorePlaytime.timer.PlaytimeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The entry point every loader calls: start and stop with the server, forward the tick and the joins and leaves, and
 * register the commands. Everything else lives in its own package.
 */
public final class Main {
	public static final Logger LOGGER = LoggerFactory.getLogger("NoMorePlaytime");

	private static PlaytimeManager manager;

	private Main() {
	}

	/** The running manager, or null while the server is stopped or the start-up failed. */
	public static PlaytimeManager manager() {
		return manager;
	}

	/** @param directory {@code config/nomoreplaytime}: holds config.json and the H2 file */
	public static void start(Platform platform, Path directory) {
		stop();
		Database database = null;
		try {
			Files.createDirectories(directory);
			Path configFile = directory.resolve("config.json");
			Config config = Config.load(configFile);
			database = new Database(config.databaseType, config.database, directory.resolve("data"));
			manager = new PlaytimeManager(platform, config, database, configFile);
		} catch (Exception | LinkageError e) {
			// LinkageError: the driver of the chosen database is missing from the jar
			LOGGER.error("NoMorePlaytime could not start, no playtime limit is enforced", e);
			if (database != null)
				database.close();
		}
	}

	public static void stop() {
		if (manager == null)
			return;
		PlaytimeManager stopping = manager;
		manager = null;
		stopping.stop();
	}

	public static void tick() {
		if (manager != null)
			manager.tick();
	}

	public static void join(UUID uuid, String name) {
		if (manager != null)
			manager.onJoin(uuid, name);
	}

	public static void leave(UUID uuid) {
		if (manager != null)
			manager.onLeave(uuid);
	}

	/** /nomoreplaytime, /nmp and /timer, ready to be registered in the game's command dispatcher. */
	public static <S> List<LiteralArgumentBuilder<S>> commands(Function<S, Source> wrap) {
		NmpCommand<S> nmp = new NmpCommand<>(wrap);
		return List.of(nmp.build("nomoreplaytime"), nmp.build("nmp"), new TimerCommand<>(wrap).build());
	}
}
