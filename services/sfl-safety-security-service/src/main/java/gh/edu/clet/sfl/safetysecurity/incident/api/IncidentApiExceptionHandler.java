package gh.edu.clet.sfl.safetysecurity.incident.api;

import gh.edu.clet.sfl.common.api.ApiError;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.AuthorizationException;
import gh.edu.clet.sfl.safetysecurity.incident.application.service.CorrectiveActionService;
import gh.edu.clet.sfl.safetysecurity.incident.application.service.IncidentClosureService;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentErrorCode;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates S163 failures into the shared {@code ApiResponse}/{@code ApiError} envelope. Mirrors
 * {@code VisitorApiExceptionHandler}, scoped to this module's own controllers for the same reason:
 * two unscoped {@code @RestControllerAdvice} beans handling the same generic exception types is
 * undefined which one Spring picks, so {@code basePackages} + {@code @Order(HIGHEST_PRECEDENCE)}
 * makes this bean the one that wins for {@code incident.api} controllers specifically.
 */
@RestControllerAdvice(basePackages = "gh.edu.clet.sfl.safetysecurity.incident.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
class IncidentApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(IncidentApiExceptionHandler.class);
    private final IncidentActorResolver actors;

    IncidentApiExceptionHandler(IncidentActorResolver actors) {
        this.actors = actors;
    }

    @ExceptionHandler(IncidentException.class)
    ResponseEntity<ApiResponse<Object>> domain(IncidentException exception, HttpServletRequest request) {
        IncidentErrorCode code = exception.errorCode();
        HttpStatus status = HttpStatus.valueOf(code.httpStatus());
        if (status.is5xxServerError()) {
            log.error("Incident request failed: {} {}", code, exception.getMessage(), exception);
        }
        Object details = exception.details().isEmpty() ? null : exception.details();
        return respond(status, code.code(), exception.getMessage(), details, request);
    }

    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<ApiResponse<Object>> authorization(AuthorizationException exception, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, IncidentErrorCode.INCIDENT_UNAUTHORIZED_SCOPE.code(),
                IncidentErrorCode.INCIDENT_UNAUTHORIZED_SCOPE.message(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> beanValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<Map<String, Object>> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.<String, Object>of("field", e.getField(), "message",
                        String.valueOf(e.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, IncidentErrorCode.INCIDENT_VALIDATION_FAILED.code(),
                IncidentErrorCode.INCIDENT_VALIDATION_FAILED.message(), fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ApiResponse<Object>> malformed(Exception exception, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, IncidentErrorCode.INCIDENT_VALIDATION_FAILED.code(),
                exception.getMessage(), null, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Object>> optimisticLock(OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, IncidentErrorCode.INCIDENT_RECORD_VERSION_CONFLICT.code(),
                IncidentErrorCode.INCIDENT_RECORD_VERSION_CONFLICT.message(), null, request);
    }

    /**
     * Postgres reports a {@code SERIALIZABLE} transaction it aborted to break a dependency cycle
     * (SQLSTATE 40001) via this exception type, translated by Spring's JDBC exception translator -
     * not {@link OptimisticLockingFailureException}, which is a sibling branch of {@code
     * DataAccessException}, not a supertype of this. {@link IncidentClosureService#close} and {@link
     * CorrectiveActionService#open} are the two callers that run at {@code SERIALIZABLE}; the same
     * "reload and retry" answer applies regardless of which of the two lost the race.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ApiResponse<Object>> concurrencyFailure(ConcurrencyFailureException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, IncidentErrorCode.INCIDENT_RECORD_VERSION_CONFLICT.code(),
                IncidentErrorCode.INCIDENT_RECORD_VERSION_CONFLICT.message(), null, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> illegalArgument(IllegalArgumentException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, IncidentErrorCode.INCIDENT_VALIDATION_FAILED.code(),
                exception.getMessage(), null, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> illegalState(IllegalStateException exception, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, IncidentErrorCode.INCIDENT_INVALID_STATE_TRANSITION.code(),
                exception.getMessage(), null, request);
    }

    private ResponseEntity<ApiResponse<Object>> respond(HttpStatus status, String code, String message, Object data,
            HttpServletRequest request) {
        String correlationId = actors.resolveCorrelationId(request);
        ApiError error = ApiError.of(code, message, correlationId);
        return ResponseEntity.status(status)
                .header(IncidentActorResolver.HEADER_CORRELATION_ID, correlationId)
                .body(new ApiResponse<>(data, error));
    }
}
