package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data access to {@code facilities.bookings}.
 *
 * <p>Every overlap test is written {@code occupiedFrom < :to and :from < occupiedTo} — the half-open
 * rule, in the same shape as {@code BookingWindow.overlaps} and the {@code tstzrange(..., '[)')} in
 * the exclusion constraint. Three expressions of one rule, and they must not drift; a test in
 * {@code S159MandatoryScenariosTest} pins the boundary case where a booking ends exactly as the next
 * begins.
 */
public interface JpaBookingJpaRepository extends JpaRepository<BookingRecord, UUID> {

    Optional<BookingRecord> findByBookingReference(String bookingReference);

    @Query("""
            select b from BookingRecord b
            where b.roomId = :roomId
              and b.occupiedFrom < :to
              and :from < b.occupiedTo
              and b.status in (gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.REQUESTED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.CONFIRMED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.IN_USE)
              and (:excluding is null or b.id <> :excluding)
            order by b.startsAt asc
            """)
    List<BookingRecord> findHolding(@Param("roomId") UUID roomId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excluding") UUID excluding);

    @Query("""
            select distinct b.roomId from BookingRecord b
            where b.siteCode = :siteCode
              and b.occupiedFrom < :to
              and :from < b.occupiedTo
              and b.status in (gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.REQUESTED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.CONFIRMED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.IN_USE)
            """)
    List<UUID> findHeldRoomIds(@Param("siteCode") String siteCode,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * The booking search.
     *
     * <h2>Why {@code from} and {@code to} are not nullable here</h2>
     *
     * Every other optional filter is written {@code (:p is null or column = :p)}. The two temporal
     * bounds are not, and cannot be: PostgreSQL rejects the query outright with
     * <em>"could not determine data type of parameter $11"</em>.
     *
     * <p>The reason is that {@code :p is null} gives the planner no type to infer from, so the
     * placeholder's type has to come from the driver — and pgjdbc sends {@code UNSPECIFIED} when
     * Hibernate binds a null {@code Instant} as {@code TIMESTAMP_WITH_TIMEZONE}. String, UUID and
     * enum parameters carry a concrete OID and are fine, which is why the same idiom works
     * everywhere else in this file and in {@code JpaWorkOrderRepository}.
     *
     * <p>So the adapter substitutes wide sentinels for an unbounded search — see
     * {@code JpaBookingRepositoryAdapter.UNBOUNDED_FROM}. Found by running the service against real
     * PostgreSQL; no unit test can see it, because it is the driver and the planner disagreeing.
     */
    @Query("""
            select b from BookingRecord b
            where (:siteCode is null or b.siteCode = :siteCode)
              and (:roomId is null or b.roomId = :roomId)
              and (:status is null or b.status = :status)
              and (:purpose is null or b.purpose = :purpose)
              and (:requestedBy is null or b.requestedBy = :requestedBy)
              and b.occupiedTo > :from
              and b.occupiedFrom < :to
              and (:liveOnly is null or :liveOnly = false
                   or b.status in (gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.REQUESTED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.CONFIRMED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.IN_USE))
              and (:onHold is null
                   or (:onHold = true and b.readinessHoldReason is not null)
                   or (:onHold = false and b.readinessHoldReason is null))
            order by b.startsAt asc
            """)
    List<BookingRecord> search(@Param("siteCode") String siteCode,
            @Param("roomId") UUID roomId,
            @Param("status") BookingStatus status,
            @Param("purpose") BookingPurpose purpose,
            @Param("requestedBy") String requestedBy,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("liveOnly") Boolean liveOnly,
            @Param("onHold") Boolean onHold,
            Pageable pageable);

    @Query("""
            select b from BookingRecord b
            where b.occupiedTo > :from
              and b.status in (gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.REQUESTED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.CONFIRMED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.IN_USE)
            order by b.startsAt asc
            """)
    List<BookingRecord> findUpcoming(@Param("from") Instant from, Pageable pageable);

    @Query("""
            select b from BookingRecord b
            where b.roomId = :roomId
              and b.occupiedTo > :from
              and b.status in (gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.REQUESTED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.CONFIRMED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.IN_USE)
            order by b.startsAt asc
            """)
    List<BookingRecord> findUpcomingForRoom(@Param("roomId") UUID roomId, @Param("from") Instant from,
            Pageable pageable);

    /**
     * Confirmed bookings that should have started and did not.
     *
     * <p>The grace period is applied by the caller, not here: it is site-scoped runtime configuration
     * and one query cannot carry a different threshold per row. The candidate set stays small because
     * it drains as fast as it fills.
     */
    @Query("""
            select b from BookingRecord b
            where b.status = gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.CONFIRMED
              and b.startedAt is null
              and b.startsAt < :startedBefore
            order by b.startsAt asc
            """)
    List<BookingRecord> findNoShowCandidates(@Param("startedBefore") Instant startedBefore,
            Pageable pageable);

    @Query("""
            select count(b) from BookingRecord b
            where b.siteCode = :siteCode
              and b.occupiedTo > :asOf
              and b.status in (gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.REQUESTED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.CONFIRMED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.IN_USE)
            """)
    long countUpcoming(@Param("siteCode") String siteCode, @Param("asOf") Instant asOf);

    @Query("""
            select count(b) from BookingRecord b
            where b.siteCode = :siteCode
              and b.status = gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.REQUESTED
            """)
    long countAwaitingApproval(@Param("siteCode") String siteCode);

    @Query("""
            select count(b) from BookingRecord b
            where b.siteCode = :siteCode
              and b.readinessHoldReason is not null
              and b.occupiedTo > :asOf
              and b.status in (gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.REQUESTED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.CONFIRMED,
                                   gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.IN_USE)
            """)
    long countOnReadinessHold(@Param("siteCode") String siteCode, @Param("asOf") Instant asOf);

    /** See {@code JpaFacilityFaultRepository.nextFaultSequence} for why this is a sequence. */
    @Query(value = "select nextval('facilities.booking_reference_seq')", nativeQuery = true)
    long nextBookingSequence();

    /**
     * Takes a transaction-scoped advisory lock. See {@code BookingRepository.lockSpace} for why.
     *
     * <p>Wrapped in {@code select 1 from (...)} because {@code pg_advisory_xact_lock} returns SQL
     * {@code void}, which has no useful Java mapping. The subquery gives the statement a row to
     * return and the lock is taken either way.
     */
    @Query(value = "select 1 from (select pg_advisory_xact_lock(:key)) as acquired", nativeQuery = true)
    int acquireAdvisoryLock(@Param("key") long key);
}
