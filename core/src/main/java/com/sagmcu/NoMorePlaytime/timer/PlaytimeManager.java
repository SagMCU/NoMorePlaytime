package com.sagmcu.NoMorePlaytime.timer;

import com.sagmcu.NoMorePlaytime.config.Config;
import com.sagmcu.NoMorePlaytime.data.Database;
import com.sagmcu.NoMorePlaytime.permission.Permissions;
import com.sagmcu.NoMorePlaytime.platform.Platform;
import com.sagmcu.NoMorePlaytime.time.TimeFormat;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongBinaryOperator;

/**
 * Owns every player's timer. Everything here runs on the server thread; only the database writes leave it, and they
 * receive immutable snapshots. Players are known from their first join on, online or not.
 */
public final class PlaytimeManager {
	/** A player's timer at one moment, as shown in command feedback. */
	public record Snapshot(String name, long remainingMs, boolean paused, int percent) {
	}

	/** How /nmp set, add and remove change a player's time left. */
	public enum TimeEdit {
		SET((remaining, ms) -> ms),
		ADD((remaining, ms) -> remaining + ms),
		REMOVE((remaining, ms) -> Math.max(0, remaining - ms));

		private final LongBinaryOperator operation;

		TimeEdit(LongBinaryOperator operation) {
			this.operation = operation;
		}
	}

	private static final String META_MAX = "max_ms";
	private static final String META_GLOBAL_PAUSED = "global_paused";
	private static final String META_LAST_RESET = "last_reset";
	private static final long SECOND_NANOS = 1_000_000_000L;

	private final Platform platform;
	private final Database database;
	private final Path configFile;
	private Config config;

	private final Map<UUID, PlayerState> states = new HashMap<>();
	private final Map<String, PlayerState> byName = new HashMap<>();
	private final Set<PlayerState> online = new LinkedHashSet<>();

	private long maxMs;
	private boolean globalPaused;
	private LocalDate lastResetDate;
	private long lastSecondNanos;
	private long lastSaveNanos;

	public PlaytimeManager(Platform platform, Config config, Database database, Path configFile) throws SQLException {
		this.platform = platform;
		this.database = database;
		this.configFile = configFile;
		this.config = config;

		Map<String, String> meta = database.loadMeta();
		maxMs = meta.containsKey(META_MAX) ? Long.parseLong(meta.get(META_MAX)) : config.maxMs;
		globalPaused = Boolean.parseBoolean(meta.get(META_GLOBAL_PAUSED));
		if (meta.containsKey(META_LAST_RESET))
			lastResetDate = LocalDate.parse(meta.get(META_LAST_RESET));

		for (Database.Row row : database.loadPlayers()) {
			PlayerState state = new PlayerState(row.uuid(), row.name(), row.remainingMs(), row.paused(), row.barHidden());
			states.put(state.uuid, state);
			byName.put(key(state.name), state);
		}

		// A maximum edited in the config while the server was off is applied like /nmp max without --add.
		applyMax(config.maxMs, false);
		lastSecondNanos = System.nanoTime();
		flush(lastSecondNanos);
	}

	public Config config() {
		return config;
	}

	public long maxMs() {
		return maxMs;
	}

	/** Every player that ever joined. */
	public List<String> names() {
		List<String> names = new ArrayList<>(states.size());
		for (PlayerState state : states.values())
			names.add(state.name);
		return names;
	}

	// ---------------------------------------------------------------- game events

	public void onJoin(UUID uuid, String name) {
		long now = System.nanoTime();
		PlayerState state = states.get(uuid);
		if (state == null) {
			state = new PlayerState(uuid, name, maxMs, globalPaused, false);
			states.put(uuid, state);
		}
		if (!name.equals(state.name))
			byName.remove(key(state.name), state);
		state.name = name;
		byName.put(key(name), state);

		state.dirty = true;
		state.online = true;
		state.kicked = false;
		online.add(state);

		if (state.remainingMs(now) == 0 && !exempt(uuid)) {
			state.kicked = true;
			platform.kick(uuid, config.plain(config.messages.joinDenied, "player", name));
			return;
		}
		state.refreshCounting(now);
		rearm(state, now);
		update(state, now);
	}

	public void onLeave(UUID uuid) {
		PlayerState state = states.get(uuid);
		if (state == null || !online.remove(state))
			return;
		long now = System.nanoTime();
		state.online = false;
		state.refreshCounting(now);
		platform.hideBar(uuid);
		state.dirty = false;
		database.write(List.of(row(state, now)), Map.of());
	}

