package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

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
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * The one adapter behind {@link SpacePlanningRepository}.
 *
 * <p>Every save reuses the managed row when one exists, for the reason {@code VersionedRecord} gives: a
 * detached merge of a domain object whose version has already moved fails every update, not just stale
 * ones. {@code saveAndFlush} so a constraint violation (the one-active-signal index, the approver check)
 * surfaces inside the calling service rather than at commit.
 */
@Repository
public class JpaSpacePlanningRepositoryAdapter implements SpacePlanningRepository {

    private final JpaSpaceScenarioJpaRepository scenarios;
    private final JpaScenarioAllocationJpaRepository lines;
    private final JpaOccupancyStandardJpaRepository standards;
    private final JpaOccupancyOverrideJpaRepository overrides;
    private final JpaUtilisationSnapshotJpaRepository snapshots;
    private final JpaUtilisationSignalJpaRepository signals;
    private final JpaSpaceChangeRequestJpaRepository requests;

    public JpaSpacePlanningRepositoryAdapter(JpaSpaceScenarioJpaRepository scenarios,
            JpaScenarioAllocationJpaRepository lines, JpaOccupancyStandardJpaRepository standards,
            JpaOccupancyOverrideJpaRepository overrides, JpaUtilisationSnapshotJpaRepository snapshots,
            JpaUtilisationSignalJpaRepository signals, JpaSpaceChangeRequestJpaRepository requests) {
        this.scenarios = scenarios;
        this.lines = lines;
        this.standards = standards;
        this.overrides = overrides;
        this.snapshots = snapshots;
        this.signals = signals;
        this.requests = requests;
    }

    private static <R extends VersionedRecord> R upsert(JpaRepository<R, UUID> repository, UUID id, long version,
            Supplier<R> fresh, Consumer<R> apply) {
        Optional<R> existing = repository.findById(id);
        existing.ifPresent(record -> record.requireNotStale(version));
        R record = existing.orElseGet(fresh);
        apply.accept(record);
        return repository.saveAndFlush(record);
    }

    // ---- scenarios ------------------------------------------------------------------------------

    @Override
    public AllocationScenario saveScenario(AllocationScenario scenario) {
        return upsert(scenarios, scenario.id(), scenario.metadata().version(), SpaceScenarioRecord::new,
                record -> record.apply(scenario)).toDomain();
    }

    @Override
    public Optional<AllocationScenario> findScenario(UUID id) {
        return scenarios.findById(id).map(SpaceScenarioRecord::toDomain);
    }

    @Override
    public List<AllocationScenario> findScenarios(String siteCode, ScenarioStatus status) {
        return scenarios.search(siteCode, status).stream().map(SpaceScenarioRecord::toDomain).toList();
    }

    @Override
    public int maxVersionOfPlan(String planReference) {
        return scenarios.maxVersion(planReference);
    }

    @Override
    public long nextPlanNumber() {
        return scenarios.nextPlanNumber();
    }

    @Override
    public List<ScenarioAllocation> findLines(UUID scenarioId) {
        return lines.findByScenarioIdOrderByRoomCodeAscAllocatedUnitAsc(scenarioId).stream()
                .map(ScenarioAllocationRecord::toDomain).toList();
    }

    @Override
    public ScenarioAllocation saveLine(ScenarioAllocation line) {
        return upsert(lines, line.id(), line.metadata().version(), ScenarioAllocationRecord::new,
                record -> record.apply(line)).toDomain();
    }

    @Override
    public void deleteLinesForRoom(UUID scenarioId, UUID roomId) {
        lines.deleteForRoom(scenarioId, roomId);
    }

    // ---- standards and overrides ----------------------------------------------------------------

    @Override
    public OccupancyStandard saveStandard(OccupancyStandard standard) {
        return upsert(standards, standard.id(), standard.metadata().version(), OccupancyStandardRecord::new,
                record -> record.apply(standard)).toDomain();
    }

    @Override
    public Optional<OccupancyStandard> findActiveStandard(String siteCode, SpaceType spaceType) {
        return standards.findActive(siteCode, spaceType).map(OccupancyStandardRecord::toDomain);
    }

    @Override
    public List<OccupancyStandard> findStandards(String siteCode) {
        return standards.findBySiteCodeOrderBySpaceTypeAscVersionNumberDesc(siteCode).stream()
                .map(OccupancyStandardRecord::toDomain).toList();
    }

