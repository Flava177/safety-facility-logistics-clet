package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.SpacePlanningRepository;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.AllocationScenario;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyOverride;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyStandard;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioAllocation;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSignal;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSnapshot;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** An in-memory {@link SpacePlanningRepository}, for exercising the S158 services without a database. */
public class InMemorySpacePlanningRepository implements SpacePlanningRepository {

    private final Map<UUID, AllocationScenario> scenarios = new LinkedHashMap<>();
    private final Map<UUID, ScenarioAllocation> lines = new LinkedHashMap<>();
    private final Map<UUID, OccupancyStandard> standards = new LinkedHashMap<>();
    private final Map<UUID, OccupancyOverride> overrides = new LinkedHashMap<>();
    private final Map<UUID, UtilisationSnapshot> snapshots = new LinkedHashMap<>();
    private final Map<UUID, UtilisationSignal> signals = new LinkedHashMap<>();
    private final Map<UUID, SpaceChangeRequest> requests = new LinkedHashMap<>();
    private final AtomicLong planSequence = new AtomicLong();
    private final AtomicLong requestSequence = new AtomicLong();

    // ---- scenarios ------------------------------------------------------------------------------

    @Override
    public AllocationScenario saveScenario(AllocationScenario scenario) {
        scenarios.put(scenario.id(), scenario);
        return scenario;
    }

    @Override
    public Optional<AllocationScenario> findScenario(UUID id) {
        return Optional.ofNullable(scenarios.get(id));
    }

    @Override
    public List<AllocationScenario> findScenarios(String siteCode, ScenarioStatus status) {
        return scenarios.values().stream()
                .filter(s -> s.siteCode().equals(siteCode) && (status == null || s.status() == status))
                .sorted(Comparator.comparing(AllocationScenario::planReference)
                        .thenComparing(AllocationScenario::versionNumber))
                .toList();
    }

    @Override
    public int maxVersionOfPlan(String planReference) {
        return scenarios.values().stream().filter(s -> s.planReference().equals(planReference))
                .mapToInt(AllocationScenario::versionNumber).max().orElse(0);
    }

    @Override
    public long nextPlanNumber() {
        return planSequence.incrementAndGet();
    }

    @Override
    public List<ScenarioAllocation> findLines(UUID scenarioId) {
        return lines.values().stream().filter(l -> l.scenarioId().equals(scenarioId))
                .sorted(Comparator.comparing(ScenarioAllocation::roomCode)).toList();
    }

    @Override
    public ScenarioAllocation saveLine(ScenarioAllocation line) {
        lines.put(line.id(), line);
        return line;
    }

    @Override
    public void deleteLinesForRoom(UUID scenarioId, UUID roomId) {
        lines.values().removeIf(l -> l.scenarioId().equals(scenarioId) && l.roomId().equals(roomId));
    }

    // ---- standards and overrides ----------------------------------------------------------------

    @Override
    public OccupancyStandard saveStandard(OccupancyStandard standard) {
        standards.put(standard.id(), standard);
        return standard;
    }

    @Override
    public Optional<OccupancyStandard> findActiveStandard(String siteCode, SpaceType spaceType) {
        return standards.values().stream()
                .filter(s -> s.siteCode().equals(siteCode) && s.spaceType() == spaceType && s.isActive())
                .findFirst();
    }

    @Override
    public List<OccupancyStandard> findStandards(String siteCode) {
        return standards.values().stream().filter(s -> s.siteCode().equals(siteCode))
                .sorted(Comparator.comparing((OccupancyStandard s) -> s.spaceType().name())
                        .thenComparing(Comparator.comparingInt(OccupancyStandard::versionNumber).reversed()))
                .toList();
    }

    @Override
    public int maxStandardVersion(String siteCode, SpaceType spaceType) {
        return standards.values().stream()
                .filter(s -> s.siteCode().equals(siteCode) && s.spaceType() == spaceType)
                .mapToInt(OccupancyStandard::versionNumber).max().orElse(0);
    }