	/** Called every server tick; the timers only need attention once per real second. */
	public void tick() {
		long now = System.nanoTime();
		if (now - lastSecondNanos < SECOND_NANOS)
			return;
		lastSecondNanos = now;

		for (PlayerState state : online.toArray(new PlayerState[0]))
			update(state, now);
		checkDailyReset(now);
		if (now - lastSaveNanos >= config.saveIntervalMinutes * 60 * SECOND_NANOS)
			flush(now);
	}

	/** Saves everything and closes the database. */
	public void stop() {
		flush(System.nanoTime());
		database.close();
	}

	public void reload() {
		long now = System.nanoTime();
		config = Config.load(configFile);
		applyMax(config.maxMs, false);
		for (PlayerState state : online.toArray(new PlayerState[0])) {
			rearm(state, now);
			update(state, now);
		}
		flush(now);
	}

	// ---------------------------------------------------------------- admin operations (null: unknown player)

	public Snapshot edit(String name, TimeEdit edit, long ms) {
		PlayerState state = byName.get(key(name));
		if (state == null)
			return null;
		long now = System.nanoTime();
		applyEdit(state, edit, ms, now);
		flush(now);
		return snapshot(state, now);
	}

	/** Edits everyone's time, offline players included. */
	public int editAll(TimeEdit edit, long ms) {
		long now = System.nanoTime();
		for (PlayerState state : states.values())
			applyEdit(state, edit, ms, now);
		flush(now);
		return states.size();
	}

	public Snapshot reset(String name) {
		PlayerState state = byName.get(key(name));
		if (state == null)
			return null;
		long now = System.nanoTime();
		restore(state, true, now);
		flush(now);
		return snapshot(state, now);
	}

	public Snapshot pause(String name) {
		return setPaused(name, true);
	}

	public Snapshot resume(String name) {
		return setPaused(name, false);
	}

	public Snapshot check(String name) {
		PlayerState state = byName.get(key(name));
		return state == null ? null : snapshot(state, System.nanoTime());
	}

	/** Pauses everyone, offline players included; players joining for the first time start paused as well. */
	public int pauseAll() {
		long now = System.nanoTime();
		globalPaused = true;
		for (PlayerState state : states.values())
			applyPaused(state, true, now);
		flush(now);
		return states.size();
	}

	public int resumeAll() {
		long now = System.nanoTime();
		globalPaused = false;
		for (PlayerState state : states.values())
			applyPaused(state, false, now);
		flush(now);
		return states.size();
	}

	/** Gives everyone the maximum time back and resumes every timer. */
	public int resetAll() {
		long now = System.nanoTime();
		globalPaused = false;
		restoreAll(true, now);
		flush(now);
		return states.size();
	}

	/**
	 * Changes the maximum playtime and writes it to the config. Lowering it keeps each player's percentage of time left
	 * ({@code add} makes no difference). Raising it with {@code add} gives every player the extra time; raising it without
	 * leaves everyone's time as it is, except for the players who still have all of it.
	 */
	public void setMax(long ms, boolean add) {
		applyMax(ms, add);
		config.maxMs = ms;
		config.maxTime = TimeFormat.formatCompact(ms);
		Config.writeMax(configFile, ms);
		flush(System.nanoTime());
	}

	public Snapshot setBarHidden(String name, boolean hidden) {
		PlayerState state = byName.get(key(name));
		if (state == null)
			return null;
		applyBarHidden(state, hidden);
		flush(System.nanoTime());
		return snapshot(state, System.nanoTime());
	}

	/** For a player that is online right now. */
	public void setBarHidden(UUID uuid, boolean hidden) {
		applyBarHidden(states.get(uuid), hidden);
		flush(System.nanoTime());
	}

	// ---------------------------------------------------------------- internals

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	/** The time never goes below zero; a player who reaches zero is kicked right away. */
	private void applyEdit(PlayerState state, TimeEdit edit, long ms, long now) {
		state.setRemaining(edit.operation.applyAsLong(state.remainingMs(now), ms), now);
		rearm(state, now);
		if (state.online)
			update(state, now);
	}

	private Snapshot setPaused(String name, boolean paused) {
		PlayerState state = byName.get(key(name));
		if (state == null)
			return null;
		long now = System.nanoTime();
		applyPaused(state, paused, now);
		flush(now);
		return snapshot(state, now);
	}

	private void applyPaused(PlayerState state, boolean paused, long now) {
		state.paused = paused;
		state.dirty = true;
		state.refreshCounting(now);
		if (state.online)
			update(state, now);
	}

	private void applyBarHidden(PlayerState state, boolean hidden) {
		state.barHidden = hidden;
		state.dirty = true;
		if (!state.online)
			return;
		if (hidden)
			platform.hideBar(state.uuid);
		else
			update(state, System.nanoTime());
	}

