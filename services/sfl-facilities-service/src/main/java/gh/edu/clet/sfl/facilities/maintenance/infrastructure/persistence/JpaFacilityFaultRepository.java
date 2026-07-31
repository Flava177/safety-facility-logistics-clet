package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaFacilityFaultRepository extends JpaRepository<FacilityFaultRecord, UUID> {

    Optional<FacilityFaultRecord> findByFaultNumber(String faultNumber);

    /**
     * The filtered search behind {@code GET /faults}.
     *
     * <p>Every filter is optional and null means "do not filter", which keeps one query where six
     * derived methods would otherwise appear. The open-status list is inlined rather than passed,
     * because {@code FacilityFaultStatus.isOpen()} is the definition and duplicating it as a
     * parameter would let the two drift.
     */
    @Query("""
            select f from FacilityFaultRecord f
            where (:siteCode is null or f.siteCode = :siteCode)
              and (:roomId is null or f.roomId = :roomId)
              and (:status is null or f.status = :status)
              and (:reportedBy is null or f.reportedBy = :reportedBy)
              and (:openOnly is null or :openOnly = false
                   or f.status in (gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus.REPORTED,
                                   gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus.TRIAGED,
                                   gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus.WORK_ORDER_CREATED))
            order by f.reportedAt desc
            """)
    List<FacilityFaultRecord> search(@Param("siteCode") String siteCode,
            @Param("roomId") UUID roomId,
            @Param("status") FacilityFaultStatus status,
            @Param("reportedBy") String reportedBy,
            @Param("openOnly") Boolean openOnly,
            Pageable pageable);

    /** Open faults past their SLA, oldest deadline first so the worst is escalated even if truncated. */
    @Query("""
            select f from FacilityFaultRecord f
            where f.slaDueAt is not null
              and f.slaDueAt < :asOf
              and f.status in (gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus.REPORTED,
                              gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus.TRIAGED,
                              gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus.WORK_ORDER_CREATED)
            order by f.slaDueAt asc
            """)
    List<FacilityFaultRecord> findOverdue(@Param("asOf") Instant asOf, Pageable pageable);

    @Query("""
            select count(f) from FacilityFaultRecord f
            where f.siteCode = :siteCode
              and f.status in (gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus.REPORTED,
                              gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus.TRIAGED,
                              gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus.WORK_ORDER_CREATED)
            """)
    long countOpenForSite(@Param("siteCode") String siteCode);

    /**
     * The next fault sequence, from a database sequence rather than a row count.
     *
     * <p>A count would be wrong twice over: two concurrent reports would read the same count and
     * allocate the same number, and a dismissed fault that was later archived would let a number be
     * reused. A sequence has neither problem, and it is monotonic even under rollback — a gap in
     * fault numbers is harmless, a duplicate is not.
     */
    @Query(value = "select nextval('facilities.fault_number_seq')", nativeQuery = true)
    long nextFaultSequence();
}
