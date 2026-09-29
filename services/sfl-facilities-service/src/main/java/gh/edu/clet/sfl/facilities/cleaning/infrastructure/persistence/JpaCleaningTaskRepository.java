package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code facilities.cleaning_tasks}. Temporal bounds are never null; see the adapter. */
public interface JpaCleaningTaskRepository extends JpaRepository<CleaningTaskRecord, UUID> {

    @Query("""
            select count(t) > 0 from CleaningTaskRecord t
             where t.scheduleId = :scheduleId and t.roomId = :roomId and t.occurrenceStart = :occurrenceStart
            """)
    boolean existsOccurrence(@Param("scheduleId") UUID scheduleId, @Param("roomId") UUID roomId,
            @Param("occurrenceStart") Instant occurrenceStart);

    @Query("select t from CleaningTaskRecord t where t.bookingId = :bookingId order by t.windowStart asc")
    List<CleaningTaskRecord> findForBooking(@Param("bookingId") UUID bookingId);

    @Query("""
            select t from CleaningTaskRecord t
             where (:siteCode is null or t.siteCode = :siteCode)
               and (:roomId is null or t.roomId = :roomId)
               and (:status is null or t.status = :status)
               and (:origin is null or t.origin = :origin)
               and (:requestedBy is null or t.requestedBy = :requestedBy)
               and (:assignedTo is null or t.assignedTo = :assignedTo)
               and (:bookingId is null or t.bookingId = :bookingId)
               and t.windowStart >= :from and t.windowStart < :to
             order by t.windowStart asc, t.taskNumber asc
            """)
    Page<CleaningTaskRecord> search(@Param("siteCode") String siteCode, @Param("roomId") UUID roomId,
            @Param("status") TaskStatus status, @Param("origin") TaskOrigin origin,
            @Param("requestedBy") String requestedBy, @Param("assignedTo") String assignedTo,
            @Param("bookingId") UUID bookingId, @Param("from") Instant from, @Param("to") Instant to,
            Pageable pageable);

    @Query("""
            select t from CleaningTaskRecord t
             where t.siteCode = :siteCode and t.status in :statuses
               and t.windowStart < :to and t.dueBy > :from
             order by t.windowStart asc, t.taskNumber asc
            """)
    List<CleaningTaskRecord> findOverlapping(@Param("siteCode") String siteCode,
            @Param("statuses") Collection<TaskStatus> statuses, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select t from CleaningTaskRecord t
             where t.origin = gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin.REACTIVE
               and t.assigneeType = gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType.VENDOR
               and t.status = gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus.ASSIGNED
               and t.startedAt is null
             order by t.requestedAt asc
            """)
    List<CleaningTaskRecord> findUnstartedReactiveVendor(Pageable pageable);

    @Query("""
            select t from CleaningTaskRecord t
             where t.siteCode = :siteCode and t.windowStart >= :from and t.windowStart < :to
             order by t.windowStart asc
            """)
    List<CleaningTaskRecord> findStartingBetween(@Param("siteCode") String siteCode, @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select t from CleaningTaskRecord t
             where t.siteCode = :siteCode
               and t.origin = gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin.REACTIVE
               and t.status in :statuses and t.dueBy < :now
             order by t.dueBy asc
            """)
    List<CleaningTaskRecord> findOverdueReactive(@Param("siteCode") String siteCode,
            @Param("statuses") Collection<TaskStatus> statuses, @Param("now") Instant now, Pageable pageable);

    @Query("""
            select t from CleaningTaskRecord t
             where t.vendorId = :vendorId and t.requestedAt >= :from and t.requestedAt < :to
             order by t.requestedAt asc
            """)
    List<CleaningTaskRecord> findVendorTasks(@Param("vendorId") UUID vendorId, @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(value = "select nextval('facilities.cleaning_task_number_seq')", nativeQuery = true)
    long nextTaskSequence();

    /** Transaction-scoped advisory lock with a bounded wait - the pattern of JpaBookingJpaRepository. */
    @Query(value = """
            select 1 from (
                select set_config('lock_timeout', (:timeoutMillis)::text || 'ms', true),
                       pg_advisory_xact_lock(:key)
            ) as acquired
            """, nativeQuery = true)
    int acquireAdvisoryLock(@Param("key") long key, @Param("timeoutMillis") long timeoutMillis);
}
