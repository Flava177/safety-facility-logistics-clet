package gh.edu.clet.sfl.safetysecurity.emergency.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints this service's front doors once it is up.
 *
 * <p>The twin of the facilities and fleet reporters - see {@code FacilitiesStartupReporter} for why
 * all three do this, and why the dashboard line names 8093 rather than this service's own port.
 */
@Component
class SafetySecurityStartupReporter {

    private static final Logger log = LoggerFactory.getLogger(SafetySecurityStartupReporter.class);

    private final String port;
    private final String contextPath;
    private final String dashboardBaseUrl;

    SafetySecurityStartupReporter(
            @Value("${server.port:8092}") String port,
            @Value("${server.servlet.context-path:}") String contextPath,
            @Value("${sfl.dashboard.base-url:http://localhost:8093/ui}") String dashboardBaseUrl) {
        this.port = port;
        this.contextPath = contextPath;
        this.dashboardBaseUrl = dashboardBaseUrl.replaceAll("/+$", "");
    }

    @EventListener(ApplicationReadyEvent.class)
    void report() {
        String root = "http://localhost:" + port + contextPath;
        log.info("");
        log.info("  SFL Safety, Security & Emergency service (SSEMP) is ready");
        log.info("    API docs (Swagger) : {}", root + "/swagger-ui.html");
        log.info("    OpenAPI JSON       : {}", root + "/v3/api-docs");
        log.info("    Health             : {}", root + "/actuator/health");
        log.info("    Emergency screens  : {}", dashboardBaseUrl + "/safetysecurity/emergency");
        log.info("");
    }
}
