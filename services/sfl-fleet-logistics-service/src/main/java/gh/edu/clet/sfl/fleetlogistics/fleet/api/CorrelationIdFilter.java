package gh.edu.clet.sfl.fleetlogistics.fleet.api;

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
 * Establishes the correlation ID for the request and propagates it to logs, the audit trail, the outbox
 * and the response.
 *
 * <p>A caller-supplied {@code X-Correlation-ID} is honoured so a trace spans the gateway, this service,
 * its events and its consumers; otherwise one is minted. The W3C {@code traceparent} header, when
 * present, is placed in the MDC so the outbox can carry trace context onto the broker.
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
        String correlationId = request.getHeader(FleetActorResolver.HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        } else {
            correlationId = correlationId.strip();
        }
        String traceParent = request.getHeader(HEADER_TRACE_PARENT);

        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        if (traceParent != null && !traceParent.isBlank()) {
            MDC.put(MDC_TRACE_PARENT, traceParent.strip());
        }
        response.setHeader(FleetActorResolver.HEADER_CORRELATION_ID, correlationId);
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
        String header = request.getHeader(FleetActorResolver.HEADER_CORRELATION_ID);
        return header == null || header.isBlank() ? UUID.randomUUID().toString() : header.strip();
    }
}
