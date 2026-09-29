package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.application.ports.SpaceAllocationRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** The one adapter behind {@link SpaceAllocationRepository}. */
@Repository
public class JpaSpaceAllocationRepositoryAdapter implements SpaceAllocationRepository {

    private final JpaSpaceAllocationJpaRepository allocations;

    public JpaSpaceAllocationRepositoryAdapter(JpaSpaceAllocationJpaRepository allocations) {
        this.allocations = allocations;
    }

    /** Reuses the managed row on update - see {@code VersionedRecord} for why a detached merge fails. */
    @Override
    public SpaceAllocation save(SpaceAllocation allocation) {
        Optional<SpaceAllocationRecord> existing = allocations.findById(allocation.id());
        existing.ifPresent(record -> record.requireNotStale(allocation.metadata().version()));
        SpaceAllocationRecord record = existing.orElseGet(SpaceAllocationRecord::new);
        record.apply(allocation);
        return allocations.saveAndFlush(record).toDomain();
    }

    @Override
    public List<SpaceAllocation> findCurrentForSite(String siteCode) {
        return allocations.findBySiteCodeAndEndedAtIsNullOrderByRoomCodeAscAllocatedUnitAsc(siteCode).stream()
                .map(SpaceAllocationRecord::toDomain).toList();
    }

    @Override
    public List<SpaceAllocation> findCurrentForRoom(UUID roomId) {
        return allocations.findByRoomIdAndEndedAtIsNullOrderByAllocatedUnitAsc(roomId).stream()
                .map(SpaceAllocationRecord::toDomain).toList();
    }

    @Override
    public List<SpaceAllocation> findCurrentFromScenario(UUID scenarioId) {
        return allocations.findBySourceScenarioIdAndEndedAtIsNullOrderByRoomCodeAsc(scenarioId).stream()
                .map(SpaceAllocationRecord::toDomain).toList();
    }

    @Override
    public boolean anyTouchedByScenario(UUID scenarioId) {
        return allocations.existsBySourceScenarioIdOrEndedByScenarioId(scenarioId, scenarioId);
    }
}
