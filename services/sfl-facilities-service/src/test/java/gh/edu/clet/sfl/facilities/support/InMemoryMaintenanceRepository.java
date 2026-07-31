package gh.edu.clet.sfl.facilities.support;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An in-memory CMMS, for exercising the S153 application services without a database.
 *
 * <p>The same choice {@code InMemoryFacilitiesRepository} makes, for the same reason: the interesting
 * S153 rules — the SLA calculation, the escalation ladder, the closure gate, the vendor scope, the
 * idempotency of generation — all live above persistence, and a failure should point at the rule.
 *
 * <p>The number sequences are {@link AtomicLong} rather than row counts, matching the real adapter.
 * A test that got duplicate fault numbers because the double counted rows would be testing the
 * double.
 */
public class InMemoryMaintenanceRepository implements MaintenanceRepository {

    private final Map<UUID, FacilityFault> faults = new LinkedHashMap<>();
    private final Map<UUID, WorkOrder> workOrders = new LinkedHashMap<>();
    private final Map<UUID, WorkOrderPart> parts = new LinkedHashMap<>();
    private final Map<UUID, MaintenanceEvidence> evidence = new LinkedHashMap<>();
    private final Map<UUID, MaintenanceVendor> vendors = new LinkedHashMap<>();
    private final Map<UUID, PreventiveMaintenanceSchedule> schedules = new LinkedHashMap<>();
    private final AtomicLong faultSequence = new AtomicLong();
    private final AtomicLong workOrderSequence = new AtomicLong();

    // ---- faults -----------------------------------------------------------------------------

    @Override
    public FacilityFault saveFault(FacilityFault fault) {
        faults.put(fault.id(), fault);
        return fault;
    }

    @Override
    public Optional<FacilityFault> findFault(UUID id) {
        return Optional.ofNullable(faults.get(id));
    }

    @Override
    public Optional<FacilityFault> findFaultByNumber(String faultNumber) {
        String wanted = normalize(faultNumber);
        return faults.values().stream().filter(f -> f.faultNumber().equals(wanted)).findFirst();
    }

