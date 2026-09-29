package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * How one space was used over one complete reporting period, next to how it was planned - SRS-SFL-S158-03.
 *
 * <p>Pulled from S159 and stored here, never written back. One row per room per period; a re-run of the
 * same period replaces the figures (bookings for a closed week can still be completed or swept as a
 * no-show a few hours after it ends) rather than adding a second row.
 *
 * <p>The rates are defined by {@code UtilisationPolicy} and documented in the S158 runbook; read that
 * before reading a number off this record.
 *
 * @param bookable whether S152 offers the space for booking; S159 can only observe bookable spaces
 * @param availableMinutes the period's bookable hours - the denominator of {@link #frequencyRate}
 * @param utilisationRate {@code frequencyRate x occupancyRate}; {@code null} when not evaluable (no
 *        capacity recorded in S152, or a period with no available hours)
 * @param plannedHeadcount the S152 register's current total headcount for the space at the time of the run
 */
public record UtilisationSnapshot(
        UUID id,
        String siteCode,
        UUID roomId,
        String roomCode,
        Instant periodStart,
        Instant periodEnd,
        Integer capacity,
        boolean bookable,
        int bookingCount,
        int takenUpCount,
        int noShowCount,
        long bookedMinutes,
        long usedMinutes,
        long availableMinutes,
        BigDecimal frequencyRate,
        BigDecimal occupancyRate,
        BigDecimal utilisationRate,
        int plannedHeadcount,
        BigDecimal plannedOccupancyRate,
        Instant recordedAt,
        RecordMetadata metadata) {

    public UtilisationSnapshot {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(roomId, "roomId is required");
        Objects.requireNonNull(periodStart, "periodStart is required");
        Objects.requireNonNull(periodEnd, "periodEnd is required");
        Objects.requireNonNull(recordedAt, "recordedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (!periodEnd.isAfter(periodStart)) {
            throw new IllegalArgumentException("a reporting period must end after it starts");
        }
    }

    /** Whether S159 data can say anything about this space - bookable, or booked anyway. */
    public boolean observable() {
        return utilisationRate != null && (bookable || bookingCount > 0);
    }

    /** The same row with this run's figures - identity and creation provenance kept. */
    public UtilisationSnapshot refreshedFrom(UtilisationSnapshot fresh, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new UtilisationSnapshot(id, siteCode, roomId, roomCode, periodStart, periodEnd, fresh.capacity(),
                fresh.bookable(), fresh.bookingCount(), fresh.takenUpCount(), fresh.noShowCount(),
                fresh.bookedMinutes(), fresh.usedMinutes(), fresh.availableMinutes(), fresh.frequencyRate(),
                fresh.occupancyRate(), fresh.utilisationRate(), fresh.plannedHeadcount(),
                fresh.plannedOccupancyRate(), at, metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
