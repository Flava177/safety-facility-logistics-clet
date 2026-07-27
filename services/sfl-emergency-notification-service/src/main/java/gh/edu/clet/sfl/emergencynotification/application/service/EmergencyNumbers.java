package gh.edu.clet.sfl.emergencynotification.application.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** Stable, unique human-readable identifiers for emergency operational records. */
final class EmergencyNumbers {

    private static final AtomicLong SEQ = new AtomicLong();

    private EmergencyNumbers() {
    }

    static String next(String prefix) {
        return prefix + "-" + Instant.now().toEpochMilli() + "-" + SEQ.incrementAndGet();
    }
}
