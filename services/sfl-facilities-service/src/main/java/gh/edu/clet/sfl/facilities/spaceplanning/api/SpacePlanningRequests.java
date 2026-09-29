package gh.edu.clet.sfl.facilities.spaceplanning.api;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.CommitOutcome;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UnitHeadcount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The S158 request bodies. Bean Validation on every field the SRS constrains, as {@code BookingRequests} does. */
public final class SpacePlanningRequests {

    private SpacePlanningRequests() {
    }

    public record CreateScenario(@NotBlank String siteCode, @NotBlank @Size(max = 200) String name,
            @Size(max = 4000) String description, UUID spaceChangeRequestId) {
    }

    public record ReviseScenario(@Size(max = 200) String name, @Size(max = 4000) String description) {
    }

    public record RenameScenario(@Size(max = 200) String name, @Size(max = 4000) String description,
            Long expectedVersion) {
    }

    public record UnitLine(@NotBlank @Size(max = 200) String allocatedUnit, @NotNull Integer headcount) {
        UnitHeadcount toDomain() {
            return new UnitHeadcount(allocatedUnit, headcount);
        }
    }

    /** An empty {@code units} list vacates the room in the scenario. */
    public record SetRoomAllocation(@Valid List<UnitLine> units, Long expectedVersion) {
        public List<UnitHeadcount> toUnits() {
            return units == null ? List.of() : units.stream().map(UnitLine::toDomain).toList();
        }
    }

    public record CommitScenario(@NotNull CommitOutcome outcome, @Size(max = 2000) String note,
            @Size(max = 200) String projectTitle, @Size(max = 2000) String projectScope, Long expectedVersion) {
    }

    public record DiscardScenario(@NotBlank @Size(max = 2000) String reason, Long expectedVersion) {
    }

    public record DefineStandard(@NotBlank String siteCode, @NotNull SpaceType spaceType,
            Integer maxCapacityPercent, BigDecimal minAreaPerPersonSqm, @Size(max = 2000) String note) {
    }

    public record RequestOverride(@NotNull UUID roomId, @NotBlank @Size(max = 2000) String reason) {
    }

    public record RunReconciliation(@NotBlank String siteCode, Instant periodEnd) {
    }

    public record SubmitRequest(@NotBlank String siteCode, @NotBlank @Size(max = 200) String requestingUnit,
            @NotBlank @Size(max = 4000) String justification, UUID targetRoomId,
            @Size(max = 2000) String targetAreaDescription, Integer requiredHeadcount,
            SpaceChangeRequest.Urgency urgency) {
    }

    public record DecideRequest(@NotNull Boolean approve, @Size(max = 2000) String reason, Long expectedVersion) {
    }

    public record LinkScenario(@NotNull UUID scenarioId) {
    }

    public record HandToConstruction(@Size(max = 200) String title, @Size(max = 2000) String scope,
            List<UUID> roomIds) {
        public List<UUID> roomIdsOrEmpty() {
            return roomIds == null ? List.of() : roomIds;
        }
    }

    public record ResolveRequest(@Size(max = 2000) String note, Long expectedVersion) {
    }

    public record CompareScenarios(@NotEmpty List<UUID> scenarioIds) {
    }
}
