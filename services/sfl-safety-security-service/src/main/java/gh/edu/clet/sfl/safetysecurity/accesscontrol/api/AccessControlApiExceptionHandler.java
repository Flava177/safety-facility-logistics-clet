package gh.edu.clet.sfl.safetysecurity.accesscontrol.api;

import gh.edu.clet.sfl.common.api.ApiError;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.AuthorizationException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlErrorCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
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

/** Translates S160a failures into the shared {@code ApiResponse}/{@code ApiError} envelope. Scoped to
 * this module's own controllers - see {@code VisitorApiExceptionHandler}'s javadoc for why scoping matters. */
@RestControllerAdvice(basePackages = "gh.edu.clet.sfl.safetysecurity.accesscontrol.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
class AccessControlApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AccessControlApiExceptionHandler.class);
    private final AccessControlActorResolver actors;

    AccessControlApiExceptionHandler(AccessControlActorResolver actors) {
        this.actors = actors;
    }

    @ExceptionHandler(AccessControlException.class)
    ResponseEntity<ApiResponse<Object>> domain(AccessControlException exception, HttpServletRequest request) {
        AccessControlErrorCode code = exception.errorCode();
        HttpStatus status = HttpStatus.valueOf(code.httpStatus());
        if (status.is5xxServerError()) {
            log.error("Access control request failed: {} {}", code, exception.getMessage(), exception);
        }
        Object details = exception.details().isEmpty() ? null : exception.details();
        return respond(status, code.code(), exception.getMessage(), details, request);
    }

    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<ApiResponse<Object>> authorization(AuthorizationException exception, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, AccessControlErrorCode.ACCESS_CONTROL_UNAUTHORIZED_SCOPE.code(),
                AccessControlErrorCode.ACCESS_CONTROL_UNAUTHORIZED_SCOPE.message(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> beanValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<Map<String, Object>> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.<String, Object>of("field", e.getField(), "message",
                        String.valueOf(e.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, AccessControlErrorCode.ACCESS_CONTROL_VALIDATION_FAILED.code(),
                AccessControlErrorCode.ACCESS_CONTROL_VALIDATION_FAILED.message(), fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ApiResponse<Object>> malformed(Exception exception, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, AccessControlErrorCode.ACCESS_CONTROL_VALIDATION_FAILED.code(),
                exception.getMessage(), null, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Object>> optimisticLock(OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, AccessControlErrorCode.ACCESS_CONTROL_RECORD_VERSION_CONFLICT.code(),
                AccessControlErrorCode.ACCESS_CONTROL_RECORD_VERSION_CONFLICT.message(), null, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> illegalArgument(IllegalArgumentException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, AccessControlErrorCode.ACCESS_CONTROL_VALIDATION_FAILED.code(),
                exception.getMessage(), null, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> illegalState(IllegalStateException exception, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, AccessControlErrorCode.ACCESS_CONTROL_INVALID_STATE_TRANSITION.code(),
                exception.getMessage(), null, request);
    }

    private ResponseEntity<ApiResponse<Object>> respond(HttpStatus status, String code, String message, Object data,
            HttpServletRequest request) {
        String correlationId = actors.resolveCorrelationId(request);
        ApiError error = ApiError.of(code, message, correlationId);
        return ResponseEntity.status(status)
                .header(AccessControlActorResolver.HEADER_CORRELATION_ID, correlationId)
                .body(new ApiResponse<>(data, error));
    }
}
