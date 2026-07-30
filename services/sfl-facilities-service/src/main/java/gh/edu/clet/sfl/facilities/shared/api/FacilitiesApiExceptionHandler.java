package gh.edu.clet.sfl.facilities.shared.api;

import gh.edu.clet.sfl.common.api.ApiError;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.AuthorizationException;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every failure to the platform envelope and the status its SRS error state implies.
 *
 * <p>The envelope is {@code ApiResponse<T>} — {@code {data, error}} — because that is what the other
 * 35 controllers in this platform emit and what the dashboard's single API client parses. S152
 * originally returned bare payloads with its own error shape; the client returns {@code envelope.data}
 * and would have read every successful response as {@code undefined}. Thirty-five controllers set the
 * convention, so the five here were changed rather than the shared client taught a per-service policy.
 *
 * <p>Field errors travel in {@code data}, which is where {@code FleetApiError.fromEnvelope} looks for
 * them — an array there becomes the form's per-field messages.
 *
 * <p>The status table this encodes:
 *
 * <ul>
 *   <li>Unauthorised scope, restricted drilldown, no scope — <strong>403</strong></li>
 *   <li>Record not found — <strong>404</strong></li>
 *   <li>Duplicate identifier, version conflict, idempotency key conflict — <strong>409</strong></li>
 *   <li>Invalid transition, readiness blocked, readiness locked, mode transition — <strong>422</strong></li>
 *   <li>Audit chain failure — <strong>500</strong></li>
 *   <li>Everything else — <strong>400</strong></li>
 * </ul>
 *
 * <p>422 rather than 400 for the domain-rule refusals is deliberate: the request was well-formed and
 * the server understood it — it is the estate's current state that forbids it. A client that retried a
 * 400 after fixing its payload would retry a 422 forever.
 */
