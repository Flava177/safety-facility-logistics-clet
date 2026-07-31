package gh.edu.clet.sfl.common.security;

public enum SflPermission {
    FACILITIES_WORK_ORDER_CREATE,
    FACILITIES_WORK_ORDER_ASSIGN,
    FACILITIES_WORK_ORDER_CLOSE,
    FACILITIES_WORK_ORDER_READ,
    FACILITIES_READINESS_MANAGE,
    FACILITIES_MASTER_DATA_MANAGE,
    // The four work-order permissions above predate S152 and are kept, because they already name the
    // right things. S153 adds the rest of what SRS-SFL-S153-01..03 needs: the fault side of the
    // workflow, the transitions the old three-state model had nowhere for, preventive scheduling,
    // vendors, and evidence — which is separated into read and export because S153-03 makes export a
    // distinct authorised act with a recorded reason, not simply a stronger form of reading.
    FACILITIES_FAULT_REPORT,
    FACILITIES_FAULT_READ,
    FACILITIES_FAULT_TRIAGE,
    FACILITIES_WORK_ORDER_UPDATE,
    FACILITIES_WORK_ORDER_CANCEL,
    FACILITIES_PM_SCHEDULE_READ,
    FACILITIES_PM_SCHEDULE_MANAGE,
    FACILITIES_VENDOR_READ,
    FACILITIES_VENDOR_MANAGE,
    FACILITIES_EVIDENCE_READ,
    FACILITIES_EVIDENCE_ATTACH,
    FACILITIES_EVIDENCE_EXPORT,
    ASSET_REFERENCE_MANAGE,
    ASSET_REFERENCE_READ,
    AUDIT_READ,
    INTEGRATION_HEALTH_READ,

    // Computer-Aided Facility Management / IWMS permissions (SRS S152). Additive only.
    // The role -> permission mapping lives in the facilities service
    // (gh.edu.clet.sfl.facilities.shared.domain.policy.FacilitiesPermissionMatrix), so no IFIMP
    // business rule enters this shared library. S152 hosts S153 and S159, so these are the
    // permissions those modules will inherit rather than redeclare.
    FACILITIES_SITE_READ,
    FACILITIES_SITE_MANAGE,
    FACILITIES_SPACE_READ,
    FACILITIES_SPACE_MANAGE,
    FACILITIES_ZONE_READ,
    FACILITIES_ZONE_MANAGE,
    FACILITIES_DEVICE_REFERENCE_READ,
    FACILITIES_DEVICE_REFERENCE_REGISTER,
    FACILITIES_ASSET_READ,
    FACILITIES_ASSET_MANAGE,
    FACILITIES_READINESS_READ,
    FACILITIES_READINESS_ASSESS,
    FACILITIES_READINESS_OVERRIDE,
    FACILITIES_READINESS_CHECKLIST_MANAGE,
    FACILITIES_OPERATING_MODE_CHANGE,
    FACILITIES_DASHBOARD_READ,
    FACILITIES_DASHBOARD_DRILLDOWN,
    FACILITIES_AUDIT_READ,
    FACILITIES_AUDIT_INTEGRITY_CHECK,
    FACILITIES_CONFIG_READ,
    FACILITIES_CONFIG_MANAGE,

    // Room and Resource Booking permissions (SRS S159). Additive only, and hosted on S152 like S153.
    //
    // REQUEST and APPROVE are separate because SRS-SFL-S159-02 makes approval a distinct authorised
    // act; CANCEL is separate again because cancelling one's own booking is not the same authority as
    // cancelling somebody else's, and the per-record narrowing for the former lives in
    // BookingApplicationService rather than here.
    //
    // OVERRIDE is the interesting one: it books into a space readiness says is unavailable. Held by
    // very few roles, always recorded with a reason, and never implied by any other permission.
    FACILITIES_BOOKING_READ,
    FACILITIES_BOOKING_REQUEST,
    FACILITIES_BOOKING_APPROVE,
    FACILITIES_BOOKING_CANCEL,
    FACILITIES_BOOKING_OVERRIDE,
    FACILITIES_RESOURCE_READ,
    FACILITIES_RESOURCE_MANAGE,
    FACILITIES_SETUP_TASK_MANAGE,

