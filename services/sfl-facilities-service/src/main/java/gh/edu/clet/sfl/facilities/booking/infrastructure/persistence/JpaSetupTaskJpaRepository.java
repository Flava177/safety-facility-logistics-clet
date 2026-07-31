package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaSetupTaskJpaRepository extends JpaRepository<SetupTaskRecord, UUID> {

    List<SetupTaskRecord> findByBookingIdOrderByDueByAsc(UUID bookingId);

    /**
     * The turnaround queue, ordered by when the room is needed.
     *
     * <p>Not by when the task was raised. A task for this afternoon matters more than one raised last
     * week for next month, and a created-at ordering gets that backwards every time.
     */
    @Query("""
            select t from SetupTaskRecord t
            where (:siteCode is null or t.siteCode = :siteCode)
              and t.status = gh.edu.clet.sfl.facilities.booking.domain.SetupTaskStatus.PENDING
              and t.dueBy < :dueBefore
            order by t.dueBy asc
            """)
    List<SetupTaskRecord> findPending(@Param("siteCode") String siteCode,
            @Param("dueBefore") Instant dueBefore, Pageable pageable);
}
