package gh.edu.clet.sfl.safetysecurity.visitor.api;

import gh.edu.clet.sfl.common.api.ApiError;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.AuthorizationException;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
 * Translates S160 failures into the shared {@code ApiResponse}/{@code ApiError} envelope. Mirrors
 * {@code EmergencyApiExceptionHandler}, scoped to this module's own controllers.
 *
 * <p>{@code EmergencyApiExceptionHandler} is a global (unscoped) {@code @RestControllerAdvice} that
 * already claims the same generic exception types this class does (bean validation, optimistic
 * locking, malformed requests). Two unscoped advice beans handling the same type is undefined which
 * one Spring picks, and picking Emergency's would mean a visitor request failing validation gets an
 * {@code EMERGENCY_VALIDATION_FAILED} code back. {@code basePackages} makes this bean a candidate
 * only for controllers in {@code visitor.api}; {@code @Order(HIGHEST_PRECEDENCE)} makes it win there
 * over the unscoped global one. Outside this package nothing changes - this bean is never even a
 * candidate, so S174's handling is untouched.
 */
@RestControllerAdvice(basePackages = "gh.edu.clet.sfl.safetysecurity.visitor.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
class VisitorApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(VisitorApiExceptionHandler.class);
    private final VisitorActorResolver actors;

    VisitorApiExceptionHandler(VisitorActorResolver actors) {
        this.actors = actors;
    }

    @ExceptionHandler(VisitorException.class)
    ResponseEntity<ApiResponse<Object>> domain(VisitorException exception, HttpServletRequest request) {
        VisitorErrorCode code = exception.errorCode();
        HttpStatus status = HttpStatus.valueOf(code.httpStatus());
        if (status.is5xxServerError()) {
            log.error("Visitor request failed: {} {}", code, exception.getMessage(), exception);
        }
        Object details = exception.details().isEmpty() ? null : exception.details();
        return respond(status, code.code(), exception.getMessage(), details, request);
    }

    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<ApiResponse<Object>> authorization(AuthorizationException exception, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, VisitorErrorCode.VISITOR_UNAUTHORIZED_SCOPE.code(),
                VisitorErrorCode.VISITOR_UNAUTHORIZED_SCOPE.message(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> beanValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<Map<String, Object>> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.<String, Object>of("field", e.getField(), "message",
                        String.valueOf(e.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, VisitorErrorCode.VISITOR_VALIDATION_FAILED.code(),
                VisitorErrorCode.VISITOR_VALIDATION_FAILED.message(), fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ApiResponse<Object>> malformed(Exception exception, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, VisitorErrorCode.VISITOR_VALIDATION_FAILED.code(),
                exception.getMessage(), null, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Object>> optimisticLock(OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, VisitorErrorCode.VISITOR_RECORD_VERSION_CONFLICT.code(),
                VisitorErrorCode.VISITOR_RECORD_VERSION_CONFLICT.message(), null, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> illegalArgument(IllegalArgumentException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, VisitorErrorCode.VISITOR_VALIDATION_FAILED.code(),
                exception.getMessage(), null, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> illegalState(IllegalStateException exception, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, VisitorErrorCode.VISITOR_INVALID_STATE_TRANSITION.code(),
                exception.getMessage(), null, request);
    }

    private ResponseEntity<ApiResponse<Object>> respond(HttpStatus status, String code, String message, Object data,
            HttpServletRequest request) {
        String correlationId = actors.resolveCorrelationId(request);
        ApiError error = ApiError.of(code, message, correlationId);
        return ResponseEntity.status(status)
                .header(VisitorActorResolver.HEADER_CORRELATION_ID, correlationId)
                .body(new ApiResponse<>(data, error));
    }
}
