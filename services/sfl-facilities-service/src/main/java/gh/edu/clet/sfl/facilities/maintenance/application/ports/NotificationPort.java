package gh.edu.clet.sfl.facilities.maintenance.application.ports;

import gh.edu.clet.sfl.common.security.SflRole;
import java.util.Map;

/**
 * Outbound port for the workflow notifications SRS-SFL-S153-02 requires: "notify when work is assigned,
 * overdue, escalated or blocked".
 *
 * <p><strong>Why this exists here rather than being left to a notification service.</strong>
 * {@code MaintenanceEscalationService} carried a javadoc saying it deliberately does not notify, on the
 * grounds that a second notifier would be a second place for CLET's escalation contact list to be
 * wrong. That reasoning is sound and this port does not contradict it: the contact list still lives in
 * exactly one place per deployable, behind one port with one adapter, and a real provider replaces the
 * adapter by configuration. What was not sound was the consequence — publishing an event nobody
 * consumed and treating the requirement as met. It was not met, the gap report said so, and it stayed
 * unmet for three passes.
 *
 * <p>Deliberately identical in shape to {@code fleet.application.port.NotificationPort}, which meets
 * the same requirement for S166-02. Two services that must not share persistence cannot share the port
 * without putting IFIMP and FTLMP workflow rules into the shared kernel, so the duplication is chosen:
 * it costs one small interface and buys two deployables that can be read the same way and, if a
 * provider is ever procured, drained by one adapter against two tables of identical shape.
 */
public interface NotificationPort {

    /** Notifies one person — an assignee, a reporter, a requester. */
    void notifyRecipient(String siteCode, String recipient, NotificationKind kind, String subjectReference,
            Map<String, String> context);

    /** Notifies whoever holds a role at a site, for escalations that belong to a desk rather than a person. */
    void notifyRole(String siteCode, SflRole role, NotificationKind kind, String subjectReference,
            Map<String, String> context);

    /**
     * What happened, not what to say about it. Wording, channel and locale belong to the provider; this
     * enum is the operational fact, so a template change never becomes a code change.
     */
    enum NotificationKind {
        WORK_ASSIGNED,
        WORK_OVERDUE,
        WORK_ESCALATED,
        WORK_BLOCKED,
        /** The response deadline passed with nobody having picked the work up — distinct from OVERDUE. */
        RESPONSE_OVERDUE,
        FAULT_ESCALATED,
        EVIDENCE_REQUIRED
    }
}
