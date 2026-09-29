package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.facilities.masterdata.application.ports.SpaceAllocationRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** An in-memory S152 allocation register, for exercising {@code SpaceAllocationService} without a database. */
public class InMemorySpaceAllocationRepository implements SpaceAllocationRepository {

    private final List<SpaceAllocation> allocations = new ArrayList<>();

    @Override
    public SpaceAllocation save(SpaceAllocation allocation) {
        allocations.removeIf(existing -> existing.id().equals(allocation.id()));
        allocations.add(allocation);
        return allocation;
    }

    @Override
    public List<SpaceAllocation> findCurrentForSite(String siteCode) {
        return allocations.stream()
                .filter(a -> a.isCurrent() && a.siteCode().equals(siteCode))
                .sorted((a, b) -> {
                    int byRoom = a.roomCode().compareTo(b.roomCode());
                    return byRoom != 0 ? byRoom : a.allocatedUnit().compareTo(b.allocatedUnit());
                })
                .toList();
    }

    @Override
    public List<SpaceAllocation> findCurrentForRoom(UUID roomId) {
        return allocations.stream().filter(a -> a.isCurrent() && a.roomId().equals(roomId))
                .sorted((a, b) -> a.allocatedUnit().compareTo(b.allocatedUnit())).toList();
    }

    @Override
    public List<SpaceAllocation> findCurrentFromScenario(UUID scenarioId) {
        return allocations.stream()
                .filter(a -> a.isCurrent() && a.sourceScenarioId().equals(scenarioId))
                .sorted((a, b) -> a.roomCode().compareTo(b.roomCode())).toList();
    }

    @Override
    public boolean anyTouchedByScenario(UUID scenarioId) {
        return allocations.stream().anyMatch(a -> a.sourceScenarioId().equals(scenarioId)
                || scenarioId.equals(a.endedByScenarioId()));
    }
}