@RestControllerAdvice
class FacilitiesApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FacilitiesApiExceptionHandler.class);

    private static final Map<FacilitiesErrorCode, HttpStatus> STATUSES = Map.ofEntries(
            Map.entry(FacilitiesErrorCode.UNAUTHORIZED_SCOPE, HttpStatus.FORBIDDEN),
            Map.entry(FacilitiesErrorCode.UNAUTHORIZED_APPROVAL, HttpStatus.FORBIDDEN),
            Map.entry(FacilitiesErrorCode.RESTRICTED_DRILLDOWN, HttpStatus.FORBIDDEN),
            Map.entry(FacilitiesErrorCode.NO_SCOPE, HttpStatus.FORBIDDEN),
            Map.entry(FacilitiesErrorCode.RECORD_NOT_FOUND, HttpStatus.NOT_FOUND),
            Map.entry(FacilitiesErrorCode.DUPLICATE_IDENTIFIER, HttpStatus.CONFLICT),
            Map.entry(FacilitiesErrorCode.VERSION_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(FacilitiesErrorCode.IDEMPOTENCY_KEY_CONFLICT, HttpStatus.CONFLICT),
            Map.entry(FacilitiesErrorCode.INVALID_STATE_TRANSITION, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(FacilitiesErrorCode.READINESS_BLOCKED, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(FacilitiesErrorCode.READINESS_LOCKED, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(FacilitiesErrorCode.OPERATING_MODE_TRANSITION_INVALID, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(FacilitiesErrorCode.CLOSURE_EVIDENCE_MISSING, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(FacilitiesErrorCode.INVALID_PARENT_REFERENCE, HttpStatus.BAD_REQUEST),
            Map.entry(FacilitiesErrorCode.AUDIT_CHAIN_FAILURE, HttpStatus.INTERNAL_SERVER_ERROR));

    /** One field's rejection, shaped as the dashboard's form binding expects. */
    public record FieldErrorResponse(String field, String message, Object rejectedValue) {
    }

    @ExceptionHandler(FacilitiesException.class)
    ResponseEntity<ApiResponse<Object>> facilitiesFailure(FacilitiesException exception,
            HttpServletRequest request) {
        HttpStatus status = STATUSES.getOrDefault(exception.code(), HttpStatus.BAD_REQUEST);
        if (status.is5xxServerError()) {
            log.error("S152 failure {}: {}", exception.code(), exception.getMessage(), exception);
        }
        return respond(status, exception.code(), exception.getMessage(), null, request);
    }

    /**
     * The pre-S152 authorisation exception from {@code sfl-service-common}.
     *
     * <p>Still thrown by {@code WorkOrderService}, which authorises through {@code AuthorizationPolicy}
     * directly. Mapped to the same envelope and the same {@code UNAUTHORIZED_SCOPE} code, so a client
     * cannot tell which module refused it.
     */
    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<ApiResponse<Object>> forbidden(AuthorizationException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, FacilitiesErrorCode.UNAUTHORIZED_SCOPE, exception.getMessage(),
                null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> validationFailure(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage(),
                        error.getRejectedValue()))
                .toList();
        String message = fieldErrors.isEmpty()
                ? FacilitiesErrorCode.VALIDATION_FAILED.defaultMessage()
                : fieldErrors.get(0).field() + ": " + fieldErrors.get(0).message();
        return respond(HttpStatus.BAD_REQUEST, FacilitiesErrorCode.VALIDATION_FAILED, message, fieldErrors,
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Object>> typeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, FacilitiesErrorCode.VALIDATION_FAILED,
                exception.getName() + " is not a valid value", null, request);
    }

    /**
     * A database constraint the application-level check did not catch first.
     *
     * <p>Two very different failures arrive as one exception type, and reporting them alike sends a
     * caller down the wrong path entirely. Discriminated on the SQL state rather than the message text,
     * so it does not depend on a driver's wording: 23503 is a foreign-key violation — a reference to
     * something that does not exist — and anything else here is a uniqueness race the pre-write check
     * lost.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Object>> integrityViolation(DataIntegrityViolationException exception,
            HttpServletRequest request) {
        Throwable cause = exception.getMostSpecificCause();
        log.warn("Database constraint rejected a write: {}", cause.getMessage());

        String sqlState = cause instanceof java.sql.SQLException sqlException ? sqlException.getSQLState() : null;
        FacilitiesErrorCode code = "23503".equals(sqlState)
                ? FacilitiesErrorCode.INVALID_PARENT_REFERENCE
                : FacilitiesErrorCode.DUPLICATE_IDENTIFIER;
        return respond(STATUSES.getOrDefault(code, HttpStatus.CONFLICT), code, code.defaultMessage(), null,
                request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Object>> optimisticLock(OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, FacilitiesErrorCode.VERSION_CONFLICT,
                FacilitiesErrorCode.VERSION_CONFLICT.defaultMessage(), null, request);
    }

    /**
     * Legacy argument failures.
     *
     * <p>The pre-existing {@code masterdata} and {@code maintenance} code signals invalid input with
     * {@link IllegalArgumentException}. Kept on 400 so this change does not alter the status of an
     * endpoint that already shipped.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> invalidRequest(IllegalArgumentException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, FacilitiesErrorCode.VALIDATION_FAILED, exception.getMessage(),
                null, request);
    }

    /** Legacy state failures from {@code maintenance}, which predate the typed exceptions. */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> invalidState(IllegalStateException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, FacilitiesErrorCode.INVALID_STATE_TRANSITION,
                exception.getMessage(), null, request);
    }

    private ResponseEntity<ApiResponse<Object>> respond(HttpStatus status, FacilitiesErrorCode code,
            String message, Object data, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.currentCorrelationId(request);
        ApiError error = ApiError.of(code.name(), message, correlationId);
        return ResponseEntity.status(status)
                .header(FacilitiesActorResolver.HEADER_CORRELATION_ID, correlationId)
                .body(new ApiResponse<>(data, error));
    }
}
