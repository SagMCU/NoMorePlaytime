package com.sagmcu.NoMorePlaytime.data;

import com.sagmcu.NoMorePlaytime.Main;
import com.sagmcu.NoMorePlaytime.config.Config;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Where every player's time is kept between restarts: an H2 file ({@code data.mv.db}), MySQL or MariaDB. Reads happen once
 * at start-up; all writes run on a single background thread so that saving never stalls the server tick.
 */
public final class Database {
	public record Row(UUID uuid, String name, long remainingMs, boolean paused, boolean barHidden) {
	}

	private final DatabaseType type;
	private final Config.Storage settings;
	private final Path h2File;
	private final String playersTable;
	private final String metaTable;
	private final String upsertPlayer;
	private final String upsertMeta;
	private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "NoMorePlaytime-Database");
		thread.setDaemon(true);
		return thread;
	});
	/** Used by the constructor and then only by the writer thread. */
	private Connection connection;

	/** @param h2File the H2 database path without extension */
	public Database(DatabaseType type, Config.Storage settings, Path h2File) throws SQLException {
		this.type = type;
		this.settings = settings;
		this.h2File = h2File;
		playersTable = settings.tablePrefix + "players";
		metaTable = settings.tablePrefix + "meta";

		String playerColumns = "(player_uuid, name, remaining_ms, paused, bar_hidden)";
		if (type == DatabaseType.H2) {
			upsertPlayer = "MERGE INTO " + playersTable + " " + playerColumns + " KEY (player_uuid) VALUES (?, ?, ?, ?, ?)";
			upsertMeta = "MERGE INTO " + metaTable + " (meta_key, meta_value) KEY (meta_key) VALUES (?, ?)";
		} else {
			upsertPlayer = "INSERT INTO " + playersTable + " " + playerColumns + " VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE "
					+ "name = VALUES(name), remaining_ms = VALUES(remaining_ms), paused = VALUES(paused), bar_hidden = VALUES(bar_hidden)";
			upsertMeta = "INSERT INTO " + metaTable + " (meta_key, meta_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)";
		}

		connection = open();
		try (Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE IF NOT EXISTS " + playersTable + " (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
					+ "name VARCHAR(64) NOT NULL, remaining_ms BIGINT NOT NULL, paused BOOLEAN NOT NULL, bar_hidden BOOLEAN NOT NULL)");
			statement.execute("CREATE TABLE IF NOT EXISTS " + metaTable
					+ " (meta_key VARCHAR(64) NOT NULL PRIMARY KEY, meta_value VARCHAR(256) NOT NULL)");
		} catch (SQLException e) {
			// Let go of the connection (and of the H2 file lock) so that a later start can try again.
			close();
			throw e;
		}
	}

	public List<Row> loadPlayers() throws SQLException {
		List<Row> rows = new ArrayList<>();
		try (Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery(
					 "SELECT player_uuid, name, remaining_ms, paused, bar_hidden FROM " + playersTable)) {
			while (result.next()) {
				rows.add(new Row(UUID.fromString(result.getString(1)), result.getString(2), result.getLong(3),
						result.getBoolean(4), result.getBoolean(5)));
			}
		}
		return rows;
	}

	public Map<String, String> loadMeta() throws SQLException {
		Map<String, String> meta = new HashMap<>();
		try (Statement statement = connection.createStatement();
			 ResultSet result = statement.executeQuery("SELECT meta_key, meta_value FROM " + metaTable)) {
			while (result.next())
				meta.put(result.getString(1), result.getString(2));
		}
		return meta;
	}

	/** Queues the rows (and the meta values) to be written; the caller must not touch them afterwards. */
	public void write(List<Row> rows, Map<String, String> meta) {
		writer.execute(() -> {
			try {
				writeNow(rows, meta);
			} catch (SQLException first) {
				// A remote database may have dropped the idle connection: reconnect once and try again.
				try {
					closeQuietly();
					connection = open();
					writeNow(rows, meta);
				} catch (SQLException second) {
					Main.LOGGER.error("Could not save the playtime data", second);
				}
			}
		});
	}

	/** Waits for the queued writes, then closes the connection. */
	public void close() {
		writer.shutdown();
		try {
			if (!writer.awaitTermination(30, TimeUnit.SECONDS))
				Main.LOGGER.error("Timed out while saving the playtime data");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		closeQuietly();
	}

	private void writeNow(List<Row> rows, Map<String, String> meta) throws SQLException {
		connection.setAutoCommit(false);
		try (PreparedStatement players = connection.prepareStatement(upsertPlayer);
			 PreparedStatement values = connection.prepareStatement(upsertMeta)) {
			for (Row row : rows) {
				players.setString(1, row.uuid().toString());
				players.setString(2, row.name());
				players.setLong(3, row.remainingMs());
				players.setBoolean(4, row.paused());
				players.setBoolean(5, row.barHidden());
				players.addBatch();
			}
			players.executeBatch();
			for (Map.Entry<String, String> entry : meta.entrySet()) {
				values.setString(1, entry.getKey());
				values.setString(2, entry.getValue());
				values.addBatch();
			}
			values.executeBatch();
			connection.commit();
		} catch (SQLException e) {
			try {
				connection.rollback();
			} catch (SQLException ignored) {
				// the connection is unusable, the caller reconnects
			}
			throw e;
		}
	}

	private Connection open() throws SQLException {
		// The drivers are used directly: DriverManager would not find them inside a mod class loader.
		if (type == DatabaseType.H2) {
			String url = "jdbc:h2:file:" + h2File.toAbsolutePath().toString().replace('\\', '/') + ";DB_CLOSE_ON_EXIT=FALSE";
			return new org.h2.Driver().connect(url, new Properties());
		}

		Properties properties = new Properties();
		properties.setProperty("user", settings.user);
		properties.setProperty("password", settings.password);
		properties.setProperty("connectTimeout", "10000");
		String options = settings.options.startsWith("?") ? settings.options.substring(1) : settings.options;
		String address = settings.host + ":" + settings.port + "/" + settings.name + (options.isEmpty() ? "" : "?" + options);
		if (type == DatabaseType.MYSQL)
			return new com.mysql.cj.jdbc.Driver().connect("jdbc:mysql://" + address, properties);
		return new org.mariadb.jdbc.Driver().connect("jdbc:mariadb://" + address, properties);
	}

	private void closeQuietly() {
		try {
			if (connection != null)
				connection.close();
		} catch (SQLException e) {
			Main.LOGGER.warn("Could not close the playtime database cleanly", e);
		}
	}
}
