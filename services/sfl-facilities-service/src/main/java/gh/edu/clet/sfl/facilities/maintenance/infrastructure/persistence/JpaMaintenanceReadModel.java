package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.dashboard.application.ports.MaintenanceReadModel;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * What the S152 dashboard is told about maintenance.
 *
 * <p>The dashboard declares {@link MaintenanceReadModel} and this module implements it, which keeps
 * the arrow pointing from the module that reports to the module that owns the facts. Nothing in
 * {@code dashboard} imports a fault or a work order.
 *
 * <p>Reads are deliberately narrow — two counts and a set of location codes. A dashboard that pulled
 * whole work orders across this boundary would end up re-deriving S153's own rules about what counts
 * as open, and the two answers would eventually disagree.
 */
@Component
public class JpaMaintenanceReadModel implements MaintenanceReadModel {

    private final JpaFacilityFaultRepository faults;
    private final JpaWorkOrderRepository workOrders;

    public JpaMaintenanceReadModel(JpaFacilityFaultRepository faults, JpaWorkOrderRepository workOrders) {
        this.faults = faults;
        this.workOrders = workOrders;
    }

    @Override
    public OpenWork openWorkFor(String siteCode) {
        String site = normalize(siteCode);
        if (site == null) {
            return new OpenWork(0, 0);
        }
        return new OpenWork((int) faults.countOpenForSite(site), (int) workOrders.countOpenForSite(site));
    }

    @Override
    public Set<String> locationCodesWithOpenWork(String siteCode) {
        String site = normalize(siteCode);
        if (site == null) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        faults.search(site, null, null, null, Boolean.TRUE, org.springframework.data.domain.PageRequest.of(0, 500))
                .stream()
                .map(record -> record.toDomain().locationCode())
                .filter(code -> code != null && !code.isBlank())
                .map(JpaMaintenanceReadModel::normalize)
                .forEach(codes::add);
        workOrders.search(site, null, null, null, null, null, Boolean.TRUE,
                        org.springframework.data.domain.PageRequest.of(0, 500))
                .stream()
                .map(record -> record.toDomain().locationCode())
                .filter(code -> code != null && !code.isBlank())
                .map(JpaMaintenanceReadModel::normalize)
                .forEach(codes::add);
        return codes;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