    @Override
    public List<FacilityFault> findFaults(String siteCode, UUID roomId, FacilityFaultStatus status,
            Boolean openOnly, String reportedBy, int limit) {
        String site = normalize(siteCode);
        return faults.values().stream()
                .filter(f -> site == null || f.siteCode().equals(site))
                .filter(f -> roomId == null || roomId.equals(f.roomId()))
                .filter(f -> status == null || f.status() == status)
                .filter(f -> reportedBy == null || reportedBy.equals(f.reportedBy()))
                .filter(f -> !Boolean.TRUE.equals(openOnly) || f.status().isOpen())
                .sorted(Comparator.comparing(FacilityFault::reportedAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<FacilityFault> findOverdueFaults(Instant asOf, int limit) {
        return faults.values().stream()
                .filter(f -> f.isOverdue(asOf))
                .sorted(Comparator.comparing(FacilityFault::slaDueAt))
                .limit(limit)
                .toList();
    }

    @Override
    public String nextFaultNumber(String siteCode) {
        return "FLT-" + normalize(siteCode) + "-" + String.format("%06d", faultSequence.incrementAndGet());
    }

    // ---- work orders ------------------------------------------------------------------------

    @Override
    public WorkOrder saveWorkOrder(WorkOrder workOrder) {
        workOrders.put(workOrder.id(), workOrder);
        return workOrder;
    }

    @Override
    public Optional<WorkOrder> findWorkOrder(UUID id) {
        return Optional.ofNullable(workOrders.get(id));
    }

    @Override
    public Optional<WorkOrder> findWorkOrderForFault(UUID faultId) {
        return workOrders.values().stream()
                .filter(order -> faultId.equals(order.facilityFaultId()))
                .findFirst();
    }

    @Override
    public List<WorkOrder> findWorkOrders(String siteCode, UUID roomId, UUID assetId, WorkOrderStatus status,
            String assignedTo, UUID vendorId, Boolean openOnly, int limit) {
        String site = normalize(siteCode);
        return workOrders.values().stream()
                .filter(w -> site == null || w.siteCode().equals(site))
                .filter(w -> roomId == null || roomId.equals(w.roomId()))
                .filter(w -> assetId == null || assetId.equals(w.assetId()))
                .filter(w -> status == null || w.status() == status)
                .filter(w -> assignedTo == null || assignedTo.equals(w.assignedTo()))
                .filter(w -> vendorId == null || vendorId.equals(w.vendorId()))
                .filter(w -> !Boolean.TRUE.equals(openOnly) || w.status().isOpen())
                .sorted(Comparator.comparing((WorkOrder w) -> w.metadata().createdAt()).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<WorkOrder> findOverdueWorkOrders(Instant asOf, int limit) {
        return workOrders.values().stream()
                .filter(order -> order.isOverdue(asOf))
                .sorted(Comparator.comparing(WorkOrder::slaDueAt))
                .limit(limit)
                .toList();
    }

    @Override
    public String nextWorkOrderNumber(String siteCode) {
        return "WO-" + normalize(siteCode) + "-" + String.format("%06d", workOrderSequence.incrementAndGet());
    }

    // ---- parts and evidence -------------------------------------------------------------------

    @Override
    public WorkOrderPart savePart(WorkOrderPart part) {
        parts.put(part.id(), part);
        return part;
    }

    @Override
    public List<WorkOrderPart> findParts(UUID workOrderId) {
        return parts.values().stream()
                .filter(part -> part.workOrderId().equals(workOrderId))
                .sorted(Comparator.comparing(WorkOrderPart::recordedAt))
                .toList();
    }

    @Override
    public void deletePart(UUID partId) {
        parts.remove(partId);
    }

    @Override
    public MaintenanceEvidence saveEvidence(MaintenanceEvidence item) {
        evidence.put(item.id(), item);
        return item;
    }

    @Override
    public Optional<MaintenanceEvidence> findEvidence(UUID id) {
        return Optional.ofNullable(evidence.get(id));
    }

    @Override
    public List<MaintenanceEvidence> findEvidenceFor(UUID workOrderId) {
        return evidence.values().stream()
                .filter(item -> item.workOrderId().equals(workOrderId))
                .sorted(Comparator.comparing(MaintenanceEvidence::uploadedAt))
                .toList();
    }

    @Override
    public int countClosureEvidence(UUID workOrderId) {
        return (int) evidence.values().stream()
                .filter(item -> item.workOrderId().equals(workOrderId))
                .filter(item -> item.evidenceType() != EvidenceType.INVOICE)
                .count();
    }

    // ---- vendors ------------------------------------------------------------------------------

    @Override
    public MaintenanceVendor saveVendor(MaintenanceVendor vendor) {
        vendors.put(vendor.id(), vendor);
        return vendor;
    }

    @Override
    public Optional<MaintenanceVendor> findVendor(UUID id) {
        return Optional.ofNullable(vendors.get(id));
    }

    @Override
    public Optional<MaintenanceVendor> findVendorByCode(String siteCode, String vendorCode) {
        String site = normalize(siteCode);
        String code = normalize(vendorCode);
        return vendors.values().stream()
                .filter(vendor -> vendor.siteCode().equals(site) && vendor.vendorCode().equals(code))
                .findFirst();
    }

    @Override
    public List<MaintenanceVendor> findVendors(String siteCode) {
        String site = normalize(siteCode);
        return vendors.values().stream()
                .filter(vendor -> site == null || vendor.siteCode().equals(site))
                .sorted(Comparator.comparing(MaintenanceVendor::vendorCode))
                .toList();
    }

    // ---- preventive schedules -----------------------------------------------------------------

    @Override
    public PreventiveMaintenanceSchedule saveSchedule(PreventiveMaintenanceSchedule schedule) {
        schedules.put(schedule.id(), schedule);
        return schedule;
    }

    @Override
    public Optional<PreventiveMaintenanceSchedule> findSchedule(UUID id) {
        return Optional.ofNullable(schedules.get(id));
    }

    @Override
    public Optional<PreventiveMaintenanceSchedule> findScheduleByCode(String siteCode, String scheduleCode) {
        String site = normalize(siteCode);
        String code = normalize(scheduleCode);
        return schedules.values().stream()
                .filter(schedule -> schedule.siteCode().equals(site) && schedule.scheduleCode().equals(code))
                .findFirst();
    }

    @Override
    public List<PreventiveMaintenanceSchedule> findSchedules(String siteCode, UUID assetId) {
        String site = normalize(siteCode);
        return schedules.values().stream()
                .filter(schedule -> site == null || schedule.siteCode().equals(site))
                .filter(schedule -> assetId == null || assetId.equals(schedule.assetId()))
                .sorted(Comparator.comparing(PreventiveMaintenanceSchedule::nextDueOn))
                .toList();
    }

    @Override
    public List<PreventiveMaintenanceSchedule> findSchedulesDueForGeneration(LocalDate today, int limit) {
        return schedules.values().stream()
                .filter(schedule -> schedule.isDueForGeneration(today))
                .sorted(Comparator.comparing(PreventiveMaintenanceSchedule::nextDueOn))
                .limit(limit)
                .toList();
    }

    // ---- read model -----------------------------------------------------------------------------

    @Override
    public OpenWorkCounts countOpenWork(String siteCode) {
        String site = normalize(siteCode);
        int openFaults = (int) faults.values().stream()
                .filter(f -> f.siteCode().equals(site) && f.status().isOpen()).count();
        int openOrders = (int) workOrders.values().stream()
                .filter(w -> w.siteCode().equals(site) && w.status().isOpen()).count();
        int overdue = (int) workOrders.values().stream()
                .filter(w -> w.siteCode().equals(site) && w.isOverdue(Instant.now())).count();
        return new OpenWorkCounts(openFaults, openOrders, overdue);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
