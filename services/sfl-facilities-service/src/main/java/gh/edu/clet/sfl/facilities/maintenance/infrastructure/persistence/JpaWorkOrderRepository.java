package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaWorkOrderRepository extends JpaRepository<WorkOrderRecord, UUID> {

    Optional<WorkOrderRecord> findByFacilityFaultId(UUID facilityFaultId);

    @Query("""
            select w from WorkOrderRecord w
            where (:siteCode is null or w.siteCode = :siteCode)
              and (:roomId is null or w.roomId = :roomId)
              and (:assetId is null or w.assetId = :assetId)
              and (:status is null or w.status = :status)
              and (:assignedTo is null or w.assignedTo = :assignedTo)
              and (:vendorId is null or w.vendorId = :vendorId)
              and (:openOnly is null or :openOnly = false
                   or w.status not in (gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.CLOSED,
                                       gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.CANCELLED))
            order by w.metadata.createdAt desc
            """)
    List<WorkOrderRecord> search(@Param("siteCode") String siteCode,
            @Param("roomId") UUID roomId,
            @Param("assetId") UUID assetId,
            @Param("status") WorkOrderStatus status,
            @Param("assignedTo") String assignedTo,
            @Param("vendorId") UUID vendorId,
            @Param("openOnly") Boolean openOnly,
            Pageable pageable);

    /**
     * Work orders past their SLA and still accruing against it.
     *
     * <p>{@code COMPLETED} is excluded, matching {@link WorkOrderStatus#accruesSla()}: the assignee
     * has finished and the order is waiting on a verifier, so escalating the assignee would be
     * chasing the wrong person.
     */
    @Query("""
            select w from WorkOrderRecord w
            where w.slaDueAt is not null
              and w.slaDueAt < :asOf
              and w.status not in (gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.CLOSED,
                                   gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.CANCELLED,
                                   gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.COMPLETED)
            order by w.slaDueAt asc
            """)
    List<WorkOrderRecord> findOverdue(@Param("asOf") Instant asOf, Pageable pageable);

    @Query("""
            select count(w) from WorkOrderRecord w
            where w.siteCode = :siteCode
              and w.status not in (gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.CLOSED,
                                   gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.CANCELLED)
            """)
    long countOpenForSite(@Param("siteCode") String siteCode);

    @Query("""
            select count(w) from WorkOrderRecord w
            where w.siteCode = :siteCode
              and w.slaDueAt is not null
              and w.slaDueAt < :asOf
              and w.status not in (gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.CLOSED,
                                   gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.CANCELLED,
                                   gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus.COMPLETED)
            """)
    long countOverdueForSite(@Param("siteCode") String siteCode, @Param("asOf") Instant asOf);

    /** See {@code JpaFacilityFaultRepository.nextFaultSequence} for why this is a sequence. */
    @Query(value = "select nextval('facilities.work_order_number_seq')", nativeQuery = true)
    long nextWorkOrderSequence();
}
