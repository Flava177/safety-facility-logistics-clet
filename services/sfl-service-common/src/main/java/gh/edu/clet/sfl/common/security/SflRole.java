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
    AUDITOR,
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
    LOGISTICS_COORDINATOR,
    CENTRE_MANAGER,
    MAILROOM_OFFICER,
    SECURITY_OFFICER
}