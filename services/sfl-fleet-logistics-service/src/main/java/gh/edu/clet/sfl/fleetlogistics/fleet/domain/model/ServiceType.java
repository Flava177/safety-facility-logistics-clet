package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/** Kind of maintenance recorded on a service record (SRS-SFL-S166-01 "service history"). */
public enum ServiceType {
    ROUTINE_SERVICE,
    MAJOR_SERVICE,
    REPAIR,
    TYRE_REPLACEMENT,
    BODYWORK,
    DEFECT_RECTIFICATION,
    STATUTORY_INSPECTION
}
