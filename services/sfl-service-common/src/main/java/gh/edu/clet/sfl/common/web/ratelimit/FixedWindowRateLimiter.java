package gh.edu.clet.sfl.common.web.ratelimit;

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
 */
public final class FixedWindowRateLimiter {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

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
        return window.count().incrementAndGet() <= limit;
    }

    private record Window(long startedAt, AtomicLong count) {
    }
}
