package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Maps a domain error code to an HTTP status.
 *
 * <p>Transport lives here, not in the domain, so the aggregates stay free of HTTP concepts. The
 * mapping follows the S166 API contract:
 * {@code 400} malformed · {@code 401} unauthenticated or bad signature · {@code 403} authorisation ·
 * {@code 404} not found · {@code 409} conflict, state or version · {@code 422} business validation ·
 * {@code 503} integration unavailable.
 */
final class FleetHttpStatusMapper {

    private FleetHttpStatusMapper() {
    }

    static HttpStatus statusFor(FleetErrorCode code) {
        return switch (code) {
            case FLEET_UNAUTHORIZED_SCOPE,
                 FLEET_UNAUTHORIZED_APPROVAL,
                 FLEET_EXPORT_NOT_APPROVED,
                 FLEET_DASHBOARD_NO_SCOPE,
                 FLEET_DASHBOARD_RESTRICTED_DRILLDOWN,
                 FLEET_INTEGRATION_SOURCE_NOT_ALLOWED -> HttpStatus.FORBIDDEN;

            case FLEET_INTEGRATION_INVALID_SIGNATURE -> HttpStatus.UNAUTHORIZED;

            case FLEET_RECORD_NOT_FOUND -> HttpStatus.NOT_FOUND;

            case FLEET_DUPLICATE_IDENTIFIER,
                 FLEET_RECORD_VERSION_CONFLICT,
                 FLEET_INVALID_STATE_TRANSITION,
                 FLEET_ASSIGNMENT_CONFLICT,
                 FLEET_SLA_BREACH,
                 FLEET_AUDIT_CHAIN_FAILURE,
                 FLEET_IDEMPOTENCY_KEY_CONFLICT,
                 FUEL_POLICY_PERIOD_OVERLAP,
                 FUEL_IMPORT_ALREADY_PROCESSED -> HttpStatus.CONFLICT;

            case FLEET_MISSING_SITE_SCOPE,
                 FLEET_CLOSURE_EVIDENCE_MISSING,
                 FLEET_RETENTION_CLASS_MISSING,
                 FLEET_INTEGRATION_SCHEMA_INVALID,
                 FLEET_READINESS_BLOCKED,
                 FLEET_DRIVER_INELIGIBLE,
                 FLEET_ODOMETER_REGRESSION,
                 FLEET_ARCHIVED_RECORD_IMMUTABLE,
                 FLEET_IDEMPOTENCY_KEY_REQUIRED -> HttpStatus.UNPROCESSABLE_ENTITY;

            case FLEET_INTEGRATION_NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;

            // A duplicate integration message is safely ignored, not an error for the sender.
            case FLEET_INTEGRATION_DUPLICATE_MESSAGE,
                 FLEET_DASHBOARD_DATA_STALE -> HttpStatus.OK;

            case FLEET_VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
        };
    }
}
