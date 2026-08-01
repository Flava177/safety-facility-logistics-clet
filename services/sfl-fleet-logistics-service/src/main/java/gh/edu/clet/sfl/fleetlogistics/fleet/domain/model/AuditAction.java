package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/** The action recorded on an {@link AuditEvent} (SRS-SFL-S166-03). */
public enum AuditAction {
    CREATE,
    UPDATE,
    STATE_TRANSITION,
    ASSIGN,
    REASSIGN,
    /** The assigned driver confirmed or deferred. Not a STATE_TRANSITION: no status changed. */
    ACKNOWLEDGE,
    HOLD,
    RESUME,
    ESCALATE,
    CANCEL,
    CLOSE,
    REOPEN,
    ODOMETER_CORRECTION,
    INSPECTION_RECORDED,
    EVIDENCE_REGISTERED,
    EVIDENCE_VIEWED,
    EVIDENCE_EXPORT_REQUESTED,
    EVIDENCE_EXPORT_DECIDED,
    EVIDENCE_EXPORTED,
    INTEGRATION_ACCEPTED,
    INTEGRATION_REJECTED,
    INTEGRATION_REPLAYED,
    AUTHORIZATION_DENIED,
    AUDIT_INTEGRITY_CHECK,
    DASHBOARD_ACCESSED,
    REPORT_EXPORTED
}
