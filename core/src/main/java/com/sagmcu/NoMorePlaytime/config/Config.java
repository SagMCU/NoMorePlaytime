package com.sagmcu.NoMorePlaytime.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.sagmcu.NoMorePlaytime.Main;
import com.sagmcu.NoMorePlaytime.data.DatabaseType;
import com.sagmcu.NoMorePlaytime.text.Text;
import com.sagmcu.NoMorePlaytime.time.TimeFormat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code config/nomoreplaytime/config.json}: settings, sounds and every message. Missing keys are added with their defaults on load. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Set<String> BAR_COLORS = Set.of("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE");
	private static final Pattern RESET_TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?:\\s+(\\S+))?");
	private static final Pattern UTC_OFFSET = Pattern.compile("UTC(?:([+-])(\\d{1,2})(?::?(\\d{2}))?)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern TABLE_PREFIX = Pattern.compile("\\w*");

	/** The maximum playtime, written like a command argument ("5h", "1h 30m"). */
	public String maxTime = "5h";
	public int saveIntervalMinutes = 5;
	public Storage database = new Storage();
	public DailyReset dailyReset = new DailyReset();
	public BossBar bossbar = new BossBar();
	public Warnings warnings = new Warnings();
	public Messages messages = new Messages();

	/** Where the times are kept. Changing it needs a restart and does not move the data that is already stored. */
	public static final class Storage {
		/** H2 (a file next to this config), MYSQL or MARIADB. */
		public String type = "H2";
		/** Used by MYSQL and MARIADB only: the database must already exist. */
		public String host = "localhost";
		public int port = 3306;
		public String name = "nomoreplaytime";
		public String user = "root";
		public String password = "";
		/** Extra JDBC options appended to the connection URL, for example "sslMode=REQUIRED". */
		public String options = "";
		/** Put in front of the table names, letters, digits and underscores only. */
		public String tablePrefix = "nmp_";
	}

	public static final class DailyReset {
		public boolean enabled = false;
		/** 24-hour time, then an optional time zone: "00:00 UTC", "16:30 UTC+5", "08:00 UTC-3", "20:15 Europe/Madrid". */
		public String time = "00:00 UTC";
	}

	public static final class BossBar {
		/** PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE. */
		public String color = "BLUE";
		public String title = "&7Time left: &b{time}";
		public String pausedTitle = "&7Time left: &e{time} &7(paused)";
	}

	public static final class Warnings {
		public boolean enabled = true;
		/** Remaining times that trigger a chat warning and the sound, written like command arguments. */
		public List<String> times = new ArrayList<>(List.of("10m", "5m", "1m", "30s", "10s"));
		public Sound sound = new Sound();
	}

	public static final class Sound {
		/** Any sound id, including the ones added by resource packs. */
		public String id = "minecraft:entity.pillager.ambient";
		public float volume = 1.0f;
		public float pitch = 1.0f;
	}

	/** The values below are read from the settings above by {@link #prepare()}. */
	public transient long maxMs;
	public transient DatabaseType databaseType;
	public transient LocalTime resetTime;
	public transient ZoneId resetZone;
	/** The warning thresholds in seconds, longest first. */
	public transient long[] warnSeconds;
	public transient String barColor;

	/** Reads the file (creating it when missing) and writes it back with every missing key filled in. */
	public static Config load(Path file) {
		Config config = null;
		if (Files.exists(file)) {
			try {
				config = GSON.fromJson(Files.readString(file), Config.class);
			} catch (IOException | JsonParseException e) {
				Path broken = file.resolveSibling("config.json.broken");
				Main.LOGGER.error("Could not read {}, it was moved to {} and the defaults are used", file, broken, e);
				try {
					Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException moveFailure) {
					Main.LOGGER.error("Could not move the broken config", moveFailure);
				}
			}
		}
		if (config == null)
			config = new Config();
		config.prepare();
		try {
			Files.writeString(file, GSON.toJson(config));
		} catch (IOException e) {
			Main.LOGGER.error("Could not write {}", file, e);
		}
		return config;
	}

	/** Changes only "maxTime" in the file, so edits made to the rest of it since the last reload are kept. */
	public static void writeMax(Path file, long ms) {
		try {
			JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
			json.addProperty("maxTime", TimeFormat.formatCompact(ms));
			Files.writeString(file, GSON.toJson(json));
		} catch (IOException | JsonParseException | NullPointerException e) {
			Main.LOGGER.error("Could not save the new maximum playtime to {}", file, e);
		}
	}

	/** A chat message: the prefix, then the template with each {key} replaced by the value that follows it in {@code values}. */
	public Text chat(String template, String... values) {
		return Text.parse(messages.prefix + fill(template, values));
	}

	/** Like {@link #chat} but without the prefix: for the kick screen and the boss bar. */
	public Text plain(String template, String... values) {
		return Text.parse(fill(template, values));
	}

	private static String fill(String template, String[] values) {
		String result = template;
		for (int i = 0; i + 1 < values.length; i += 2)
			result = result.replace("{" + values[i] + "}", values[i + 1]);
		return result;
	}

	private void prepare() {
		if (database == null)
			database = new Storage();
		if (dailyReset == null)
			dailyReset = new DailyReset();
		if (bossbar == null)
			bossbar = new BossBar();
		if (warnings == null)
			warnings = new Warnings();
		if (warnings.sound == null)
			warnings.sound = new Sound();
		if (warnings.times == null)
			warnings.times = new ArrayList<>();
		if (messages == null)
			messages = new Messages();
		fillNulls(database, new Storage());
		fillNulls(bossbar, new BossBar());
		fillNulls(warnings.sound, new Sound());
		fillNulls(messages, new Messages());

		maxMs = parseMax();
		if (saveIntervalMinutes < 1) {
			Main.LOGGER.warn("saveIntervalMinutes must be at least 1, using 5");
			saveIntervalMinutes = 5;
		}

		try {
			databaseType = DatabaseType.valueOf(database.type.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			Main.LOGGER.warn("Invalid database.type '{}' (use H2, MYSQL or MARIADB), using H2", database.type);
			database.type = "H2";
			databaseType = DatabaseType.H2;
		}
		if (!TABLE_PREFIX.matcher(database.tablePrefix).matches()) {
			Main.LOGGER.warn("Invalid database.tablePrefix '{}' (letters, digits and _ only), using nmp_", database.tablePrefix);
			database.tablePrefix = "nmp_";
		}

		parseResetTime();

		barColor = bossbar.color.toUpperCase(Locale.ROOT);
		if (!BAR_COLORS.contains(barColor)) {
			Main.LOGGER.warn("Invalid bossbar.color '{}', using BLUE", bossbar.color);
			bossbar.color = "BLUE";
			barColor = "BLUE";
		}

		TreeSet<Long> thresholds = new TreeSet<>(Comparator.reverseOrder());
		for (String entry : warnings.times) {
			try {
				long seconds = TimeFormat.parse(entry) / 1000;
				if (seconds < 1)
					throw new IllegalArgumentException("below one second");
				thresholds.add(seconds);
			} catch (RuntimeException e) {
				Main.LOGGER.warn("Ignoring invalid warnings.times entry '{}'", entry);
			}
		}
		warnSeconds = thresholds.stream().mapToLong(Long::longValue).toArray();
	}

	/** A text set to null (or removed) in the file falls back to its default. */
	private static void fillNulls(Object target, Object defaults) {
		try {
			for (Field field : target.getClass().getFields()) {
				if (field.get(target) == null)
					field.set(target, field.get(defaults));
			}
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private long parseMax() {
		try {
			long ms = TimeFormat.parse(maxTime);
			if (ms >= 1000)
				return ms;
		} catch (RuntimeException ignored) {
			// reported below
		}
		Main.LOGGER.warn("Invalid maxTime '{}', using 5h", maxTime);
		maxTime = "5h";
		return TimeFormat.parse(maxTime);
	}

	private void parseResetTime() {
		try {
			Matcher matcher = RESET_TIME.matcher(dailyReset.time.trim());
			if (!matcher.matches())
				throw new IllegalArgumentException("not HH:mm");
			resetTime = LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
			resetZone = matcher.group(3) == null ? ZoneOffset.UTC : parseZone(matcher.group(3));
		} catch (RuntimeException e) {
			Main.LOGGER.warn("Invalid dailyReset.time '{}' (use HH:mm and a zone such as UTC+5), using 00:00 UTC", dailyReset.time);
			dailyReset.time = "00:00 UTC";
			resetTime = LocalTime.MIDNIGHT;
			resetZone = ZoneOffset.UTC;
		}
	}

	/** "UTC", "UTC+5", "UTC-3", "UTC+5:30", or a region such as "Europe/Madrid". */
	private static ZoneId parseZone(String text) {
		Matcher matcher = UTC_OFFSET.matcher(text);
		if (!matcher.matches())
			return ZoneId.of(text);
		if (matcher.group(1) == null)
			return ZoneOffset.UTC;
		int sign = matcher.group(1).equals("-") ? -1 : 1;
		int minutes = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
		return ZoneOffset.ofHoursMinutes(sign * Integer.parseInt(matcher.group(2)), sign * minutes);
	}
}
