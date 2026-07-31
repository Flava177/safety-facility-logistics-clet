package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.EvidenceType;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * The one adapter behind {@link MaintenanceRepository}.
 *
 * <h2>Why every save re-reads before writing</h2>
 *
 * Each {@code save*} looks the row up and calls {@code apply(...)} on the managed entity rather than
 * constructing a detached one and handing it to {@code save}. Both work; the difference shows on
 * update. A detached entity with a matching id makes Hibernate issue a select-then-update anyway, and
 * — more importantly — silently overwrites any column the mapping forgot, because a null field on a
 * fresh object is indistinguishable from a deliberate null. Applying onto the managed instance keeps
 * the aggregate the single source of every column, which is the property the {@code apply} methods
 * exist to give.
 */
@Repository
public class JpaMaintenanceRepositoryAdapter implements MaintenanceRepository {

    private final JpaFacilityFaultRepository faults;
    private final JpaWorkOrderRepository workOrders;
    private final JpaWorkOrderPartRepository parts;
    private final JpaMaintenanceEvidenceRepository evidence;
    private final JpaMaintenanceVendorRepository vendors;
    private final JpaPreventiveScheduleRepository schedules;

    public JpaMaintenanceRepositoryAdapter(JpaFacilityFaultRepository faults,
            JpaWorkOrderRepository workOrders, JpaWorkOrderPartRepository parts,
            JpaMaintenanceEvidenceRepository evidence, JpaMaintenanceVendorRepository vendors,
            JpaPreventiveScheduleRepository schedules) {
        this.faults = faults;
        this.workOrders = workOrders;
        this.parts = parts;
        this.evidence = evidence;
        this.vendors = vendors;
        this.schedules = schedules;
    }

    // ---- faults -----------------------------------------------------------------------------

    @Override
    public FacilityFault saveFault(FacilityFault fault) {
        FacilityFaultRecord record = faults.findById(fault.id()).orElseGet(FacilityFaultRecord::new);
        record.apply(fault);
        return faults.save(record).toDomain();
    }

    @Override
    public Optional<FacilityFault> findFault(UUID id) {
        return faults.findById(id).map(FacilityFaultRecord::toDomain);
    }

    @Override
    public Optional<FacilityFault> findFaultByNumber(String faultNumber) {
        return faults.findByFaultNumber(normalize(faultNumber)).map(FacilityFaultRecord::toDomain);
    }

    @Override
    public List<FacilityFault> findFaults(String siteCode, UUID roomId, FacilityFaultStatus status,
            Boolean openOnly, String reportedBy, int limit) {
        return faults.search(normalize(siteCode), roomId, status, reportedBy, openOnly, page(limit)).stream()
                .map(FacilityFaultRecord::toDomain)
                .toList();
    }

    @Override
    public List<FacilityFault> findOverdueFaults(Instant asOf, int limit) {
        return faults.findOverdue(asOf, page(limit)).stream().map(FacilityFaultRecord::toDomain).toList();
    }

    @Override
    public String nextFaultNumber(String siteCode) {
        return "FLT-" + normalize(siteCode) + "-" + String.format("%06d", faults.nextFaultSequence());
    }

    // ---- work orders ------------------------------------------------------------------------

    @Override
    public WorkOrder saveWorkOrder(WorkOrder workOrder) {
        WorkOrderRecord record = workOrders.findById(workOrder.id()).orElseGet(WorkOrderRecord::new);
        record.apply(workOrder);
        return workOrders.save(record).toDomain();
    }

    @Override
    public Optional<WorkOrder> findWorkOrder(UUID id) {
        return workOrders.findById(id).map(WorkOrderRecord::toDomain);
    }

    @Override
    public Optional<WorkOrder> findWorkOrderForFault(UUID faultId) {
        return workOrders.findByFacilityFaultId(faultId).map(WorkOrderRecord::toDomain);
    }

    @Override
    public List<WorkOrder> findWorkOrders(String siteCode, UUID roomId, UUID assetId, WorkOrderStatus status,
            String assignedTo, UUID vendorId, Boolean openOnly, int limit) {
        return workOrders.search(normalize(siteCode), roomId, assetId, status, assignedTo, vendorId, openOnly,
                        page(limit)).stream()
                .map(WorkOrderRecord::toDomain)
                .toList();
    }

    @Override
    public List<WorkOrder> findOverdueWorkOrders(Instant asOf, int limit) {
        return workOrders.findOverdue(asOf, page(limit)).stream().map(WorkOrderRecord::toDomain).toList();
    }

    @Override
    public List<WorkOrder> findResponseBreaches(Instant asOf, int limit) {
        return workOrders.findResponseBreaches(asOf, page(limit)).stream().map(WorkOrderRecord::toDomain).toList();
    }

