package gh.edu.clet.sfl.facilities.shared.domain.audit;

/**
 * The catalogue of auditable S152 actions.
 *
 * <p>An enum rather than a free string: the audit trail is queried by action
 * ({@code GET /api/v1/facilities/audit?action=...}) and hashed by name, so a typo would both hide a
 * record from its own filter and produce a hash nobody can reproduce.
 *
 * <p>Additive only. Renaming a constant would invalidate every hash computed with the old name — the
 * chain would replay as tampered. Deprecate, never rename.
 */
public enum AuditAction {

    // Estate register — SRS-SFL-S152-01
    SITE_CREATED,
    SITE_UPDATED,
    SITE_LIFECYCLE_CHANGED,
    SITE_OPERATING_MODE_CHANGED,
    BUILDING_CREATED,
    BUILDING_UPDATED,
    FLOOR_CREATED,
    FLOOR_UPDATED,
    ROOM_CREATED,
    ROOM_UPDATED,
    ROOM_LIFECYCLE_CHANGED,
    ROOM_READINESS_CHANGED,
    ZONE_CREATED,
    ZONE_UPDATED,
    ZONE_MEMBER_ADDED,
    ZONE_MEMBER_REMOVED,
    DEVICE_REFERENCE_REGISTERED,
    DEVICE_REFERENCE_UPDATED,
    FACILITY_ASSET_REGISTERED,
    FACILITY_ASSET_UPDATED,
    FACILITY_ASSET_STATUS_CHANGED,
    FACILITY_ASSET_RELOCATED,

    // Readiness — SRS-SFL-S152-01, -02, -05
    READINESS_CHECKLIST_CREATED,
    READINESS_CHECKLIST_UPDATED,
    READINESS_ASSESSMENT_SUBMITTED,
    READINESS_BLOCKER_RAISED,
    READINESS_BLOCKER_RESOLVED,
    READINESS_LOCK_ENGAGED,
    READINESS_LOCK_RELEASED,

    // Maintenance — SRS-SFL-S153-01, -02, -03
    FAULT_REPORTED,
    FAULT_TRIAGED,
    FAULT_DISMISSED,
    FAULT_RESOLVED,
    FAULT_LIFECYCLE_CHANGED,
    WORK_ORDER_CREATED,
    WORK_ORDER_ASSIGNED,
    WORK_ORDER_STARTED,
    WORK_ORDER_HELD,
    WORK_ORDER_COMPLETED,
    WORK_ORDER_REOPENED,
    WORK_ORDER_CLOSED,
    WORK_ORDER_CANCELLED,
    /**
     * The scheduled evaluator moved an item up the ladder. SRS-SFL-S153-02.
     *
     * <p>Audited even though no human did it, because "who escalated this and when" is the first
     * question asked about an item that reached a director's queue, and "the system, at 03:14, on the
     * rules then in force" is the only answer that closes it.
     */
    WORK_ORDER_ESCALATED,
    FAULT_ESCALATED,
    WORK_ORDER_PART_RECORDED,
    WORK_ORDER_PART_REMOVED,
    PREVENTIVE_SCHEDULE_CREATED,
    PREVENTIVE_SCHEDULE_UPDATED,
    PREVENTIVE_SCHEDULE_LIFECYCLE_CHANGED,
    PREVENTIVE_WORK_ORDER_GENERATED,
    MAINTENANCE_VENDOR_REGISTERED,
    MAINTENANCE_VENDOR_UPDATED,
    MAINTENANCE_VENDOR_LIFECYCLE_CHANGED,
    EVIDENCE_ATTACHED,
    /**
     * Evidence left CLET. SRS-SFL-S153-03 requires export to carry an approved reason and to be
     * logged; this is that log, and it records the recipient as well as the reason.
     */
    EVIDENCE_EXPORTED,
    EVIDENCE_LEGAL_HOLD_CHANGED,

    // Governance — SRS-SFL-S152-03, -04, -05
    RUNTIME_CONFIGURATION_CHANGED,
    DASHBOARD_SNAPSHOT_GENERATED,
    AUDIT_INTEGRITY_VERIFIED,

    /**
     * A refused command or query.
     *
     * <p>A denial is evidence. "You are not authorised to access this site or record" is an
     * {@code SRS-SFL-S152-01} error state, and an attempt to read a site outside an actor's scope is
     * exactly the event a compliance review looks for.
     */
    AUTHORIZATION_DENIED
}
