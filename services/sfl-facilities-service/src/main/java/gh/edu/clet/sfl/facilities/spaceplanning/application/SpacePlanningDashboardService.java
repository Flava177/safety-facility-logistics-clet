package gh.edu.clet.sfl.facilities.spaceplanning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.SpaceAllocationService;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.SpacePlanningRepository;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.AllocationScenario;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ComplianceStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyOverride;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.RoomCompliance;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The S158 dashboard read: "Current versus planned space utilisation, allocation-scenario comparison,
 * space-change request pipeline, occupancy-standard compliance by unit."
 *
 * <p>One read model, assembled from S158's own records and the S152 register - nothing here writes, and
 * nothing is cached, so it is exactly as current as the last reconciliation run and the register.
 *
 * <p>"Compliance by unit" is computed for the <em>current register</em>, not for drafts: a draft's
 * compliance is on its own comparison view. A room is counted against every unit allocated in it,
 * because each of those units is in a room that is, or is not, within its standard.
 */
@Service
public class SpacePlanningDashboardService {

    private final SpacePlanningRepository repository;
    private final FacilitiesRepository facilities;
    private final SpaceAllocationService register;
    private final OccupancyStandardService standards;
    private final SpaceScenarioService scenarios;
    private final FacilitiesAuthorization authorization;

    public SpacePlanningDashboardService(SpacePlanningRepository repository, FacilitiesRepository facilities,
            SpaceAllocationService register, OccupancyStandardService standards, SpaceScenarioService scenarios,
            FacilitiesAuthorization authorization) {
        this.repository = repository;
        this.facilities = facilities;
        this.register = register;
        this.standards = standards;
        this.scenarios = scenarios;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_PLAN_READ, siteCode, channel,
                "SpacePlanningDashboard", siteCode);
        List<SpaceAllocation> current = register.current(siteCode, actor, channel);

        List<UtilisationRow> utilisation = repository.findLatestSnapshots(siteCode).stream()
                .map(snapshot -> new UtilisationRow(snapshot.roomId(), snapshot.roomCode(), snapshot.periodStart(),
                        snapshot.periodEnd(), snapshot.capacity(), snapshot.plannedHeadcount(),
                        snapshot.plannedOccupancyRate(), snapshot.utilisationRate(), snapshot.observable()))
                .toList();

        List<AllocationScenario> all = repository.findScenarios(siteCode, null);
        Map<ScenarioStatus, Long> byStatus = new EnumMap<>(ScenarioStatus.class);
        all.forEach(scenario -> byStatus.merge(scenario.status(), 1L, Long::sum));
        List<UUID> drafts = all.stream().filter(scenario -> scenario.status().isDraft()).map(AllocationScenario::id)
                .limit(10).toList();
        List<SpaceScenarioService.ScenarioTotals> comparison = drafts.isEmpty() ? List.of()
                : scenarios.compare(drafts, actor, channel).scenarios();
        List<String> awaitingHandover = all.stream().filter(AllocationScenario::awaitingHandover)
                .map(scenario -> scenario.displayReference() + " -> " + scenario.linkedProjectReference()).toList();

        List<SpaceChangeRequest> requests = repository.findRequests(siteCode, null, null);
        Map<SpaceChangeRequest.Status, Long> pipeline = new EnumMap<>(SpaceChangeRequest.Status.class);
        Map<SpaceChangeRequest.Urgency, Long> openByUrgency = new EnumMap<>(SpaceChangeRequest.Urgency.class);
        for (SpaceChangeRequest request : requests) {
            pipeline.merge(request.status(), 1L, Long::sum);
            if (request.status().isOpen()) {
                openByUrgency.merge(request.urgency(), 1L, Long::sum);
            }
        }

        Map<UtilisationSignal.Kind, Long> signals = new EnumMap<>(UtilisationSignal.Kind.class);
        repository.findSignals(siteCode, true).forEach(signal -> signals.merge(signal.kind(), 1L, Long::sum));

        return new Dashboard(siteCode, utilisation, byStatus, comparison, awaitingHandover, pipeline, openByUrgency,
                complianceByUnit(current), signals);
    }

    /** Compliance of the register, rolled up per allocated unit. */
    List<UnitCompliance> complianceByUnit(List<SpaceAllocation> current) {
        Map<UUID, List<SpaceAllocation>> byRoom = current.stream()
                .collect(Collectors.groupingBy(SpaceAllocation::roomId, LinkedHashMap::new, Collectors.toList()));
        Set<UUID> sources = current.stream().map(SpaceAllocation::sourceScenarioId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<OccupancyOverride> overrides = repository.findLiveOverrides(sources);

        Map<String, int[]> tally = new TreeMap<>();
        for (Map.Entry<UUID, List<SpaceAllocation>> room : byRoom.entrySet()) {
            FacilityRoom space = facilities.findRoom(room.getKey()).orElse(null);
            if (space == null) {
                continue;
            }
            int headcount = room.getValue().stream().mapToInt(SpaceAllocation::headcount).sum();
            Set<UUID> roomSources = room.getValue().stream().map(SpaceAllocation::sourceScenarioId)
                    .collect(Collectors.toSet());
            boolean overridden = overrides.stream().anyMatch(override -> override.roomId().equals(space.id())
                    && roomSources.contains(override.scenarioId())
                    && override.status() == OccupancyOverride.OverrideStatus.APPROVED
                    && override.headcountCovered() >= headcount);
            RoomCompliance compliance = standards.evaluate(space, headcount).withOverride(overridden);
            for (SpaceAllocation allocation : room.getValue()) {
                int[] counts = tally.computeIfAbsent(allocation.allocatedUnit(), unit -> new int[6]);
                counts[0]++;
                counts[1] += allocation.headcount();
                counts[2] += compliance.status() == ComplianceStatus.COMPLIANT ? 1 : 0;
                counts[3] += compliance.status() == ComplianceStatus.NON_COMPLIANT ? 1 : 0;
                counts[4] += compliance.status() == ComplianceStatus.NOT_EVALUATED ? 1 : 0;
                counts[5] += compliance.flagged() ? 1 : 0;
            }
        }
        List<UnitCompliance> result = new ArrayList<>();
        tally.forEach((unit, c) -> result.add(new UnitCompliance(unit, c[0], c[1], c[2], c[3], c[4], c[5])));
        return result;
    }

    public record Dashboard(String siteCode, List<UtilisationRow> currentVersusPlanned,
            Map<ScenarioStatus, Long> scenariosByStatus, List<SpaceScenarioService.ScenarioTotals> draftComparison,
            List<String> awaitingHandover, Map<SpaceChangeRequest.Status, Long> requestPipeline,
            Map<SpaceChangeRequest.Urgency, Long> openRequestsByUrgency, List<UnitCompliance> complianceByUnit,
            Map<UtilisationSignal.Kind, Long> activeSignals) {
    }

    /** @param observable {@code false} when S159 data cannot say anything about the space - see the runbook */
    public record UtilisationRow(UUID roomId, String roomCode, Instant periodStart, Instant periodEnd,
            Integer capacity, int plannedHeadcount, BigDecimal plannedOccupancyRate, BigDecimal utilisationRate,
            boolean observable) {
    }

    public record UnitCompliance(String allocatedUnit, int rooms, int headcount, int compliantRooms,
            int nonCompliantRooms, int notEvaluatedRooms, int flaggedRooms) {
    }
}
