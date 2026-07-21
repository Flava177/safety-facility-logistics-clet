package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

/**
 * One field-level validation failure.
 *
 * <p>Carried in the {@code data} member of the shared {@code ApiResponse} envelope on validation
 * failures, so the envelope itself stays exactly as {@code sfl-service-common} defines it while
 * clients still receive per-field detail.
 */
public record FieldErrorResponse(String field, String message, Object rejectedValue) {
}
