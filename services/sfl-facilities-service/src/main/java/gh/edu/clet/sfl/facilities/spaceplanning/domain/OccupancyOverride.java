package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recorded acceptance of a non-compliant allocation - SRS-SFL-S158-02.
 *
 * <p>"A compliance override must carry a reason and an accountable approver." Two people, in two steps:
 * the planner who wants the allocation asks, with the reason; somebody holding
 * {@code FACILITIES_OCCUPANCY_OVERRIDE_APPROVE} who is not that planner approves. A single call in which
 * the requester typed an approver's name would record an approver nobody authenticated.
 *
 * <h2>What it covers</h2>
 *
 * One room in one scenario, at the headcount it was asked for. If the room's allocation changes
 * afterwards the override is withdrawn - approving thirty people in a room does not approve sixty.
 */
public record OccupancyOverride(
        UUID id,
        String siteCode,
        UUID scenarioId,
        UUID roomId,
        String roomCode,
        int headcountCovered,
        String complianceDetail,
        String reason,
        OverrideStatus status,
        String requestedBy,
        Instant requestedAt,
        String approvedBy,
        Instant approvedAt,
        Instant withdrawnAt,
        RecordMetadata metadata) {

    public enum OverrideStatus {
        PENDING,
        APPROVED,
        WITHDRAWN
    }

    public OccupancyOverride {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(scenarioId, "scenarioId is required");
        Objects.requireNonNull(roomId, "roomId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (reason == null || reason.isBlank()) {
            throw incomplete("The reason is missing.");
        }
        reason = AllocationScenario.requireText(reason, "reason", 2000);
    }

    public static OccupancyOverride request(UUID id, String siteCode, UUID scenarioId, UUID roomId, String roomCode,
            int headcountCovered, String complianceDetail, String reason, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new OccupancyOverride(id, siteCode, scenarioId, roomId, roomCode, headcountCovered, complianceDetail,
                reason, OverrideStatus.PENDING, actorId, at, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Approves. Refuses the requester approving their own - that is exactly the missing "accountable
     * approver" the SRS names.
     */
    public OccupancyOverride approve(String approverId, Instant at, SourceChannel channel, String correlationId) {
        if (status != OverrideStatus.PENDING) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only a pending override can be approved; this one is " + status + ".");
        }
        if (approverId == null || approverId.isBlank() || approverId.equals(requestedBy)) {
            throw incomplete("The approver must be someone other than the person who requested it.");
        }
        return new OccupancyOverride(id, siteCode, scenarioId, roomId, roomCode, headcountCovered, complianceDetail,
                reason, OverrideStatus.APPROVED, requestedBy, requestedAt, approverId, at, null,
                metadata.modifiedBy(approverId, at, channel, correlationId));
    }

    public OccupancyOverride withdraw(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new OccupancyOverride(id, siteCode, scenarioId, roomId, roomCode, headcountCovered, complianceDetail,
                reason, OverrideStatus.WITHDRAWN, requestedBy, requestedAt, approvedBy, approvedAt, at,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isLive() {
        return status != OverrideStatus.WITHDRAWN;
    }

    public static FacilitiesException incomplete(String detail) {
        return new FacilitiesException(FacilitiesErrorCode.SPACE_OVERRIDE_INCOMPLETE,
                FacilitiesErrorCode.SPACE_OVERRIDE_INCOMPLETE.defaultMessage() + " " + detail);
    }
}
