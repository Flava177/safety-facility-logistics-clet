package gh.edu.clet.sfl.facilities.masterdata.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One unit's current occupation of one S152 space - the current-state allocation register.
 *
 * <p>SRS-SFL-S158 describes space planning as "a scenario layer under CAFM/IWMS (S152) ... which remains
 * the authoritative current-state register". Phase 1 S152 held no such register - a room carried only a
 * {@code costCentre} - so there was nothing for a committed scenario to update. This record is that
 * register, added to S152 rather than to S158 so the thing S158 plans <em>against</em> is not owned by
 * the planner: a draft that could write here would be the "parallel register" the SRS rules out.
 *
 * <h2>Never overwritten</h2>
 *
 * A change ends the current row ({@link #endedAt}) and inserts a new one; nothing is updated in place
 * except that end-stamp. The register therefore answers "who was in OFF-101 last March" as well as "who
 * is in it now", and an allocation that arrived from the wrong scenario can be traced to it by
 * {@link #sourceScenarioId} and reversed by the next one.
 *
 * @param allocatedUnit the organisational unit occupying the space. Free text keyed by the planner,
 *        because the HRMS (S140) unit list the SRS names as the source is not available to SFL - see the
 *        S158 gap report
 * @param headcount people the unit places in the space; zero is a valid reservation with nobody in it
 * @param sourceScenarioId the committed S158 scenario this row came from, held by value - S152 does
 *        not depend on S158
 * @param sourceReference the scenario's human reference and version, e.g. {@code SP-MAIN-000003 v2}
 */
public record SpaceAllocation(
        UUID id,
        String siteCode,
        UUID roomId,
        String roomCode,
        String allocatedUnit,
        int headcount,
        Instant allocatedSince,
        UUID sourceScenarioId,
        String sourceReference,
        Instant endedAt,
        UUID endedByScenarioId,
        RecordMetadata metadata) {

    public SpaceAllocation {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(roomId, "roomId is required");
        Objects.requireNonNull(allocatedSince, "allocatedSince is required");
        Objects.requireNonNull(sourceScenarioId, "sourceScenarioId is required");
        Objects.requireNonNull(metadata, "metadata is required");
        siteCode = requireText(siteCode, "siteCode");
        roomCode = requireText(roomCode, "roomCode");
        allocatedUnit = requireText(allocatedUnit, "allocatedUnit");
        if (headcount < 0) {
            throw new IllegalArgumentException("headcount cannot be negative");
        }
    }

    public static SpaceAllocation allocate(UUID id, FacilityRoom room, String allocatedUnit, int headcount,
            UUID sourceScenarioId, String sourceReference, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new SpaceAllocation(id, room.siteCode(), room.id(), room.roomCode(), allocatedUnit, headcount, at,
                sourceScenarioId, sourceReference, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Ends this allocation because a later committed scenario replaced it. */
    public SpaceAllocation end(UUID byScenarioId, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (endedAt != null) {
            throw new IllegalStateException("allocation " + id + " has already ended");
        }
        return new SpaceAllocation(id, siteCode, roomId, roomCode, allocatedUnit, headcount, allocatedSince,
                sourceScenarioId, sourceReference, at, byScenarioId,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isCurrent() {
        return endedAt == null;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
