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
 * <p>Reads are deliberately narrow - two counts and a set of location codes. A dashboard that pulled
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

    /**
     * The two counts the dashboard reports.
     *
     * <p>A null site means the estate, not nothing. This used to return {@code (0, 0)} without a site
     * code, so the estate-wide dashboard - which is what an actor scoped to every site sees - reported
     * no open faults and no open work orders beside eighteen open blockers and three impaired assets.
     * Every other summary on that screen aggregates across sites when none is named, and a card that
     * silently says zero is worse than one that says nothing.
     */
    @Override
    public OpenWork openWorkFor(String siteCode) {
        String site = normalize(siteCode);
        return new OpenWork((int) faults.countOpenForSite(site), (int) workOrders.countOpenForSite(site));
    }

    @Override
    public Set<String> locationCodesWithOpenWork(String siteCode) {
        String site = normalize(siteCode);
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
