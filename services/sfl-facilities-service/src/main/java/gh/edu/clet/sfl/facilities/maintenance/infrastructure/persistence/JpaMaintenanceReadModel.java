package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.dashboard.application.ports.MaintenanceReadModel;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplies the maintenance half of the S152 dashboard from the S153 tables.
 *
 * <p>Lives in {@code maintenance} rather than in {@code dashboard} because it reads maintenance's own
 * persistence, and a module's tables are its own business. The dashboard sees only the port.
 *
 * <p>Reads the whole table and filters in memory. That is a deliberate Phase-1 choice: the fault and
 * work-order tables hold a handful of rows per site, the dashboard is not on a hot path, and a derived
 * query would need a status projection this module does not otherwise have. It is the first thing to
 * revisit when S153 grows a real backlog.
 */
@Component
class JpaMaintenanceReadModel implements MaintenanceReadModel {

    private final FacilityFaultRepository faults;
    private final WorkOrderRepository workOrders;

    JpaMaintenanceReadModel(FacilityFaultRepository faults, WorkOrderRepository workOrders) {
        this.faults = faults;
        this.workOrders = workOrders;
    }

    @Override
    @Transactional(readOnly = true)
    public OpenWork openWorkFor(String siteCode) {
        String scope = normalize(siteCode);
        long openFaults = openFaults(scope).size();
        long openWorkOrders = openWorkOrders(scope).size();
        return new OpenWork((int) openFaults, (int) openWorkOrders);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> locationCodesWithOpenWork(String siteCode) {
        String scope = normalize(siteCode);
        Set<String> codes = new HashSet<>();
        openFaults(scope).forEach(fault -> codes.add(normalize(fault.locationCode())));
        openWorkOrders(scope).forEach(workOrder -> codes.add(normalize(workOrder.locationCode())));
        codes.remove(null);
        return Set.copyOf(codes);
    }

    /**
     * Faults that still represent work.
     *
     * <p>"Open" is everything except {@code RESOLVED} and {@code CANCELLED}. {@code WORK_ORDER_CREATED}
     * counts as open on purpose: the fault has been triaged into a work order, not fixed, and a
     * dashboard that stopped counting it there would report a leaking roof as dealt with the moment
     * somebody wrote a ticket for it.
     */
    private List<FacilityFault> openFaults(String scope) {
        return faults.findAll().stream()
                .map(FacilityFaultRecord::toDomain)
                .filter(fault -> scope == null || scope.equals(fault.siteCode()))
                .filter(fault -> fault.status() != FacilityFaultStatus.RESOLVED
                        && fault.status() != FacilityFaultStatus.CANCELLED)
                .toList();
    }

    private List<WorkOrder> openWorkOrders(String scope) {
        return workOrders.findAll().stream()
                .map(WorkOrderRecord::toDomain)
                .filter(workOrder -> scope == null || scope.equals(workOrder.siteCode()))
                .filter(workOrder -> workOrder.status() != WorkOrderStatus.CLOSED)
                .toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
