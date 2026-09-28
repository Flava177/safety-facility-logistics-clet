package gh.edu.clet.sfl.common.security;

public enum SflRole {
    SFL_ADMIN,
    FACILITIES_DIRECTOR,
    FACILITIES_MANAGER,
    IFIMP_MAINTENANCE_SUPERVISOR,
    IFIMP_TECHNICIAN,
    IFIMP_REQUESTER,
    VENDOR_TECHNICIAN,
    COMMAND_ROLE,
    DTI_ADMIN,
    INTEGRATION_ENGINEER,

    // Fleet and logistics user classes (SRS S166). Added for the S166 slice; adding enum
    // constants changes no existing signature and no existing service behaviour.
    FLEET_LOGISTICS_OFFICER,
    FLEET_MANAGER,
    FLEET_DRIVER,
    COMPLIANCE_OFFICER,
    FLEET_REPORTING_VIEWER,
    SERVICE_INTEGRATION,

    // Mailroom / Courier and Dispatch Tracking user classes (SRS S171). Added for the S171 slice;
    // adding enum constants changes no existing signature and no existing service behaviour.
    DISPATCH_CONTROLLER,
    CENTRE_MANAGER,
    MAILROOM_OFFICER,
    SECURITY_OFFICER,

    // Emergency Mass Notification user classes (SRS S174). Added for the S174 slice; adding enum
    // constants changes no existing signature and no existing service behaviour.
    EMERGENCY_COORDINATOR,
    SOC_OPERATOR,
    SECURITY_DIRECTOR,
    HSE_MANAGER,

    // Visitor Management user classes (SRS S160). Added for the S160 slice; adding enum
    // constants changes no existing signature and no existing service behaviour.
    VISITOR_HOST,
    RECEPTION_OFFICER,

    // HSE Incident / Near-Miss Reporting user classes (SRS S163). Added for the S163 slice; adding
    // enum constants changes no existing signature and no existing service behaviour. HSE_MANAGER,
    // COMPLIANCE_OFFICER, SECURITY_DIRECTOR and SFL_ADMIN already exist and are reused rather
    // than duplicated - see IncidentPermissionMatrix.
    INCIDENT_INVESTIGATOR,

    // Physical Access Control Integration user classes (SRS S160a). Added for the S160a slice; adding
    // enum constants changes no existing signature and no existing service behaviour. SECURITY_DIRECTOR,
    // SOC_OPERATOR, INTEGRATION_ENGINEER, COMPLIANCE_OFFICER, EMERGENCY_COORDINATOR and
    // SFL_ADMIN already exist and are reused rather than duplicated - see AccessControlPermissionMatrix.
    ACCESS_CONTROL_ADMINISTRATOR,

    // Phase 2 IFIMP user classes (SRS CLET/DTI/CL9/SFL/SRS/2026/002 §2.3). Added for S156, S157, S158,
    // S173 and S176; adding enum constants changes no existing signature and no existing service
    // behaviour. Only user classes the SRS names as their own role are added: the Cleaning Supervisor
    // and Facilities Officer personas map to FACILITIES_MANAGER, a Unit Head raising a space-change
    // request and an occupant raising a cleaning request map to IFIMP_REQUESTER - see
    // FacilitiesPermissionMatrix for each grant and the user story behind it.
    FACILITIES_ENGINEER,
    ENERGY_SUSTAINABILITY_OFFICER,
    SPACE_PLANNING_OFFICER,
    CONSTRUCTION_PROJECT_MANAGER,
    EVENT_LOGISTICS_COORDINATOR

    // Intrusion Detection & Alarm Monitoring (SRS S162) needed no new role: SECURITY_DIRECTOR,
    // SOC_OPERATOR, COMMAND_ROLE (the NECC escalation target), INTEGRATION_ENGINEER and
    // SFL_ADMIN already name every user story S162-01..05 describes - see IntrusionPermissionMatrix.
}