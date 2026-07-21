package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/** What a fleet workflow item is about. Drives which SLA rule applies. */
public enum FleetWorkflowType {
    VEHICLE_DEFECT,
    COMPLIANCE_RENEWAL,
    SERVICE_SCHEDULING,
    DRIVER_ELIGIBILITY,
    TRIP_EXCEPTION,
    INTEGRATION_FAILURE,
    EVIDENCE_REVIEW,
    ODOMETER_CORRECTION
}
