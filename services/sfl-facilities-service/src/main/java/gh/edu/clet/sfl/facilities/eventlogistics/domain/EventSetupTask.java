package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The physical set-up task for one S078 event - SRS-SFL-S173-01..04.
 *
 * <h2>Why there is exactly one way to make one</h2>
 *
 * <p>S173-01's validation rule: "A set-up task cannot exist without a resolvable S078 event reference".
 * {@link #fromHandoff} is the only factory, it requires the reference, and the only caller is the
 * hand-off intake after the reference has been resolved - {@code EventLogisticsArchitectureTest} holds
 * that, and {@code s078_event_reference} is {@code NOT NULL} and unique in V20. There is no "create a
 * set-up task" endpoint, and adding one would be the speculative creation the rule forbids.
 *
 * @param coordinatorId the coordinator who took the task on - the first to decompose it. Escalations
 *        go to them; before anybody has, to the site's coordinator desk
 * @param riskAssessmentId the linked S165 assessment, held by value (S165 is in SSEMP)
 * @param handoffCount how many accepted hand-offs have been applied - one for a task S078 never changed
 */
public record EventSetupTask(
        UUID id,
        String siteCode,
        String taskReference,
        String s078EventReference,
        EventDetails details,
        EventSetupTaskStatus status,
        String coordinatorId,
        String riskAssessmentId,
        Integer riskAssessmentVersion,
        String riskAssessmentLinkedBy,
        Instant riskAssessmentLinkedAt,
        String confirmedBy,
        Instant confirmedAt,
        String completedBy,
        Instant completedAt,
        String closureReason,
        int handoffCount,
        Instant lastHandoffAt,
        RecordMetadata metadata) {

    public EventSetupTask {
        Objects.requireNonNull(id, "id is required");
        if (s078EventReference == null || s078EventReference.isBlank()) {
            // The invariant S173-01 states, held by the type as well as by the database.
            throw new IllegalArgumentException("A set-up task cannot exist without an S078 event reference");
        }
        Objects.requireNonNull(details, "details are required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    /** The only factory. See the class Javadoc. */
    public static EventSetupTask fromHandoff(UUID id, String siteCode, String taskReference,
            String s078EventReference, EventDetails details, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new EventSetupTask(id, siteCode, taskReference, s078EventReference, details,
                EventSetupTaskStatus.OPEN, null, null, null, null, null, null, null, null, null, null, 1, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Applies a later S078 hand-off's details.
     *
     * @param revertConfirmation whether a confirmed task goes back to OPEN - true when the change is one
     *        the confirmation depended on (see {@link EventDetails#materiallyDiffersFrom})
     */
    public EventSetupTask withHandoffDetails(EventDetails newDetails, boolean revertConfirmation, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        requireLive("apply an S078 update to");
        boolean revert = revertConfirmation && status == EventSetupTaskStatus.CONFIRMED;
        return new EventSetupTask(id, siteCode, taskReference, s078EventReference, newDetails,
                revert ? EventSetupTaskStatus.OPEN : status, coordinatorId, riskAssessmentId, riskAssessmentVersion,
                riskAssessmentLinkedBy, riskAssessmentLinkedAt, revert ? null : confirmedBy,
                revert ? null : confirmedAt, completedBy, completedAt, closureReason, handoffCount + 1, at,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public EventSetupTask withCoordinator(String coordinator, Instant at, SourceChannel channel,
            String correlationId) {
        if (coordinatorId != null) {
            return this;
        }
        return new EventSetupTask(id, siteCode, taskReference, s078EventReference, details, status, coordinator,
                riskAssessmentId, riskAssessmentVersion, riskAssessmentLinkedBy, riskAssessmentLinkedAt,
                confirmedBy, confirmedAt, completedBy, completedAt, closureReason, handoffCount, lastHandoffAt,
                metadata.modifiedBy(coordinator, at, channel, correlationId));
    }

    public EventSetupTask linkRiskAssessment(String assessmentId, int version, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        requireLive("link a risk assessment to");
        return new EventSetupTask(id, siteCode, taskReference, s078EventReference, details, status, coordinatorId,
                assessmentId, version, actorId, at, confirmedBy, confirmedAt, completedBy, completedAt,
                closureReason, handoffCount, lastHandoffAt, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public EventSetupTask confirm(String actorId, Instant at, SourceChannel channel, String correlationId) {
        transition(EventSetupTaskStatus.CONFIRMED);
        return new EventSetupTask(id, siteCode, taskReference, s078EventReference, details,
                EventSetupTaskStatus.CONFIRMED, coordinatorId, riskAssessmentId, riskAssessmentVersion,
                riskAssessmentLinkedBy, riskAssessmentLinkedAt, actorId, at, completedBy, completedAt, closureReason,
                handoffCount, lastHandoffAt, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public EventSetupTask complete(String notes, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        transition(EventSetupTaskStatus.COMPLETED);
        return new EventSetupTask(id, siteCode, taskReference, s078EventReference, details,
                EventSetupTaskStatus.COMPLETED, coordinatorId, riskAssessmentId, riskAssessmentVersion,
                riskAssessmentLinkedBy, riskAssessmentLinkedAt, confirmedBy, confirmedAt, actorId, at,
                notes == null || notes.isBlank() ? null : notes.strip(), handoffCount, lastHandoffAt,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** S078 cancelled the event. */
    public EventSetupTask cancel(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        transition(EventSetupTaskStatus.CANCELLED);
        return new EventSetupTask(id, siteCode, taskReference, s078EventReference, details,
                EventSetupTaskStatus.CANCELLED, coordinatorId, riskAssessmentId, riskAssessmentVersion,
                riskAssessmentLinkedBy, riskAssessmentLinkedAt, confirmedBy, confirmedAt, completedBy, completedAt,
                reason, handoffCount + 1, at, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean hasLinkedRiskAssessment() {
        return riskAssessmentId != null;
    }

    public void requireLive(String doing) {
        if (!status.isLive()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Cannot " + doing + " set-up task " + taskReference + ": it is " + status + ".");
        }
    }

    private void transition(EventSetupTaskStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Set-up task " + taskReference + " cannot move from " + status + " to " + target + ".");
        }
    }
}
