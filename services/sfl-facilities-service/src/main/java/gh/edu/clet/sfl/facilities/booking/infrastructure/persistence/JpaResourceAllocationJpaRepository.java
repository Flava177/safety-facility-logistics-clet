package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaResourceAllocationJpaRepository extends JpaRepository<ResourceAllocationRecord, UUID> {

    List<ResourceAllocationRecord> findByBookingIdOrderByAllocatedAtAsc(UUID bookingId);

    /** Unreleased allocations of these resources overlapping the half-open interval. */
    @Query("""
            select a from ResourceAllocationRecord a
            where a.resourceId in :resourceIds
              and a.releasedWithBooking = false
              and a.occupiedFrom < :to
              and :from < a.occupiedTo
            order by a.startsAt asc
            """)
    List<ResourceAllocationRecord> findLive(@Param("resourceIds") Collection<UUID> resourceIds,
            @Param("from") Instant from, @Param("to") Instant to);
}