    // Fleet and vehicle management permissions (SRS S166). Additive only.
    // The role -> permission mapping lives in the fleet feature package
    // (gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.FleetPermissionMatrix),
    // so no fleet business logic enters this shared library.
    FLEET_VEHICLE_READ,
    FLEET_VEHICLE_MANAGE,
    FLEET_VEHICLE_LIFECYCLE_MANAGE,
    FLEET_VEHICLE_RESTORE,
    FLEET_VEHICLE_SENSITIVE_READ,
    FLEET_VEHICLE_ODOMETER_CORRECT,
    FLEET_COMPLIANCE_MANAGE,
    FLEET_SERVICE_RECORD_MANAGE,
    FLEET_DRIVER_READ,
    FLEET_DRIVER_MANAGE,
    FLEET_DRIVER_SENSITIVE_READ,
    FLEET_TRIP_READ,
    FLEET_TRIP_MANAGE,
    FLEET_TRIP_ASSIGN,
    FLEET_TRIP_CANCEL,
    FLEET_TRIP_CLOSE,
    FLEET_INSPECTION_RECORD,
    FLEET_WORKFLOW_READ,
    FLEET_WORKFLOW_MANAGE,
    FLEET_WORKFLOW_ASSIGN,
    FLEET_WORKFLOW_ESCALATE,
    FLEET_WORKFLOW_APPROVE,
    FLEET_WORKFLOW_CANCEL,
    FLEET_WORKFLOW_REOPEN,
    FLEET_EVIDENCE_READ,
    FLEET_EVIDENCE_REGISTER,
    FLEET_EVIDENCE_EXPORT_REQUEST,
    FLEET_EVIDENCE_EXPORT_APPROVE,
    FLEET_EVIDENCE_LEGAL_HOLD_OVERRIDE,
    FLEET_AUDIT_READ,
    FLEET_AUDIT_INTEGRITY_CHECK,
    FLEET_INTEGRATION_INGEST,
    FLEET_INTEGRATION_HEALTH_READ,
    FLEET_INTEGRATION_REPLAY,
    FLEET_DASHBOARD_READ,
    FLEET_DASHBOARD_DRILLDOWN,
    FLEET_REPORT_EXPORT,

    // Fuel Management and Driver Logbooks permissions (SRS S168_fuel). Additive only.
    FUEL_TRANSACTION_READ,
    FUEL_TRANSACTION_CAPTURE,
    FUEL_TRANSACTION_IMPORT,
    FUEL_TRANSACTION_VOID,
    FUEL_POLICY_READ,
    /** SRS-SFL-S168fuel-04. Reading the card register is a fleet-office read, not a driver's. */
    FUEL_CARD_READ,
    /** Issue, reassign, suspend, reinstate, cancel. A payment instrument, so manager-only. */
    FUEL_CARD_MANAGE,
    FUEL_POLICY_MANAGE,
    FUEL_LOGBOOK_READ,
    FUEL_LOGBOOK_CREATE,
    FUEL_LOGBOOK_SUBMIT,
    FUEL_LOGBOOK_REVIEW,
    FUEL_LOGBOOK_REOPEN,
    FUEL_RECONCILIATION_RUN,
    FUEL_ANOMALY_READ,
    FUEL_ANOMALY_MANAGE,
    FUEL_ANOMALY_APPROVE,
    FUEL_ANOMALY_ESCALATE,
    FUEL_REPORT_READ,
    FUEL_REPORT_EXPORT,
    FUEL_INTEGRATION_INGEST,
    FUEL_INTEGRATION_REPLAY,

    // Mailroom / Courier and Dispatch Tracking permissions (SRS S171). Additive only.
    // The role -> permission mapping lives in the dispatch feature package
    // (gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.DispatchPermissionMatrix).
    DISPATCH_ITEM_READ,
    DISPATCH_ITEM_REGISTER,
    DISPATCH_ITEM_MANAGE,
    DISPATCH_MANIFEST_READ,
    DISPATCH_MANIFEST_CREATE,
    DISPATCH_CUSTODY_RECORD,
    DISPATCH_RECEIPT_CONFIRM,
    DISPATCH_RETURN_RECONCILE,
    DISPATCH_INBOUND_REGISTER,
    DISPATCH_INBOUND_DISTRIBUTE,
    DISPATCH_EXCEPTION_READ,
    DISPATCH_EXCEPTION_MANAGE,
    DISPATCH_EXCEPTION_APPROVE,
    DISPATCH_EXCEPTION_ESCALATE,
    DISPATCH_REPORT_READ,
    DISPATCH_REPORT_EXPORT,
    DISPATCH_INTEGRATION_INGEST,
    DISPATCH_INTEGRATION_REPLAY,

    // Emergency Mass Notification permissions (SRS S174). Additive only. The role -> permission mapping
    // lives in the emergency service (gh.edu.clet.sfl.emergencynotification.domain.policy.EmergencyPermissionMatrix).
    EMERGENCY_TEMPLATE_READ,
    EMERGENCY_TEMPLATE_MANAGE,
    EMERGENCY_SCENARIO_READ,
    EMERGENCY_SCENARIO_MANAGE,
    EMERGENCY_AUDIENCE_READ,
    EMERGENCY_AUDIENCE_MANAGE,
    EMERGENCY_ACTIVATION_READ,
    EMERGENCY_ACTIVATION_CREATE,
    EMERGENCY_ACTIVATION_APPROVE,
    EMERGENCY_ACTIVATION_SEND,
    EMERGENCY_BREAK_GLASS_SEND,
    EMERGENCY_AFTER_ACTION_APPROVE,
    EMERGENCY_ALL_CLEAR_SEND,
    EMERGENCY_EVIDENCE_READ,
    EMERGENCY_EVIDENCE_EXPORT,
    EMERGENCY_REPORT_READ,
    EMERGENCY_REPORT_EXPORT,
    EMERGENCY_INTEGRATION_INGEST,
    EMERGENCY_INTEGRATION_REPLAY
}
