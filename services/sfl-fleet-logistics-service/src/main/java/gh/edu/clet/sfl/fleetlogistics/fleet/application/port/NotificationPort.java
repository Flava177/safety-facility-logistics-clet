package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.util.Map;

/**
 * Outbound port for the workflow notifications required by SRS-SFL-S166-02 ("notify responsible users
 * when work is assigned, overdue, escalated or blocked").
 *
 * <p>Phase 1 has no fleet notification provider, so the shipped adapter persists a notification intent
 * and publishes it through the outbox rather than reporting a success it did not achieve. Configuring a
 * real provider that is not fully configured fails loudly (gap report C-11).
 */
public interface NotificationPort {

    void notifyAssignee(SiteCode siteScope, String assignee, NotificationKind kind, String subjectReference,
            Map<String, String> context);

    void notifyRole(SiteCode siteScope, SflRole role, NotificationKind kind, String subjectReference,
            Map<String, String> context);

    enum NotificationKind {
        WORK_ASSIGNED,
        WORK_OVERDUE,
        WORK_ESCALATED,
        WORK_BLOCKED,
        COMPLIANCE_EXPIRING,
        COMPLIANCE_EXPIRED,
        SERVICE_DUE,
        SERVICE_OVERDUE,
        INSPECTION_FAILED,
        AUDIT_INTEGRITY_ALERT
    }
}
