package gh.edu.clet.sfl.fleetlogistics.fleet.api;

import gh.edu.clet.sfl.common.api.ApiError;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.AuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.response.FieldErrorResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAuditService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetDomainException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Translates fleet failures into the shared {@code ApiResponse}/{@code ApiError} envelope.
 *
 * <p>Two rules matter here. First, the message for an SRS-defined code is the SRS <em>Error States</em>
 * wording, verbatim — that text is contract. Second, authorisation denials are audited from this handler
 * rather than from the access policy: the failed request's transaction has already rolled back by the
 * time the handler runs, so the denial record survives (SRS-SFL-S166-03).
 *
 * <p>Scoped to the fleet controllers only, so the other services' handlers are unaffected.
 */
@RestControllerAdvice(basePackages = "gh.edu.clet.sfl.fleetlogistics")
class FleetApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FleetApiExceptionHandler.class);

    private final FleetAuditService auditService;
    private final FleetActorResolver actorResolver;

    FleetApiExceptionHandler(FleetAuditService auditService, FleetActorResolver actorResolver) {
        this.auditService = auditService;
        this.actorResolver = actorResolver;
    }

    @ExceptionHandler(FleetDomainException.class)
    ResponseEntity<ApiResponse<Object>> domainFailure(FleetDomainException exception, HttpServletRequest request) {
        FleetErrorCode code = exception.errorCode();
        if (isAuthorizationDenial(code)) {
            auditDenial(exception, request);
        }
        HttpStatus status = FleetHttpStatusMapper.statusFor(code);
        if (status.is5xxServerError()) {
            log.error("Fleet request failed: {} {}", code, exception.getMessage(), exception);
        }
        return respond(status, code, exception.getMessage(),
                exception.details().isEmpty() ? null : exception.details(), request);
    }

    /** Authorisation failures raised by the shared kernel rather than by the fleet access policy. */
    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<ApiResponse<Object>> commonAuthorizationFailure(AuthorizationException exception,
            HttpServletRequest request) {
        auditService.recordAuthorizationDenial(actorResolver.resolve(request), null,
                request.getRequestURI(), null, null, exception.getMessage());
        return respond(HttpStatus.FORBIDDEN, FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE,
                FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE.message(), null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Object>> beanValidationFailure(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage(),
                        error.getRejectedValue()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, FleetErrorCode.FLEET_VALIDATION_FAILED,
                FleetErrorCode.FLEET_VALIDATION_FAILED.message(), fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Object>> constraintViolation(ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(String.valueOf(violation.getPropertyPath()),
                        violation.getMessage(), violation.getInvalidValue()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, FleetErrorCode.FLEET_VALIDATION_FAILED,
                FleetErrorCode.FLEET_VALIDATION_FAILED.message(), fieldErrors, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ApiResponse<Object>> malformedRequest(Exception exception, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, FleetErrorCode.FLEET_VALIDATION_FAILED,
                FleetErrorCode.FLEET_VALIDATION_FAILED.message(),
                List.of(new FieldErrorResponse("request", exception.getMessage(), null)), request);
    }

    /**
     * A JPA optimistic-lock failure surfaces as the stable version-conflict error rather than a 500, so
     * a client can safely reload and retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Object>> optimisticLockFailure(OptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, FleetErrorCode.FLEET_RECORD_VERSION_CONFLICT,
                FleetErrorCode.FLEET_RECORD_VERSION_CONFLICT.message(), null, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiResponse<Object>> illegalArgument(IllegalArgumentException exception,
            HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, FleetErrorCode.FLEET_VALIDATION_FAILED, exception.getMessage(),
                null, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Object>> illegalState(IllegalStateException exception, HttpServletRequest request) {
        log.error("Fleet request failed with an unexpected state error", exception);
        return respond(HttpStatus.CONFLICT, FleetErrorCode.FLEET_INVALID_STATE_TRANSITION, exception.getMessage(),
                null, request);
    }

    private void auditDenial(FleetDomainException exception, HttpServletRequest request) {
        Map<String, Object> details = exception.details();
        auditService.recordAuthorizationDenial(
                actorResolver.resolve(request),
                asString(details.get("siteCode")),
                details.containsKey("resourceType") ? asString(details.get("resourceType")) : request.getRequestURI(),
                asString(details.get("resourceId")),
                asString(details.get("requiredPermission")),
                exception.errorCode().name());
    }

    private ResponseEntity<ApiResponse<Object>> respond(HttpStatus status, FleetErrorCode code, String message,
            Object data, HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.currentCorrelationId(request);
        ApiError error = ApiError.of(code.code(), message, correlationId);
        return ResponseEntity.status(status)
                .header(FleetActorResolver.HEADER_CORRELATION_ID, correlationId)
                .body(new ApiResponse<>(data, error));
    }

    private static boolean isAuthorizationDenial(FleetErrorCode code) {
        return code == FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE
                || code == FleetErrorCode.FLEET_UNAUTHORIZED_APPROVAL
                || code == FleetErrorCode.FLEET_DASHBOARD_NO_SCOPE
                || code == FleetErrorCode.FLEET_DASHBOARD_RESTRICTED_DRILLDOWN
                || code == FleetErrorCode.FLEET_EXPORT_NOT_APPROVED;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
