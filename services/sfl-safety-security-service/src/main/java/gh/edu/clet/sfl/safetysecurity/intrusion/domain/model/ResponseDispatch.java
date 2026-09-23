package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Coordination record with the external monitoring/armed-response contract for one confirmed alarm -
 * SRS-SFL-S162-05 (SHOULD). One row per alarm; kept as a single evolving record rather than an event
 * stream, proportionate to what the requirement asks for (dispatch, acknowledgement, arrival,
 * outcome) rather than a full contract-management subsystem.
 */
public record ResponseDispatch(UUID id, String siteCode, UUID alarmId, String monitoringService,
        Instant dispatchRequestedAt, Instant acknowledgedAt, Instant arrivedAt, DispatchOutcome outcome,
        String notes, RecordMetadata metadata) {

    public ResponseDispatch {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        Objects.requireNonNull(alarmId, "alarmId is required");
        require(monitoringService, "monitoringService");
        Objects.requireNonNull(dispatchRequestedAt, "dispatchRequestedAt is required");
        notes = blankToNull(notes);
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static ResponseDispatch request(UUID id, String siteCode, UUID alarmId, String monitoringService,
            Instant dispatchRequestedAt, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new ResponseDispatch(id, siteCode, alarmId, monitoringService, dispatchRequestedAt, null, null,
                DispatchOutcome.PENDING, null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public ResponseDispatch acknowledge(Instant acknowledgedAt, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new ResponseDispatch(id, siteCode, alarmId, monitoringService, dispatchRequestedAt, acknowledgedAt,
                arrivedAt, outcome, notes, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public ResponseDispatch recordOutcome(Instant arrivedAt, DispatchOutcome newOutcome, String notesText,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new ResponseDispatch(id, siteCode, alarmId, monitoringService, dispatchRequestedAt, acknowledgedAt,
                arrivedAt, newOutcome, notesText, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
