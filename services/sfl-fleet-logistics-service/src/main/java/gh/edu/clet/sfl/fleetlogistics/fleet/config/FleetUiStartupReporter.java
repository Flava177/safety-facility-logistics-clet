package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints the service's front doors once it is up, and optionally opens them in a browser.
 *
 * <p>The point is that starting the service is the whole ceremony: the log says where the API docs
 * and the console are, and {@code sfl.fleet.open-browser=true} — which the local start script sets —
 * opens both tabs.
 *
 * <p>The browser is launched with the platform's own opener rather than {@code java.awt.Desktop},
 * because Spring Boot runs headless by default and {@code Desktop} is unavailable there. Opening is
 * best-effort and never fails startup: a server or CI machine simply gets the log lines.
 */
@Component
class FleetUiStartupReporter {

    private static final Logger log = LoggerFactory.getLogger(FleetUiStartupReporter.class);

    private final boolean openBrowser;
    private final String port;
    private final String contextPath;

    FleetUiStartupReporter(
            @Value("${sfl.fleet.open-browser:false}") boolean openBrowser,
            @Value("${server.port:8093}") String port,
            @Value("${server.servlet.context-path:}") String contextPath) {
        this.openBrowser = openBrowser;
        this.port = port;
        this.contextPath = contextPath;
    }

    @EventListener(ApplicationReadyEvent.class)
    void report() {
        String root = "http://localhost:" + port + contextPath;
        String swagger = root + "/swagger-ui.html";
        boolean uiBundled = FleetUiBundle.isPresent();
        String console = root + FleetUiBundle.PATH;

        log.info("");
        log.info("  SFL Fleet & Logistics service is ready");
        log.info("    API docs (Swagger) : {}", swagger);
        log.info("    OpenAPI JSON       : {}", root + "/v3/api-docs");
        if (uiBundled) {
            log.info("    Operations console : {}", console);
        } else {
            log.info("    Operations console : not bundled. Build it with");
            log.info("                         mvn -pl sfl-fleet-logistics-service -Pui spring-boot:run");
            log.info("                         or run it separately with 'npm run dev' on port 5005");
        }
        log.info("");

        if (!openBrowser) {
            return;
        }
        // Console first so it ends up as the focused tab.
        if (uiBundled) {
            open(console);
        }
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