    @Override
    public int maxStandardVersion(String siteCode, SpaceType spaceType) {
        return standards.maxVersion(siteCode, spaceType);
    }

    @Override
    public OccupancyOverride saveOverride(OccupancyOverride override) {
        return upsert(overrides, override.id(), override.metadata().version(), OccupancyOverrideRecord::new,
                record -> record.apply(override)).toDomain();
    }

    @Override
    public Optional<OccupancyOverride> findOverride(UUID id) {
        return overrides.findById(id).map(OccupancyOverrideRecord::toDomain);
    }

    @Override
    public List<OccupancyOverride> findOverrides(UUID scenarioId) {
        return overrides.findByScenarioIdOrderByRequestedAtAsc(scenarioId).stream()
                .map(OccupancyOverrideRecord::toDomain).toList();
    }

    @Override
    public List<OccupancyOverride> findLiveOverrides(Collection<UUID> scenarioIds) {
        if (scenarioIds.isEmpty()) {
            return List.of();
        }
        return overrides.findByScenarioIdInAndStatusNot(scenarioIds, OccupancyOverride.OverrideStatus.WITHDRAWN)
                .stream().map(OccupancyOverrideRecord::toDomain).toList();
    }

    // ---- utilisation ----------------------------------------------------------------------------

    @Override
    public UtilisationSnapshot saveSnapshot(UtilisationSnapshot snapshot) {
        return upsert(snapshots, snapshot.id(), snapshot.metadata().version(), UtilisationSnapshotRecord::new,
                record -> record.apply(snapshot)).toDomain();
    }

    @Override
    public Optional<UtilisationSnapshot> findSnapshot(UUID roomId, Instant periodStart, Instant periodEnd) {
        return snapshots.findByRoomIdAndPeriodStartAndPeriodEnd(roomId, periodStart, periodEnd)
                .map(UtilisationSnapshotRecord::toDomain);
    }

    @Override
    public List<UtilisationSnapshot> findRecentSnapshots(UUID roomId, Instant upToPeriodEnd, int limit) {
        return snapshots.recent(roomId, upToPeriodEnd, PageRequest.of(0, Math.max(1, limit))).stream()
                .map(UtilisationSnapshotRecord::toDomain).toList();
    }

    @Override
    public List<UtilisationSnapshot> findLatestSnapshots(String siteCode) {
        return snapshots.latest(siteCode).stream().map(UtilisationSnapshotRecord::toDomain).toList();
    }

    @Override
    public UtilisationSignal saveSignal(UtilisationSignal signal) {
        return upsert(signals, signal.id(), signal.metadata().version(), UtilisationSignalRecord::new,
                record -> record.apply(signal)).toDomain();
    }

    @Override
    public Optional<UtilisationSignal> findActiveSignal(UUID roomId, UtilisationSignal.Kind kind) {
        return signals.findByRoomIdAndKindAndStatus(roomId, kind, UtilisationSignal.Status.ACTIVE)
                .map(UtilisationSignalRecord::toDomain);
    }

    @Override
    public List<UtilisationSignal> findSignals(String siteCode, boolean activeOnly) {
        List<UtilisationSignalRecord> found = activeOnly
                ? signals.findBySiteCodeAndStatusOrderByRoomCodeAsc(siteCode, UtilisationSignal.Status.ACTIVE)
                : signals.findBySiteCodeOrderByRaisedAtDesc(siteCode);
        return found.stream().map(UtilisationSignalRecord::toDomain).toList();
    }

    // ---- space-change requests ------------------------------------------------------------------

    @Override
    public SpaceChangeRequest saveRequest(SpaceChangeRequest request) {
        return upsert(requests, request.id(), request.metadata().version(), SpaceChangeRequestRecord::new,
                record -> record.apply(request)).toDomain();
    }

    @Override
    public Optional<SpaceChangeRequest> findRequest(UUID id) {
        return requests.findById(id).map(SpaceChangeRequestRecord::toDomain);
    }

    @Override
    public List<SpaceChangeRequest> findRequests(String siteCode, String requestedBy,
            SpaceChangeRequest.Status status) {
        return requests.search(siteCode, requestedBy, status).stream().map(SpaceChangeRequestRecord::toDomain)
                .toList();
    }

    @Override
    public long nextRequestNumber() {
        return scenarios.nextRequestNumber();
    }
}