    @Override
    public OccupancyOverride saveOverride(OccupancyOverride override) {
        overrides.put(override.id(), override);
        return override;
    }

    @Override
    public Optional<OccupancyOverride> findOverride(UUID id) {
        return Optional.ofNullable(overrides.get(id));
    }

    @Override
    public List<OccupancyOverride> findOverrides(UUID scenarioId) {
        return overrides.values().stream().filter(o -> o.scenarioId().equals(scenarioId))
                .sorted(Comparator.comparing(OccupancyOverride::requestedAt)).toList();
    }

    @Override
    public List<OccupancyOverride> findLiveOverrides(Collection<UUID> scenarioIds) {
        return overrides.values().stream()
                .filter(o -> scenarioIds.contains(o.scenarioId()) && o.isLive())
                .toList();
    }

    // ---- utilisation ----------------------------------------------------------------------------

    @Override
    public UtilisationSnapshot saveSnapshot(UtilisationSnapshot snapshot) {
        snapshots.put(snapshot.id(), snapshot);
        return snapshot;
    }

    @Override
    public Optional<UtilisationSnapshot> findSnapshot(UUID roomId, Instant periodStart, Instant periodEnd) {
        return snapshots.values().stream()
                .filter(s -> s.roomId().equals(roomId) && s.periodStart().equals(periodStart)
                        && s.periodEnd().equals(periodEnd))
                .findFirst();
    }

    @Override
    public List<UtilisationSnapshot> findRecentSnapshots(UUID roomId, Instant upToPeriodEnd, int limit) {
        return snapshots.values().stream()
                .filter(s -> s.roomId().equals(roomId) && !s.periodEnd().isAfter(upToPeriodEnd))
                .sorted(Comparator.comparing(UtilisationSnapshot::periodEnd).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public List<UtilisationSnapshot> findLatestSnapshots(String siteCode) {
        Optional<Instant> latest = snapshots.values().stream().filter(s -> s.siteCode().equals(siteCode))
                .map(UtilisationSnapshot::periodEnd).max(Comparator.naturalOrder());
        if (latest.isEmpty()) {
            return List.of();
        }
        return snapshots.values().stream()
                .filter(s -> s.siteCode().equals(siteCode) && s.periodEnd().equals(latest.get()))
                .sorted(Comparator.comparing(UtilisationSnapshot::roomCode)).toList();
    }

    @Override
    public UtilisationSignal saveSignal(UtilisationSignal signal) {
        signals.put(signal.id(), signal);
        return signal;
    }

    @Override
    public Optional<UtilisationSignal> findActiveSignal(UUID roomId, UtilisationSignal.Kind kind) {
        return signals.values().stream()
                .filter(s -> s.roomId().equals(roomId) && s.kind() == kind && s.isActive())
                .findFirst();
    }

    @Override
    public List<UtilisationSignal> findSignals(String siteCode, boolean activeOnly) {
        return signals.values().stream()
                .filter(s -> s.siteCode().equals(siteCode) && (!activeOnly || s.isActive()))
                .sorted(Comparator.comparing(UtilisationSignal::roomCode)).toList();
    }

    // ---- space-change requests ------------------------------------------------------------------

    @Override
    public SpaceChangeRequest saveRequest(SpaceChangeRequest request) {
        requests.put(request.id(), request);
        return request;
    }

    @Override
    public Optional<SpaceChangeRequest> findRequest(UUID id) {
        return Optional.ofNullable(requests.get(id));
    }

    @Override
    public List<SpaceChangeRequest> findRequests(String siteCode, String requestedBy, SpaceChangeRequest.Status status) {
        return requests.values().stream()
                .filter(r -> r.siteCode().equals(siteCode))
                .filter(r -> requestedBy == null || requestedBy.equals(r.requestedBy()))
                .filter(r -> status == null || r.status() == status)
                .sorted(Comparator.comparing(SpaceChangeRequest::requestedAt).reversed())
                .toList();
    }

    @Override
    public long nextRequestNumber() {
        return requestSequence.incrementAndGet();
    }
}
