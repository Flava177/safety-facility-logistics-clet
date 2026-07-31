package gh.edu.clet.sfl.facilities.dashboard.application.ports;

import java.util.Set;

/**
 * The maintenance facts the S152 dashboard reports.
 *
 * <p>SRS-SFL-S152-05 asks for "open service requests" and "maintenance-linked readiness risks" on the
 * facilities dashboard, so the dashboard has to know something about S153. A port rather than a direct
 * call into {@code maintenance} keeps the direction clean: the dashboard states what it needs and the
 * maintenance module supplies it. When S153 is built out properly this is the seam it plugs into, and
 * nothing in {@code dashboard} changes.
 */
public interface MaintenanceReadModel {

    /** Open fault and work-order counts for a site, or across every site when {@code siteCode} is null. */
    record OpenWork(int openFaults, int openWorkOrders) {
    }

    OpenWork openWorkFor(String siteCode);

    /**
     * The location codes in a site that have open maintenance against them.
     *
     * <p>Still codes rather than space ids, and now for a different reason than when it was written.
     * The original note said S153 would populate {@code room_id} and this could then match on it; S153
     * does populate it. But a fault may also be reported against a corridor or a car park, which the
     * estate model has no room for, so the code remains the only identifier every maintenance record
     * has. The adapter reads {@code room_id} where there is one and falls back to the code.
     */
    Set<String> locationCodesWithOpenWork(String siteCode);
}
