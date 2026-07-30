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
     * <p>Returns codes rather than space ids because that is what the S153 records actually carry
     * today: a fault names a {@code locationCode}, which is a room code. V6 added a nullable
     * {@code room_id} to both tables for S153 to populate later; until it does, matching on the code is
     * the link that exists rather than the one that is planned.
     */
    Set<String> locationCodesWithOpenWork(String siteCode);
}
