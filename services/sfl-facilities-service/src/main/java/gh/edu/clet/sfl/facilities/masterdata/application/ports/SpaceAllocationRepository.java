package gh.edu.clet.sfl.facilities.masterdata.application.ports;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for the S152 current-state allocation register - see {@link SpaceAllocation}.
 *
 * <p>A separate port from {@link FacilitiesRepository} on purpose: the register arrived with S158 and is
 * written by exactly one use case ({@code SpaceAllocationService.applyCommittedChange}), and folding it
 * into the estate port would make every existing S152 adapter and double implement methods nothing in
 * S152 itself calls.
 */
public interface SpaceAllocationRepository {

    SpaceAllocation save(SpaceAllocation allocation);

    /** Current (un-ended) allocations at a site, in room-code then unit order. Site-filtered in SQL. */
    List<SpaceAllocation> findCurrentForSite(String siteCode);

    List<SpaceAllocation> findCurrentForRoom(UUID roomId);

    /** Current allocations that a given committed scenario put in place. */
    List<SpaceAllocation> findCurrentFromScenario(UUID scenarioId);

    /**
     * Whether a scenario has already been applied - it started or ended at least one row.
     *
     * <p>Both halves matter: a scenario that only vacates rooms inserts nothing, and would otherwise
     * look unapplied and be applied twice.
     */
    boolean anyTouchedByScenario(UUID scenarioId);
}
