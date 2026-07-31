package gh.edu.clet.sfl.facilities.shared.api;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import java.time.Instant;
import java.util.List;

/**
 * The uniform error envelope for every S152 failure.
 *
 * <p>The SRS names error states as prose per requirement; this is what makes them testable. {@code code}
 * is the contract — a client branches on it — while {@code message} is display text that may be
 * reworded. {@code correlationId} is echoed so a user reporting a failure and an engineer reading logs
 * are looking at the same request.
 *
 * <p>{@code fieldErrors} is populated only for validation failures, where naming the offending fields
 * is the difference between a form a user can fix and one they cannot.
 */
public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String correlationId,
        Instant timestamp,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    static ApiErrorResponse of(int status, FacilitiesErrorCode code, String message, String correlationId) {
        return new ApiErrorResponse(status, code.name(), message, correlationId, Instant.now(), List.of());
    }

    static ApiErrorResponse validation(int status, String message, String correlationId,
            List<FieldError> fieldErrors) {
        return new ApiErrorResponse(status, FacilitiesErrorCode.VALIDATION_FAILED.name(), message, correlationId,
                Instant.now(), List.copyOf(fieldErrors));
    }
}
