package gh.edu.clet.sfl.facilities.maintenance.application.ports;

import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceEvidence;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceVendor;
import gh.edu.clet.sfl.facilities.maintenance.domain.PreventiveMaintenanceSchedule;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderPart;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything S153 stores, behind one port.
 *
 * <p>One port rather than five, mirroring S152's {@code FacilitiesRepository}. The five aggregates
 * here are read together constantly — a work order needs its fault, its parts and its evidence, and
 * closing it touches the schedule that raised it — so splitting them would produce five interfaces
 * that every service injects all of, plus five adapters that all wrap the same transaction.
 *
 * <p>The application layer sees only this. The previous version of these services named JPA types
 * directly, which is the debt the ArchUnit test recorded as "excluded, out of scope"; this round
 * removes both the debt and the exclusion.
 */
public interface MaintenanceRepository {

    // ---- faults -----------------------------------------------------------------------------

    FacilityFault saveFault(FacilityFault fault);

    Optional<FacilityFault> findFault(UUID id);

    Optional<FacilityFault> findFaultByNumber(String faultNumber);

    /** Filtered search. Null filters are ignored; the caller applies its own site-scope filter. */
    List<FacilityFault> findFaults(String siteCode, UUID roomId, FacilityFaultStatus status, Boolean openOnly,
            String reportedBy, int limit);

    /** Open faults whose SLA has passed, for the escalation evaluator. */
    List<FacilityFault> findOverdueFaults(Instant asOf, int limit);

    /** The next fault number for a site, allocated by the store so two callers cannot collide. */
    String nextFaultNumber(String siteCode);

    // ---- work orders ------------------------------------------------------------------------

    WorkOrder saveWorkOrder(WorkOrder workOrder);

    Optional<WorkOrder> findWorkOrder(UUID id);

    Optional<WorkOrder> findWorkOrderForFault(UUID faultId);

    List<WorkOrder> findWorkOrders(String siteCode, UUID roomId, UUID assetId, WorkOrderStatus status,
            String assignedTo, UUID vendorId, Boolean openOnly, int limit);

    List<WorkOrder> findOverdueWorkOrders(Instant asOf, int limit);

    /** Work past its response deadline that nobody has started and nobody has yet been told about. */
    List<WorkOrder> findResponseBreaches(Instant asOf, int limit);

    /**
     * Evidence that is not held and not already disposed of, oldest first.
     *
     * <p>Eligibility itself is decided in the domain by {@link MaintenanceEvidence#isDisposalEligible},
     * because the retention arithmetic is a rule rather than a query. This narrows the candidates to
     * the rows that could possibly qualify so the sweep does not read the whole table to find them.
     */
    List<MaintenanceEvidence> findDisposalCandidates(int limit);

    String nextWorkOrderNumber(String siteCode);

    // ---- parts and evidence -------------------------------------------------------------------

    WorkOrderPart savePart(WorkOrderPart part);

    List<WorkOrderPart> findParts(UUID workOrderId);

    void deletePart(UUID partId);

    MaintenanceEvidence saveEvidence(MaintenanceEvidence evidence);

    Optional<MaintenanceEvidence> findEvidence(UUID id);

    List<MaintenanceEvidence> findEvidenceFor(UUID workOrderId);

    /** How many attached items count towards the closure requirement. */
    int countClosureEvidence(UUID workOrderId);

    // ---- vendors ------------------------------------------------------------------------------

    MaintenanceVendor saveVendor(MaintenanceVendor vendor);

    Optional<MaintenanceVendor> findVendor(UUID id);

    Optional<MaintenanceVendor> findVendorByCode(String siteCode, String vendorCode);

    List<MaintenanceVendor> findVendors(String siteCode);

    // ---- preventive schedules -----------------------------------------------------------------

    PreventiveMaintenanceSchedule saveSchedule(PreventiveMaintenanceSchedule schedule);

    Optional<PreventiveMaintenanceSchedule> findSchedule(UUID id);

    Optional<PreventiveMaintenanceSchedule> findScheduleByCode(String siteCode, String scheduleCode);

    List<PreventiveMaintenanceSchedule> findSchedules(String siteCode, UUID assetId);

    /** Active schedules whose next service falls inside the lead-time window on {@code today}. */
    List<PreventiveMaintenanceSchedule> findSchedulesDueForGeneration(LocalDate today, int limit);

    // ---- read model -----------------------------------------------------------------------------

    /** Open faults and work orders for a site, for the S152 dashboard's maintenance summary. */
    OpenWorkCounts countOpenWork(String siteCode);

    record OpenWorkCounts(int openFaults, int openWorkOrders, int overdueWorkOrders) {
    }
}
