package gh.edu.clet.sfl.common.web.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * A per-key, fixed-window request counter: at most {@code limit} calls to {@link #tryAcquire(String)}
 * succeed for a given key within any {@code windowMillis} span, after which the window rolls over and
 * the count resets.
 *
 * <p>In-memory and single-instance: each service replica keeps its own counters, so the effective
 * limit multiplies with the number of running replicas. That is an acceptable trade-off for the
 * platform's current single-instance-per-service deployment; if a service is ever scaled horizontally,
 * this should be swapped for a shared counter (the platform's Redis instance is already provisioned
 * for exactly that) rather than assumed to still hold.
 *
 * <p><strong>Eviction.</strong> Keyed on {@code remoteAddr + ":" + servletPath} (see the caller), so
 * sustained traffic from many distinct client addresses grows this map by one entry per address per
 * path - unbounded, for the life of the process, unless expired windows are removed. No new dependency
 * is added for this (the project has no established caching-library policy yet - see the platform-wide
 * "no application-level caching layer" finding this rate limiter's own class comment already flagged):
 * a sweep piggybacks on the calls this class already receives instead. Each {@link #tryAcquire} check,
 * once per {@code windowMillis} at most (a {@link #lastSweepAt} CAS elects exactly one caller to do it,
 * so concurrent callers in the same instant do not all pay for the sweep), removes every window whose
 * span has closed. A closed window is not read again by design - a key's next request always finds it
 * expired and starts a fresh one in {@link #tryAcquire} regardless - so sweeping it is never a race with
 * a legitimate read, only ever with a concurrent rollover to a new window for the same key, which
 * {@link Map#remove(Object, Object)}'s identity-compare handles: a sweep can only remove the exact
 * {@link Window} instance it observed as expired, never a fresher one a concurrent caller just installed.
 */
public final class FixedWindowRateLimiter {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepAt;

    public FixedWindowRateLimiter(int limit, long windowMillis) {
        this(limit, windowMillis, System::currentTimeMillis);
    }

    FixedWindowRateLimiter(int limit, long windowMillis, LongSupplier clock) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (windowMillis <= 0) {
            throw new IllegalArgumentException("windowMillis must be positive");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
        // A window behind "now", not Long.MIN_VALUE: `now - lastSweepAt` must never overflow, and
        // starting already due means the first call's sweep is a harmless no-op (nothing has expired
        // yet) rather than a special case to reason about.
        this.lastSweepAt = new AtomicLong(clock.getAsLong() - windowMillis);
    }

    /**
     * Records one call for {@code key} and reports whether it falls within the limit for the window
     * it lands in. A call past the limit still increments the counter, so it costs the caller nothing
     * to keep retrying inside the same window.
     */
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        Window window = windows.compute(key, (k, existing) -> (existing == null || now - existing.startedAt() >= windowMillis)
                ? new Window(now, new AtomicLong(0))
                : existing);
        sweepIfDue(now);
        return window.count().incrementAndGet() <= limit;
    }

    /** The number of distinct keys currently tracked, for tests and for a leak-diagnostics metric. */
    public int trackedKeyCount() {
        return windows.size();
    }

    private void sweepIfDue(long now) {
        long previous = lastSweepAt.get();
        if (now - previous < windowMillis) {
            return;
        }
        if (!lastSweepAt.compareAndSet(previous, now)) {
            return;
        }
        for (Map.Entry<String, Window> entry : windows.entrySet()) {
            Window window = entry.getValue();
            if (now - window.startedAt() >= windowMillis) {
                windows.remove(entry.getKey(), window);
            }
        }
    }

    private record Window(long startedAt, AtomicLong count) {
    }
}
