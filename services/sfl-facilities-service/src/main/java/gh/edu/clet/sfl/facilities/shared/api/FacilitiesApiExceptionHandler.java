package gh.edu.clet.sfl.facilities.shared.api;

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
 * Maps every failure to the uniform envelope and the status its SRS error state implies.
 *
 * <p>The mapping is declared once here rather than at each throw site, so a rule added to the domain
 * cannot ship with an inconsistent status. The table it encodes:
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
 *
 * <p>The pre-S152 handlers for {@link IllegalArgumentException} and {@link AuthorizationException} are
 * kept, mapped to the same codes, so the endpoints that already shipped do not change status.
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

    @ExceptionHandler(FacilitiesException.class)
    ResponseEntity<ApiErrorResponse> facilitiesFailure(FacilitiesException exception, HttpServletRequest request) {
        HttpStatus status = STATUSES.getOrDefault(exception.code(), HttpStatus.BAD_REQUEST);
        if (status.is5xxServerError()) {
            log.error("S152 failure {}: {}", exception.code(), exception.getMessage(), exception);
        }
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), exception.code(), exception.getMessage(),
                        correlationId(request)));
    }

    /**
     * The pre-S152 authorisation exception from {@code sfl-service-common}.
     *
     * <p>Still thrown by {@code WorkOrderService}, which authorises through {@code AuthorizationPolicy}
     * directly. Mapped to the same envelope and the same {@code UNAUTHORIZED_SCOPE} code, so a client
     * cannot tell which module refused it.
     */
    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<ApiErrorResponse> forbidden(AuthorizationException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiErrorResponse.of(HttpStatus.FORBIDDEN.value(), FacilitiesErrorCode.UNAUTHORIZED_SCOPE,
                        exception.getMessage(), correlationId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validationFailure(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ApiErrorResponse.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        String message = fieldErrors.isEmpty()
                ? FacilitiesErrorCode.VALIDATION_FAILED.defaultMessage()
                : fieldErrors.get(0).field() + ": " + fieldErrors.get(0).message();
        return ResponseEntity.badRequest().body(ApiErrorResponse.validation(HttpStatus.BAD_REQUEST.value(),
                message, correlationId(request), fieldErrors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> typeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                FacilitiesErrorCode.VALIDATION_FAILED,
                exception.getName() + " is not a valid value", correlationId(request)));
    }

    /**
     * A database constraint the application-level check did not catch first.
     *
     * <p>Two very different failures arrive as one exception type, and reporting them alike sends a
     * caller down the wrong path entirely:
     *
     * <ul>
     *   <li><strong>Unique violation</strong> — a duplicate that lost a race against a concurrent
     *       write. The commands check first, but the constraint is the real guarantee, and losing that
     *       race should still be {@code DUPLICATE_IDENTIFIER} rather than a 500.</li>
     *   <li><strong>Foreign key violation</strong> — a reference to something that does not exist. That
     *       is not a duplicate, and telling a client "this record already exists" when the truth is
     *       "the record you pointed at does not" is worse than saying nothing. Reported as
     *       {@code INVALID_PARENT_REFERENCE}.</li>
     * </ul>
     *
     * <p>Discriminated on the SQL state rather than the message text, so it does not depend on a
     * driver's wording: 23503 is a foreign-key violation, 23505 is unique.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> integrityViolation(DataIntegrityViolationException exception,
            HttpServletRequest request) {
        Throwable cause = exception.getMostSpecificCause();
        log.warn("Database constraint rejected a write: {}", cause.getMessage());

        String sqlState = cause instanceof java.sql.SQLException sqlException ? sqlException.getSQLState() : null;
        FacilitiesErrorCode code = "23503".equals(sqlState)
                ? FacilitiesErrorCode.INVALID_PARENT_REFERENCE
                : FacilitiesErrorCode.DUPLICATE_IDENTIFIER;
        HttpStatus status = STATUSES.getOrDefault(code, HttpStatus.CONFLICT);

        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), code, code.defaultMessage(), correlationId(request)));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiErrorResponse> optimisticLock(OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(HttpStatus.CONFLICT.value(), FacilitiesErrorCode.VERSION_CONFLICT,
                        FacilitiesErrorCode.VERSION_CONFLICT.defaultMessage(), correlationId(request)));
    }

    /**
     * Legacy argument failures.
     *
     * <p>The pre-existing {@code masterdata} and {@code maintenance} code signals invalid input with
     * {@link IllegalArgumentException}. Kept on 400 so this change does not alter the status of an
     * endpoint that already shipped.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> invalidRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(),
                FacilitiesErrorCode.VALIDATION_FAILED, exception.getMessage(), correlationId(request)));
    }

    /** Legacy state failures from {@code maintenance}, which predate the typed exceptions. */
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiErrorResponse> invalidState(IllegalStateException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorResponse.of(HttpStatus.UNPROCESSABLE_ENTITY.value(),
                        FacilitiesErrorCode.INVALID_STATE_TRANSITION, exception.getMessage(),
                        correlationId(request)));
    }

    private static String correlationId(HttpServletRequest request) {
        return CorrelationIdFilter.currentCorrelationId(request);
    }
}
