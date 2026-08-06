package gh.edu.clet.sfl.facilities.shared.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints this service's front doors once it is up.
 *
 * <h2>Why all three services do this now</h2>
 *
 * <p>Only the fleet service printed a ready banner, so starting all three produced one block of URLs
 * and two walls of Spring log lines. There is no way to tell from that whether the other two came up,
 * failed, or are still starting - and facilities takes around a minute, which is long enough to
 * conclude the wrong thing and go looking for a fault. Three banners, three answers.
 *
 * <p>The dashboard line points at the fleet service on purpose. Every screen for all three platforms
 * lives in one bundle (ADR 0006) and only {@code sfl-fleet-logistics-service} packages it, so naming
 * this service's own port there would send people to the redirect rather than the thing itself.
 */
@Component
class FacilitiesStartupReporter {

    private static final Logger log = LoggerFactory.getLogger(FacilitiesStartupReporter.class);

    private final String port;
    private final String contextPath;
    private final String dashboardBaseUrl;

    FacilitiesStartupReporter(
            @Value("${server.port:8091}") String port,
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
        log.info("  SFL Facilities service (IFIMP) is ready");
        log.info("    API docs (Swagger) : {}", root + "/swagger-ui.html");
        log.info("    OpenAPI JSON       : {}", root + "/v3/api-docs");
        log.info("    Health             : {}", root + "/actuator/health");
        log.info("    Facilities screens : {}", dashboardBaseUrl + "/facilities");
        log.info("");
    }
}
