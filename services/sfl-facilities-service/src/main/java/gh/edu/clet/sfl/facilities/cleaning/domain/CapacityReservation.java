package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What S173 asked this system for, and what it was told - SRS-SFL-S169-04.
 *
 * <p>A {@code CONFLICT} is stored as well as a {@code RESERVED}. "The conflict and its cause are
 * returned rather than a bare refusal" is the acceptance criterion, and a conflict returned to one
 * coordinator and then lost cannot be reviewed when the event goes wrong. The database refuses a
 * conflict row without its competing commitment ({@code ck_cleaning_reservations_conflict}).
 */
public record CapacityReservation(
        UUID id,
        String siteCode,
        UUID roomId,
        String locationCode,
        Instant windowFrom,
        Instant windowTo,
        String scope,
        String eventReference,
        String requestedBy,
        ReservationStatus status,
        UUID taskId,
        UUID competingTaskId,
        String competingCommitment,
        Instant competingFrom,
        Instant competingTo,
        Instant releasedAt,
        String releaseReason,
        RecordMetadata metadata) {

    public CapacityReservation {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(windowFrom, "windowFrom is required");
        Objects.requireNonNull(windowTo, "windowTo is required");
        if (!windowTo.isAfter(windowFrom)) {
            throw new FacilitiesException.ValidationFailedException("A cleaning slot must end after it starts.");
        }
        scope = EstateCodes.blankToNull(scope);
        EstateCodes.require(eventReference, "eventReference");
        eventReference = eventReference.strip();
        EstateCodes.require(requestedBy, "requestedBy");
        Objects.requireNonNull(status, "status is required");
        if (status == ReservationStatus.RESERVED && taskId == null) {
            throw new IllegalArgumentException("a reservation names the task it raised");
        }
        if (status == ReservationStatus.CONFLICT && (competingTaskId == null || competingCommitment == null)) {
            // SRS-SFL-S169-04 validation: "must name the competing commitment, not just state that none
            // is available". Unrepresentable rather than merely discouraged.
            throw new IllegalArgumentException("a conflict names its competing commitment");
        }
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static CapacityReservation reserved(UUID id, String siteCode, UUID roomId, String locationCode,
            Instant from, Instant to, String scope, String eventReference, String requestedBy, UUID taskId,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new CapacityReservation(id, siteCode, roomId, locationCode, from, to, scope, eventReference,
                requestedBy, ReservationStatus.RESERVED, taskId, null, null, null, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public static CapacityReservation conflict(UUID id, String siteCode, UUID roomId, String locationCode,
            Instant from, Instant to, String scope, String eventReference, String requestedBy, UUID competingTaskId,
            String competingCommitment, Instant competingFrom, Instant competingTo, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new CapacityReservation(id, siteCode, roomId, locationCode, from, to, scope, eventReference,
                requestedBy, ReservationStatus.CONFLICT, null, competingTaskId, competingCommitment, competingFrom,
                competingTo, null, null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public CapacityReservation release(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new CapacityReservation(id, siteCode, roomId, locationCode, windowFrom, windowTo, scope,
                eventReference, requestedBy, ReservationStatus.RELEASED, taskId, competingTaskId, competingCommitment,
                competingFrom, competingTo, at, EstateCodes.blankToNull(reason),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
