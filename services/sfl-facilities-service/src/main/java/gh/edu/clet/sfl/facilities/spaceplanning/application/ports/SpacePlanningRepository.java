package gh.edu.clet.sfl.facilities.spaceplanning.application.ports;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for every S158 aggregate. One port for the module, as {@code BookingRepository} is for
 * S159: the aggregates are small and always read together (a scenario with its lines and overrides).
 *
 * <p>Every list method takes a site and filters by it in SQL; the application layer filters again
 * through {@code FacilitiesAuthorization.filterBySite}, and RLS narrows underneath both.
 */
public interface SpacePlanningRepository {

    // ---- scenarios ------------------------------------------------------------------------------

    AllocationScenario saveScenario(AllocationScenario scenario);

    Optional<AllocationScenario> findScenario(UUID id);

    /** @param status {@code null} for every status */
    List<AllocationScenario> findScenarios(String siteCode, ScenarioStatus status);

    /** The highest version number in a plan lineage, for revising. */
    int maxVersionOfPlan(String planReference);

    /** The next number for a plan reference ({@code SP-<site>-000001}). */
    long nextPlanNumber();

    List<ScenarioAllocation> findLines(UUID scenarioId);

    ScenarioAllocation saveLine(ScenarioAllocation line);

    void deleteLinesForRoom(UUID scenarioId, UUID roomId);

    // ---- standards and overrides ----------------------------------------------------------------

    OccupancyStandard saveStandard(OccupancyStandard standard);

    Optional<OccupancyStandard> findActiveStandard(String siteCode, SpaceType spaceType);

    /** Every version at a site, newest first within each type. */
    List<OccupancyStandard> findStandards(String siteCode);

    int maxStandardVersion(String siteCode, SpaceType spaceType);

    OccupancyOverride saveOverride(OccupancyOverride override);

    Optional<OccupancyOverride> findOverride(UUID id);

    List<OccupancyOverride> findOverrides(UUID scenarioId);

    /** Overrides not withdrawn, across the given scenarios - for the register's compliance view. */
    List<OccupancyOverride> findLiveOverrides(Collection<UUID> scenarioIds);

    // ---- utilisation ----------------------------------------------------------------------------

    UtilisationSnapshot saveSnapshot(UtilisationSnapshot snapshot);

    Optional<UtilisationSnapshot> findSnapshot(UUID roomId, Instant periodStart, Instant periodEnd);

    /**
     * A room's snapshots for periods ending at or before {@code upToPeriodEnd}, newest first, at most
     * {@code limit} - so re-running an old period judges persistence as of that period, not as of today.
     */
    List<UtilisationSnapshot> findRecentSnapshots(UUID roomId, Instant upToPeriodEnd, int limit);

    /** Every snapshot for a site's most recent evaluated period. */
    List<UtilisationSnapshot> findLatestSnapshots(String siteCode);

    UtilisationSignal saveSignal(UtilisationSignal signal);

    Optional<UtilisationSignal> findActiveSignal(UUID roomId, UtilisationSignal.Kind kind);

    /** @param activeOnly {@code true} for the signal list the dashboard and the AC read */
    List<UtilisationSignal> findSignals(String siteCode, boolean activeOnly);

    // ---- space-change requests ------------------------------------------------------------------

    SpaceChangeRequest saveRequest(SpaceChangeRequest request);

    Optional<SpaceChangeRequest> findRequest(UUID id);

    /**
     * @param requestedBy narrows to one requester's own; {@code null} for all
     * @param status {@code null} for every status
     */
    List<SpaceChangeRequest> findRequests(String siteCode, String requestedBy, SpaceChangeRequest.Status status);

    /** The next number for a request reference ({@code SCR-<site>-000001}). */
    long nextRequestNumber();
}
