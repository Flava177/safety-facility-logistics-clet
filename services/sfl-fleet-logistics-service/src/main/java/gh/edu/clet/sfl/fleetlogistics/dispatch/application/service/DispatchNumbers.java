package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** Stable, unique human-readable identifiers for dispatch operational records. */
final class DispatchNumbers {
    private static final AtomicLong SEQ = new AtomicLong();
    private DispatchNumbers() {}

    static String next(String prefix) {
        return prefix + "-" + Instant.now().toEpochMilli() + "-" + SEQ.incrementAndGet();
    }
}
