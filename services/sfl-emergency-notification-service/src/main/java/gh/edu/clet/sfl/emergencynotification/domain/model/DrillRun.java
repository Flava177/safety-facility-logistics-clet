package gh.edu.clet.sfl.emergencynotification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A notification drill — a rehearsal that exercises the activation path and records performance metrics
 * without a real broadcast. Completion computes acknowledgement rate and elapsed time.
 */
public record DrillRun(UUID id, String drillNumber, SiteCode siteCode, UUID scenarioId, Status status,
        int targetRecipients, int reachedRecipients, int acknowledgedRecipients, Long activationMillis,
        Instant startedAt, Instant completedAt, String notes, RecordMetadata metadata) {

    public enum Status { RUNNING, COMPLETED, CANCELLED }

    public DrillRun {
        Objects.requireNonNull(id);
        Objects.requireNonNull(siteCode);
        Objects.requireNonNull(status);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(metadata);
        drillNumber = require(drillNumber, "drillNumber");
        if (targetRecipients < 0 || reachedRecipients < 0 || acknowledgedRecipients < 0) {
            throw new IllegalArgumentException("drill counts cannot be negative");
        }
    }

    public DrillRun complete(int reached, int acknowledged, long activationMillis, Instant at, String notes,
            RecordMetadata changed) {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Only a running drill can be completed");
        }
        return new DrillRun(id, drillNumber, siteCode, scenarioId, Status.COMPLETED, targetRecipients, reached,
                acknowledged, activationMillis, startedAt, Objects.requireNonNull(at, "completedAt"), notes, changed);
    }

    /** Acknowledgement rate as a percentage 0..100 (0 when no recipients were targeted). */
    public int acknowledgementRatePercent() {
        return targetRecipients == 0 ? 0 : (int) Math.round(100.0 * acknowledgedRecipients / targetRecipients);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
