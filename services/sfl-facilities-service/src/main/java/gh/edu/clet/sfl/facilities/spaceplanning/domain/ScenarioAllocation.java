package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of a scenario: a unit and its headcount in one S152 space - SRS-SFL-S158-01/-02.
 *
 * <p>A room the scenario leaves empty is one line with no unit and a headcount of zero - a
 * <em>vacate</em> line. Without it a scenario could not say "OFF-101 is given up", only stay silent
 * about OFF-101, and silence means "unchanged".
 *
 * <p>The compliance columns are the room-level result stamped on each of its lines when the room was
 * last saved, and re-stamped at commit - so a committed scenario records what it was checked against
 * on the day, even after the standard is revised.
 */
public record ScenarioAllocation(
        UUID id,
        UUID scenarioId,
        String siteCode,
        UUID roomId,
        String roomCode,
        String allocatedUnit,
        int headcount,
        ComplianceStatus complianceStatus,
        String complianceDetail,
        UUID standardId,
        Integer standardVersion,
        RecordMetadata metadata) {

    public ScenarioAllocation {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(scenarioId, "scenarioId is required");
        Objects.requireNonNull(roomId, "roomId is required");
        Objects.requireNonNull(complianceStatus, "complianceStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        allocatedUnit = allocatedUnit == null || allocatedUnit.isBlank() ? null : allocatedUnit.strip();
        if (headcount < 0) {
            throw new IllegalArgumentException("headcount cannot be negative");
        }
        if (allocatedUnit == null && headcount != 0) {
            throw new IllegalArgumentException("a vacate line carries no headcount");
        }
    }

    public static ScenarioAllocation line(UUID id, UUID scenarioId, String siteCode, UUID roomId, String roomCode,
            String allocatedUnit, int headcount, RoomCompliance compliance, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new ScenarioAllocation(id, scenarioId, siteCode, roomId, roomCode, allocatedUnit, headcount,
                compliance.status(), compliance.summary(), compliance.standardId(), compliance.standardVersion(),
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public ScenarioAllocation restamp(RoomCompliance compliance, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new ScenarioAllocation(id, scenarioId, siteCode, roomId, roomCode, allocatedUnit, headcount,
                compliance.status(), compliance.summary(), compliance.standardId(), compliance.standardVersion(),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isVacate() {
        return allocatedUnit == null;
    }
}
