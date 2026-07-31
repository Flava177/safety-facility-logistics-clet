package gh.edu.clet.sfl.facilities.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the correlation ID for the request and propagates it to logs, the audit trail, the
 * outbox and the response (SRS-SFL-S152-01 "audit correlation ID"; NFR 23.5).
 *
 * <p>A caller-supplied {@code X-Correlation-ID} is honoured so one trace spans the gateway, this
 * service, its events and its consumers; otherwise one is minted here rather than left null, because
 * a state change without a correlation ID is one nobody can follow across the platform.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String CORRELATION_ID_ATTRIBUTE = "sfl.correlationId";
    static final String MDC_CORRELATION_ID = "correlationId";
    static final String MDC_TRACE_PARENT = "traceparent";
    private static final String HEADER_TRACE_PARENT = "traceparent";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(FacilitiesActorResolver.HEADER_CORRELATION_ID);
        correlationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.strip();
        String traceParent = request.getHeader(HEADER_TRACE_PARENT);

        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        if (traceParent != null && !traceParent.isBlank()) {
            MDC.put(MDC_TRACE_PARENT, traceParent.strip());
        }
        response.setHeader(FacilitiesActorResolver.HEADER_CORRELATION_ID, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_TRACE_PARENT);
        }
    }

    /** The correlation ID for the current request, minting one if the filter did not run (unit tests). */
    static String currentCorrelationId(HttpServletRequest request) {
        Object attribute = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        if (attribute instanceof String correlationId && !correlationId.isBlank()) {
            return correlationId;
        }
        String header = request.getHeader(FacilitiesActorResolver.HEADER_CORRELATION_ID);
        return header == null || header.isBlank() ? UUID.randomUUID().toString() : header.strip();
    }
}
