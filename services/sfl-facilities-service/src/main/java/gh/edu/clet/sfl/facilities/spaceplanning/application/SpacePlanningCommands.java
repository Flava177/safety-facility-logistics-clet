package gh.edu.clet.sfl.facilities.spaceplanning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.CommitOutcome;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UnitHeadcount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The S158 commands. Each carries its actor and channel, as every facilities command does. */
public final class SpacePlanningCommands {

    private SpacePlanningCommands() {
    }

    // ---- S158-01 scenarios ------------------------------------------------------------------------

    public record CreateScenario(String siteCode, String name, String description, UUID spaceChangeRequestId,
            ActorContext actor, SourceChannel channel) {
    }

    public record ReviseScenario(UUID scenarioId, String name, String description, ActorContext actor,
            SourceChannel channel) {
    }

    public record RenameScenario(UUID scenarioId, String name, String description, Long expectedVersion,
            ActorContext actor, SourceChannel channel) {
    }

    /** @param units empty vacates the room in the scenario */
    public record SetRoomAllocation(UUID scenarioId, UUID roomId, List<UnitHeadcount> units, Long expectedVersion,
            ActorContext actor, SourceChannel channel) {
        public SetRoomAllocation {
            units = units == null ? List.of() : List.copyOf(units);
        }
    }

    public record RemoveRoom(UUID scenarioId, UUID roomId, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    /**
     * @param projectTitle for a physical-works commit, the S176 project's title; defaults to the scenario
     *        name
     * @param projectScope for a physical-works commit, what the works are
     */
    public record CommitScenario(UUID scenarioId, CommitOutcome outcome, String note, String projectTitle,
            String projectScope, Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record DiscardScenario(UUID scenarioId, String reason, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    // ---- S158-02 standards and overrides ----------------------------------------------------------

    public record DefineStandard(String siteCode, SpaceType spaceType, Integer maxCapacityPercent,
            BigDecimal minAreaPerPersonSqm, String note, ActorContext actor, SourceChannel channel) {
    }

    public record RequestOverride(UUID scenarioId, UUID roomId, String reason, ActorContext actor,
            SourceChannel channel) {
    }

    public record ApproveOverride(UUID overrideId, ActorContext actor, SourceChannel channel) {
    }

    // ---- S158-03 utilisation ----------------------------------------------------------------------

    /** @param periodEnd a period boundary to (re)evaluate; {@code null} for the latest complete period */
    public record RunReconciliation(String siteCode, Instant periodEnd, ActorContext actor, SourceChannel channel) {
    }

    // ---- S158-04 space-change requests ------------------------------------------------------------

    public record SubmitRequest(String siteCode, String requestingUnit, String justification, UUID targetRoomId,
            String targetAreaDescription, Integer requiredHeadcount, SpaceChangeRequest.Urgency urgency,
            ActorContext actor, SourceChannel channel) {
    }

    public record DecideRequest(UUID requestId, boolean approve, String reason, Long expectedVersion,
            ActorContext actor, SourceChannel channel) {
    }

    public record LinkScenario(UUID requestId, UUID scenarioId, ActorContext actor, SourceChannel channel) {
    }

    public record HandToConstruction(UUID requestId, String title, String scope, List<UUID> roomIds,
            ActorContext actor, SourceChannel channel) {
        public HandToConstruction {
            roomIds = roomIds == null ? List.of() : List.copyOf(roomIds);
        }
    }

    public record ResolveRequest(UUID requestId, String note, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }
}
