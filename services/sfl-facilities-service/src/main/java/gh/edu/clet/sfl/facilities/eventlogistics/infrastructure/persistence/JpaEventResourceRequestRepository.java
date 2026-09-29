package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEventResourceRequestRepository extends JpaRepository<EventResourceRequestRecord, UUID> {

    @Query("""
            select r from EventResourceRequestRecord r where r.setupTaskId = :taskId
            order by r.metadata.createdAt asc, r.id asc
            """)
    List<EventResourceRequestRecord> findForTask(@Param("taskId") UUID taskId);

    @Query("""
            select r from EventResourceRequestRecord r
            where r.externalReference = :bookingId or r.externalParentReference = :bookingId
            """)
    List<EventResourceRequestRecord> findForBooking(@Param("bookingId") String bookingId);

    @Query("""
            select r from EventResourceRequestRecord r
            where r.externalReference is not null
              and r.status in (gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus.REQUESTED,
                               gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus.CONFIRMED)
            order by r.neededFrom asc
            """)
    List<EventResourceRequestRecord> findRoutedLive(Pageable pageable);
}