	private void restore(PlayerState state, boolean resume, long now) {
		state.setRemaining(maxMs, now);
		if (resume)
			state.paused = false;
		state.refreshCounting(now);
		rearm(state, now);
		if (state.online)
			update(state, now);
	}

	private void restoreAll(boolean resume, long now) {
		for (PlayerState state : states.values())
			restore(state, resume, now);
	}

	private void applyMax(long newMax, boolean add) {
		long old = maxMs;
		if (newMax == old)
			return;
		long now = System.nanoTime();
		for (PlayerState state : states.values()) {
			long remaining = state.remainingMs(now);
			long updated;
			if (newMax < old)
				updated = Math.round((double) remaining * newMax / old);
			else if (add)
				updated = remaining + (newMax - old);
			else
				updated = remaining == old ? newMax : remaining;
			if (updated != remaining)
				state.setRemaining(updated, now);
		}
		maxMs = newMax;
		for (PlayerState state : online.toArray(new PlayerState[0])) {
			rearm(state, now);
			update(state, now);
		}
	}

	/** Operators, and whoever holds the bypass permission, are never kicked or refused. */
	private boolean exempt(UUID uuid) {
		return Permissions.bypassesLimit(uuid, platform.isOp(uuid));
	}

	private Snapshot snapshot(PlayerState state, long now) {
		long remaining = state.remainingMs(now);
		return new Snapshot(state.name, remaining, state.paused, (int) Math.round(remaining * 100.0 / maxMs));
	}

	/** Kicks at zero, sends the warnings and refreshes the boss bar of one online player. */
	private void update(PlayerState state, long now) {
		if (state.kicked)
			return;
		long remaining = state.remainingMs(now);
		if (remaining == 0 && !exempt(state.uuid)) {
			state.kicked = true;
			platform.kick(state.uuid, config.plain(config.messages.kick, "player", state.name));
			return;
		}
		if (config.warnings.enabled && state.isCounting())
			warn(state, (remaining + 999) / 1000);
		if (state.barHidden)
			return;
		String title = state.paused ? config.bossbar.pausedTitle : config.bossbar.title;
		platform.showBar(state.uuid, config.plain(title, "player", state.name, "time", TimeFormat.format(remaining)),
				(float) Math.min(1.0, remaining / (double) maxMs), config.barColor);
	}

	private void warn(PlayerState state, long seconds) {
		long[] thresholds = config.warnSeconds;
		int reached = -1;
		for (int i = 0; i < thresholds.length; i++) {
			if (seconds <= thresholds[i] && !state.warned.get(i)) {
				state.warned.set(i);
				reached = i;
			}
		}
		if (reached < 0)
			return;
		platform.send(state.uuid, config.chat(config.messages.warning, "player", state.name, "time",
				TimeFormat.format(thresholds[reached] * 1000)));
		Config.Sound sound = config.warnings.sound;
		platform.playSound(state.uuid, sound.id, sound.volume, sound.pitch);
	}

	/** Marks every threshold that is already behind the player as done, so only the ones still ahead can fire. */
	private void rearm(PlayerState state, long now) {
		long seconds = (state.remainingMs(now) + 999) / 1000;
		state.warned.clear();
		for (int i = 0; i < config.warnSeconds.length; i++) {
			if (seconds <= config.warnSeconds[i])
				state.warned.set(i);
		}
	}

	private void checkDailyReset(long now) {
		ZonedDateTime clock = ZonedDateTime.now(config.resetZone);
		LocalDate today = clock.toLocalDate();
		// While the reset is off the date keeps following the clock, so that turning it on never resets at once.
		if (!config.dailyReset.enabled || lastResetDate == null) {
			lastResetDate = today;
			return;
		}
		if (today.isAfter(lastResetDate) && !clock.toLocalTime().isBefore(config.resetTime)) {
			lastResetDate = today;
			restoreAll(false, now);
			flush(now);
		}
	}

	private Database.Row row(PlayerState state, long now) {
		return new Database.Row(state.uuid, state.name, state.remainingMs(now), state.paused, state.barHidden);
	}

	/** Queues every changed timer, and every running one, for the background writer. */
	private void flush(long now) {
		List<Database.Row> rows = new ArrayList<>();
		for (PlayerState state : states.values()) {
			if (state.dirty || state.isCounting()) {
				rows.add(row(state, now));
				state.dirty = false;
			}
		}
		Map<String, String> meta = new HashMap<>();
		meta.put(META_MAX, Long.toString(maxMs));
		meta.put(META_GLOBAL_PAUSED, Boolean.toString(globalPaused));
		if (lastResetDate != null)
			meta.put(META_LAST_RESET, lastResetDate.toString());
		database.write(rows, meta);
		lastSaveNanos = now;
	}
}
