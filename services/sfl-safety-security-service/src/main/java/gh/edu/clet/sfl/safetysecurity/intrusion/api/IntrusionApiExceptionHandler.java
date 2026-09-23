package gh.edu.clet.sfl.safetysecurity.intrusion.api;

import gh.edu.clet.sfl.common.api.ApiError;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.AuthorizationException;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionErrorCode;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionException;
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

/** Translates S162 failures into the shared {@code ApiResponse}/{@code ApiError} envelope. Scoped to
 * this module's own controllers - mirrors {@code AccessControlApiExceptionHandler}. */
@RestControllerAdvice(basePackages = "gh.edu.clet.sfl.safetysecurity.intrusion.api")
@Order(Ordered.HIGHEST_PRECEDENCE)
class IntrusionApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(IntrusionApiExceptionHandler.class);
    private final IntrusionActorResolver actors;

    IntrusionApiExceptionHandler(IntrusionActorResolver actors) {
        this.actors = actors;
    }

    @ExceptionHandler(IntrusionException.class)
    ResponseEntity<ApiResponse<Object>> domain(IntrusionException exception, HttpServletRequest request) {
        IntrusionErrorCode code = exception.errorCode();
        HttpStatus status = HttpStatus.valueOf(code.httpStatus());
        if (status.is5xxServerError()) {
            log.error("Intrusion request failed: {} {}", code, exception.getMessage(), exception);
        }
        Object details = exception.details().isEmpty() ? null : exception.details();
        return respond(status, code.code(), exception.getMessage(), details, request);
    }

    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<ApiResponse<Object>> authorization(AuthorizationException exception, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, IntrusionErrorCode.INTRUSION_UNAUTHORIZED_SCOPE.code(),
                IntrusionErrorCode.INTRUSION_UNAUTHORIZED_SCOPE.message(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> beanValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<Map<String, Object>> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.<String, Object>of("field", e.getField(), "message",
                        String.valueOf(e.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, IntrusionErrorCode.INTRUSION_VALIDATION_FAILED.code(),
                IntrusionErrorCode.INTRUSION_VALIDATION_FAILED.message(), fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ApiResponse<Object>> malformed(Exception exception, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, IntrusionErrorCode.INTRUSION_VALIDATION_FAILED.code(),
                exception.getMessage(), null, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Object>> optimisticLock(OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, IntrusionErrorCode.INTRUSION_RECORD_VERSION_CONFLICT.code(),
                IntrusionErrorCode.INTRUSION_RECORD_VERSION_CONFLICT.message(), null, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> illegalArgument(IllegalArgumentException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, IntrusionErrorCode.INTRUSION_VALIDATION_FAILED.code(),
                exception.getMessage(), null, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> illegalState(IllegalStateException exception, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, IntrusionErrorCode.INTRUSION_INVALID_STATE_TRANSITION.code(),
                exception.getMessage(), null, request);
    }

    private ResponseEntity<ApiResponse<Object>> respond(HttpStatus status, String code, String message, Object data,
            HttpServletRequest request) {
        String correlationId = actors.resolveCorrelationId(request);
        ApiError error = ApiError.of(code, message, correlationId);
        return ResponseEntity.status(status)
                .header(IntrusionActorResolver.HEADER_CORRELATION_ID, correlationId)
                .body(new ApiResponse<>(data, error));
    }
}
