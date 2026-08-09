package gh.edu.clet.sfl.safetysecurity.emergency.config;

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
 * <p>The twin of the facilities and fleet reporters - see {@code FacilitiesStartupReporter} for why
 * all three do this, why the dashboard line names 8093 rather than this service's own port, and why
 * opening a browser is off unless a run configuration asks for it.
 */
@Component
class SafetySecurityStartupReporter {

    private static final Logger log = LoggerFactory.getLogger(SafetySecurityStartupReporter.class);

    private final boolean openBrowser;
    private final String port;
    private final String contextPath;
    private final String dashboardBaseUrl;

    SafetySecurityStartupReporter(
            @Value("${sfl.safety-security.open-browser:false}") boolean openBrowser,
            @Value("${server.port:8092}") String port,
            @Value("${server.servlet.context-path:}") String contextPath,
            @Value("${sfl.dashboard.base-url:http://localhost:${server.port:8092}/home}") String dashboardBaseUrl) {
        this.openBrowser = openBrowser;
        this.port = port;
        this.contextPath = contextPath;
        this.dashboardBaseUrl = dashboardBaseUrl.replaceAll("/+$", "");
    }

    @EventListener(ApplicationReadyEvent.class)
    void report() {
        String root = "http://localhost:" + port + contextPath;
        String swagger = root + "/swagger-ui.html";
        String screens = dashboardBaseUrl + "/safetysecurity/emergency";
        log.info("");
        log.info("  SFL Safety, Security & Emergency service (SSEMP) is ready");
        log.info("    API docs (Swagger) : {}", swagger);
        log.info("    OpenAPI JSON       : {}", root + "/v3/api-docs");
        log.info("    Health             : {}", root + "/actuator/health");
        log.info("    Emergency screens  : {}", screens);
        log.info("");

        if (!openBrowser) {
            return;
        }
        // Screens first so they end up as the focused tab. This service serves them itself, from its
        // own jar on its own port, so nothing else has to be running for the tab to work.
        open(screens);
        open(swagger);
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