    @Override
    public String nextWorkOrderNumber(String siteCode) {
        return "WO-" + normalize(siteCode) + "-" + String.format("%06d", workOrders.nextWorkOrderSequence());
    }

    // ---- parts and evidence -------------------------------------------------------------------

    @Override
    public WorkOrderPart savePart(WorkOrderPart part) {
        WorkOrderPartRecord record = parts.findById(part.id()).orElseGet(WorkOrderPartRecord::new);
        record.apply(part);
        return parts.save(record).toDomain();
    }

    @Override
    public List<WorkOrderPart> findParts(UUID workOrderId) {
        return parts.findByWorkOrderIdOrderByRecordedAtAsc(workOrderId).stream()
                .map(WorkOrderPartRecord::toDomain)
                .toList();
    }

    @Override
    public void deletePart(UUID partId) {
        parts.deleteById(partId);
    }

    @Override
    public MaintenanceEvidence saveEvidence(MaintenanceEvidence item) {
        MaintenanceEvidenceRecord record = evidence.findById(item.id())
                .orElseGet(MaintenanceEvidenceRecord::new);
        record.apply(item);
        return evidence.save(record).toDomain();
    }

    @Override
    public Optional<MaintenanceEvidence> findEvidence(UUID id) {
        return evidence.findById(id).map(MaintenanceEvidenceRecord::toDomain);
    }

    @Override
    public List<MaintenanceEvidence> findEvidenceFor(UUID workOrderId) {
        return evidence.findByWorkOrderIdOrderByUploadedAtAsc(workOrderId).stream()
                .map(MaintenanceEvidenceRecord::toDomain)
                .toList();
    }

    @Override
    public int countClosureEvidence(UUID workOrderId) {
        return (int) evidence.countByWorkOrderIdAndEvidenceTypeNot(workOrderId, EvidenceType.INVOICE);
    }

    // ---- vendors ------------------------------------------------------------------------------

    @Override
    public MaintenanceVendor saveVendor(MaintenanceVendor vendor) {
        MaintenanceVendorRecord record = vendors.findById(vendor.id())
                .orElseGet(MaintenanceVendorRecord::new);
        record.apply(vendor);
        return vendors.save(record).toDomain();
    }

    @Override
    public Optional<MaintenanceVendor> findVendor(UUID id) {
        return vendors.findById(id).map(MaintenanceVendorRecord::toDomain);
    }

    @Override
    public Optional<MaintenanceVendor> findVendorByCode(String siteCode, String vendorCode) {
        return vendors.findBySiteCodeAndVendorCode(normalize(siteCode), normalize(vendorCode))
                .map(MaintenanceVendorRecord::toDomain);
    }

    @Override
    public List<MaintenanceVendor> findVendors(String siteCode) {
        return vendors.search(normalize(siteCode)).stream().map(MaintenanceVendorRecord::toDomain).toList();
    }

    // ---- preventive schedules -----------------------------------------------------------------

    @Override
    public PreventiveMaintenanceSchedule saveSchedule(PreventiveMaintenanceSchedule schedule) {
        PreventiveScheduleRecord record = schedules.findById(schedule.id())
                .orElseGet(PreventiveScheduleRecord::new);
        record.apply(schedule);
        return schedules.save(record).toDomain();
    }

    @Override
    public Optional<PreventiveMaintenanceSchedule> findSchedule(UUID id) {
        return schedules.findById(id).map(PreventiveScheduleRecord::toDomain);
    }

    @Override
    public Optional<PreventiveMaintenanceSchedule> findScheduleByCode(String siteCode, String scheduleCode) {
        return schedules.findBySiteCodeAndScheduleCode(normalize(siteCode), normalize(scheduleCode))
                .map(PreventiveScheduleRecord::toDomain);
    }

    @Override
    public List<PreventiveMaintenanceSchedule> findSchedules(String siteCode, UUID assetId) {
        return schedules.search(normalize(siteCode), assetId).stream()
                .map(PreventiveScheduleRecord::toDomain)
                .toList();
    }

    @Override
    public List<PreventiveMaintenanceSchedule> findSchedulesDueForGeneration(LocalDate today, int limit) {
        return schedules.findDue(today, Math.max(1, limit)).stream()
                .map(PreventiveScheduleRecord::toDomain)
                .toList();
    }

    // ---- read model -----------------------------------------------------------------------------

    @Override
    public OpenWorkCounts countOpenWork(String siteCode) {
        String site = normalize(siteCode);
        if (site == null) {
            return new OpenWorkCounts(0, 0, 0);
        }
        return new OpenWorkCounts(
                (int) faults.countOpenForSite(site),
                (int) workOrders.countOpenForSite(site),
                (int) workOrders.countOverdueForSite(site, Instant.now()));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }

    private static PageRequest page(int limit) {
        return PageRequest.of(0, Math.max(1, Math.min(limit, 500)));
    }
}
