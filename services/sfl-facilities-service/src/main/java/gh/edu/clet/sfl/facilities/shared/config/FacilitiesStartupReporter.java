package gh.edu.clet.sfl.facilities.shared.config;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints this service's front doors once it is up, and opens them.
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
 *
 * <h2>Opening a browser</h2>
 *
 * <p>Swagger and the dashboard screens open independently - {@code sfl.facilities.open-swagger} and
 * {@code sfl.facilities.open-dashboard} - so a run configuration that is one member of a larger
 * compound (see {@code SFL all services (compound)}) can open this service's API tab without also
 * opening its own copy of the dashboard, leaving that to whichever one config in the group is meant
 * to. Both default off, which the run configuration overrides and nothing else does - a test run or a
 * container start that opened tabs would be a defect. The twin of the fleet reporter's behaviour, and
 * it fails silently for the same reason: a convenience must never be able to take the service down.
 */
@Component
class FacilitiesStartupReporter {

    private static final Logger log = LoggerFactory.getLogger(FacilitiesStartupReporter.class);

    private final boolean openSwagger;
    private final boolean openDashboard;
    private final String port;
    private final String contextPath;
    private final String dashboardBaseUrl;

    FacilitiesStartupReporter(
            @Value("${sfl.facilities.open-swagger:false}") boolean openSwagger,
            @Value("${sfl.facilities.open-dashboard:false}") boolean openDashboard,
            @Value("${server.port:8091}") String port,
            @Value("${server.servlet.context-path:}") String contextPath,
            @Value("${sfl.dashboard.base-url:http://localhost:${server.port:8091}/home}") String dashboardBaseUrl) {
        this.openSwagger = openSwagger;
        this.openDashboard = openDashboard;
        this.port = port;
        this.contextPath = contextPath;
        this.dashboardBaseUrl = dashboardBaseUrl.replaceAll("/+$", "");
    }

    @EventListener(ApplicationReadyEvent.class)
    void report() {
        String root = "http://localhost:" + port + contextPath;
        String swagger = root + "/swagger-ui.html";
        String screens = dashboardBaseUrl + "/facilities";
        log.info("");
        log.info("  SFL Facilities service (IFIMP) is ready");
        log.info("    API docs (Swagger) : {}", swagger);
        log.info("    OpenAPI JSON       : {}", root + "/v3/api-docs");
        log.info("    Health             : {}", root + "/actuator/health");
        log.info("    Facilities screens : {}", screens);
        log.info("");

        // Screens first so they end up as the focused tab when both open.
        if (openDashboard) {
            open(screens);
        }
        if (openSwagger) {
            open(swagger);
        }
    }

    private void open(String url) {
        try {
            new ProcessBuilder(openCommand(url)).start();
        } catch (Exception exception) {
            // A convenience feature must never affect the service.
            log.debug("Could not open {} in a browser: {}", url, exception.getMessage());
        }
    }

    private static List<String> openCommand(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return List.of("rundll32", "url.dll,FileProtocolHandler", url);
        }
        if (os.contains("mac")) {
            return List.of("open", url);
        }
        return List.of("xdg-open", url);
    }
}
