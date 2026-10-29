package com.sagmcu.NoMorePlaytime.timer;

import java.util.BitSet;
import java.util.UUID;

/**
 * One player's timer. The time left is stored as a base value plus the monotonic clock reading at which it started
 * running down, so it is exact however much the server lags and never needs a per-tick update.
 */
final class PlayerState {
	final UUID uuid;
	String name;

	boolean paused;
	boolean barHidden;
	boolean online;
	/** Set once the player was kicked this session, so that it happens only once. */
	boolean kicked;
	/** Written to the database at the next save. */
	boolean dirty;
	/** The warning thresholds (indexes into the configured list) that already fired or no longer apply. */
	final BitSet warned = new BitSet();

	private long baseMs;
	private long sinceNanos;
	private boolean counting;

	PlayerState(UUID uuid, String name, long remainingMs, boolean paused, boolean barHidden) {
		this.uuid = uuid;
		this.name = name;
		this.baseMs = remainingMs;
		this.paused = paused;
		this.barHidden = barHidden;
	}

	/** Whether the time is running down: the player is online and the timer is not paused. */
	boolean isCounting() {
		return counting;
	}

	long remainingMs(long nowNanos) {
		return counting ? Math.max(0, baseMs - (nowNanos - sinceNanos) / 1_000_000L) : baseMs;
	}

	void setRemaining(long ms, long nowNanos) {
		baseMs = ms;
		sinceNanos = nowNanos;
		dirty = true;
	}

	/** Starts or stops the countdown after {@link #online} or {@link #paused} changed, keeping the time exact. */
	void refreshCounting(long nowNanos) {
		boolean should = online && !paused;
		if (should == counting)
			return;
		baseMs = remainingMs(nowNanos);
		sinceNanos = nowNanos;
		counting = should;
		dirty = true;
	}
}
