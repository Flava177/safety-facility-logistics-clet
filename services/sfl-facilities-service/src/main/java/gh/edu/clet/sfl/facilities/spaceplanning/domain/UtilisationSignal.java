package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A planning signal: a space whose use and plan disagree - SRS-SFL-S158-03.
 *
 * <p>At most one active signal per space per {@link Kind}; the database holds that with a partial unique
 * index. A signal is raised by the reconciliation run that first sees the condition, stays active while
 * each later run still sees it, and is cleared - not deleted - by the first run that does not.
 */
public record UtilisationSignal(
        UUID id,
        String siteCode,
        UUID roomId,
        String roomCode,
        Kind kind,
        Status status,
        Instant raisedAt,
        Instant raisedForPeriodStart,
        Instant raisedForPeriodEnd,
        Instant latestPeriodEnd,
        BigDecimal latestUtilisationRate,
        BigDecimal thresholdRate,
        String detail,
        Instant clearedAt,
        RecordMetadata metadata) {

    public enum Kind {
        /**
         * The AC: "booked at under 20% of capacity over a full reporting period". One complete period is
         * enough.
         */
        UNDER_UTILISED,
        /**
         * "A persistent gap between planned allocation and actual utilisation": the S152 register plans
         * the space at a markedly higher occupancy than S159 observes, for several consecutive periods.
         */
        PLANNED_ACTUAL_GAP
    }

    public enum Status {
        ACTIVE,
        CLEARED
    }

    public UtilisationSignal {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(roomId, "roomId is required");
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
        detail = AllocationScenario.optional(detail, "detail", 2000);
    }

    public static UtilisationSignal raise(UUID id, UtilisationSnapshot snapshot, Kind kind, BigDecimal threshold,
            String detail, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new UtilisationSignal(id, snapshot.siteCode(), snapshot.roomId(), snapshot.roomCode(), kind,
                Status.ACTIVE, at, snapshot.periodStart(), snapshot.periodEnd(), snapshot.periodEnd(),
                snapshot.utilisationRate(), threshold, detail, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** The condition still holds; carry the latest figure. */
    public UtilisationSignal stillActive(UtilisationSnapshot snapshot, String detail, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new UtilisationSignal(id, siteCode, roomId, roomCode, kind, status, raisedAt, raisedForPeriodStart,
                raisedForPeriodEnd, snapshot.periodEnd(), snapshot.utilisationRate(), thresholdRate,
                detail == null ? this.detail : detail, null, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public UtilisationSignal clear(UtilisationSnapshot snapshot, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new UtilisationSignal(id, siteCode, roomId, roomCode, kind, Status.CLEARED, raisedAt,
                raisedForPeriodStart, raisedForPeriodEnd, snapshot.periodEnd(), snapshot.utilisationRate(),
                thresholdRate, detail, at, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
