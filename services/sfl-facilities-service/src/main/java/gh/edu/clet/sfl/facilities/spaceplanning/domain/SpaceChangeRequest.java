package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A unit's governed request for space - SRS-SFL-S158-04.
 *
 * <p>"Request submitted -> reviewed and approved/declined -> approved request becomes a scenario or an S176
 * project -> resolved with linked outcome." The state machine is that sentence:
 *
 * <pre>
 *   SUBMITTED --approve--> APPROVED --link scenario / S176 project--> ACTIONED --resolve--> RESOLVED
 *            \--decline--> DECLINED
 * </pre>
 *
 * <p>Resolution needs a linked outcome - "Unlinked Resolution: an attempt to close a request without a
 * linked scenario or project; refused" - which is why {@code ACTIONED} exists at all: it is the state in
 * which the outcome is linked but the space change has not yet happened.
 *
 * @param targetRoomId the S152 space asked for, when the unit has one in mind
 * @param targetAreaDescription what is wanted when no single space is named ("two offices near the
 *        library"). One of the two is required
 */
public record SpaceChangeRequest(
        UUID id,
        String reference,
        String siteCode,
        String requestingUnit,
        String justification,
        UUID targetRoomId,
        String targetRoomCode,
        String targetAreaDescription,
        Integer requiredHeadcount,
        Urgency urgency,
        Status status,
        String requestedBy,
        Instant requestedAt,
        String decidedBy,
        Instant decidedAt,
        String decisionReason,
        OutcomeType outcomeType,
        UUID linkedScenarioId,
        UUID linkedProjectId,
        String linkedProjectReference,
        String resolvedBy,
        Instant resolvedAt,
        String resolutionNote,
        RecordMetadata metadata) {

    public enum Urgency {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    public enum Status {
        SUBMITTED,
        APPROVED,
        DECLINED,
        ACTIONED,
        RESOLVED;

        public Set<Status> allowedTransitions() {
            return switch (this) {
                case SUBMITTED -> EnumSet.of(APPROVED, DECLINED);
                case APPROVED -> EnumSet.of(ACTIONED);
                case ACTIONED -> EnumSet.of(RESOLVED);
                case DECLINED, RESOLVED -> EnumSet.noneOf(Status.class);
            };
        }

        public boolean isOpen() {
            return this != DECLINED && this != RESOLVED;
        }
    }

    /** How the approved request is being met. */
    public enum OutcomeType {
        /** A like-for-like reassignment, modelled and committed as an S158 scenario. */
        SCENARIO,
        /** Physical works, handed to S176 Construction Project Management. */
        CONSTRUCTION_PROJECT
    }

    public SpaceChangeRequest {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(urgency, "urgency is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
        reference = AllocationScenario.requireText(reference, "reference", 40);
        siteCode = AllocationScenario.requireText(siteCode, "siteCode", 40);
        requestingUnit = AllocationScenario.requireText(requestingUnit, "requestingUnit", 200);
        justification = AllocationScenario.requireText(justification, "justification", 4000);
        targetAreaDescription = AllocationScenario.optional(targetAreaDescription, "targetAreaDescription", 2000);
        if (targetRoomId == null && targetAreaDescription == null) {
            throw new IllegalArgumentException(
                    "A space-change request must name a target space or describe the area wanted.");
        }
        if (requiredHeadcount != null && requiredHeadcount < 0) {
            throw new IllegalArgumentException("requiredHeadcount cannot be negative");
        }
    }

    public static SpaceChangeRequest submit(UUID id, String reference, String siteCode, String requestingUnit,
            String justification, UUID targetRoomId, String targetRoomCode, String targetAreaDescription,
            Integer requiredHeadcount, Urgency urgency, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new SpaceChangeRequest(id, reference, siteCode, requestingUnit, justification, targetRoomId,
                targetRoomCode, targetAreaDescription, requiredHeadcount, urgency == null ? Urgency.NORMAL : urgency,
                Status.SUBMITTED, actorId, at, null, null, null, null, null, null, null, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Approves or declines. A decline must say why - a unit told "no" with no reason files the same
     * request again next week.
     */
    public SpaceChangeRequest decide(boolean approve, String reason, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        Status target = approve ? Status.APPROVED : Status.DECLINED;
        transition(target);
        if (!approve && (reason == null || reason.isBlank())) {
            throw new FacilitiesException.ValidationFailedException("Declining a space-change request requires a reason.");
        }
        return new SpaceChangeRequest(id, reference, siteCode, requestingUnit, justification, targetRoomId,
                targetRoomCode, targetAreaDescription, requiredHeadcount, urgency, target, requestedBy, requestedAt,
                actorId, at, AllocationScenario.optional(reason, "decisionReason", 2000), outcomeType,
                linkedScenarioId, linkedProjectId, linkedProjectReference, resolvedBy, resolvedAt, resolutionNote,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Links the scenario that will realise the request. */
    public SpaceChangeRequest linkScenario(UUID scenarioId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Objects.requireNonNull(scenarioId, "scenarioId is required");
        if (outcomeType == OutcomeType.CONSTRUCTION_PROJECT) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "This request has already been handed to S176 as a construction project.");
        }
        Status target = status == Status.APPROVED ? Status.ACTIONED : status;
        if (target != Status.ACTIONED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only an approved request can be actioned; this one is " + status + ".");
        }
        return new SpaceChangeRequest(id, reference, siteCode, requestingUnit, justification, targetRoomId,
                targetRoomCode, targetAreaDescription, requiredHeadcount, urgency, target, requestedBy, requestedAt,
                decidedBy, decidedAt, decisionReason, OutcomeType.SCENARIO, scenarioId, linkedProjectId,
                linkedProjectReference, resolvedBy, resolvedAt, resolutionNote,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * Links an S176 project. Either the request's own hand-off (outcome {@code CONSTRUCTION_PROJECT}) or the
     * project a linked scenario's physical-works commit proposed (outcome stays {@code SCENARIO}).
     */
    public SpaceChangeRequest linkProject(UUID projectId, String projectReference, OutcomeType type, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        Objects.requireNonNull(projectId, "projectId is required");
        Status target = status == Status.APPROVED ? Status.ACTIONED : status;
        if (target != Status.ACTIONED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only an approved request can be actioned; this one is " + status + ".");
        }
        return new SpaceChangeRequest(id, reference, siteCode, requestingUnit, justification, targetRoomId,
                targetRoomCode, targetAreaDescription, requiredHeadcount, urgency, target, requestedBy, requestedAt,
                decidedBy, decidedAt, decisionReason, outcomeType == null ? type : outcomeType, linkedScenarioId,
                projectId, projectReference, resolvedBy, resolvedAt, resolutionNote,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * Closes the request. Refused with no linked outcome - the SRS "Unlinked Resolution" error state.
     * Whether a linked scenario is actually committed is the application service's check, since it needs
     * the scenario.
     */
    public SpaceChangeRequest resolve(String note, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (!hasLinkedOutcome()) {
            throw new FacilitiesException(FacilitiesErrorCode.SPACE_CHANGE_UNLINKED_RESOLUTION);
        }
        transition(Status.RESOLVED);
        return new SpaceChangeRequest(id, reference, siteCode, requestingUnit, justification, targetRoomId,
                targetRoomCode, targetAreaDescription, requiredHeadcount, urgency, Status.RESOLVED, requestedBy,
                requestedAt, decidedBy, decidedAt, decisionReason, outcomeType, linkedScenarioId, linkedProjectId,
                linkedProjectReference, actorId, at, AllocationScenario.optional(note, "resolutionNote", 2000),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean hasLinkedOutcome() {
        return linkedScenarioId != null || linkedProjectId != null;
    }

    private void transition(Status target) {
        if (!status.allowedTransitions().contains(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A " + status + " space-change request cannot become " + target + ".");
        }
    }
}
