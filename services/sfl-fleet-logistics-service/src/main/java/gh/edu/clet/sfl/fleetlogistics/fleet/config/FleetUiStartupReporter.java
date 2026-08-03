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
 * <p>This used to announce a bundled dashboard as well. The service no longer serves a user
 * interface, so what it announces now is the API surface a front end would build against: the
 * Swagger page and the OpenAPI document. Anyone plugging in a new UI starts from those two URLs.
 *
 * <p>The allowed browser origins are logged with them. A UI on another origin fails at the browser
 * rather than here, and this log is the first place anyone looks — naming the origins turns a
 * confusing silent CORS rejection into a one-line diagnosis.
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
    private final String allowedOrigins;

    FleetUiStartupReporter(
            @Value("${sfl.fleet.open-browser:false}") boolean openBrowser,
            @Value("${server.port:8093}") String port,
            @Value("${server.servlet.context-path:}") String contextPath,
            @Value("${sfl.cors.allowed-origins:}") String allowedOrigins) {
        this.openBrowser = openBrowser;
        this.port = port;
        this.contextPath = contextPath;
        this.allowedOrigins = allowedOrigins;
    }

    @EventListener(ApplicationReadyEvent.class)
    void report() {
        String root = "http://localhost:" + port + contextPath;
        String swagger = root + "/swagger-ui.html";

        log.info("");
        log.info("  SFL Fleet & Logistics service is ready — API only, no bundled UI");
        log.info("    API base           : {}/api/v1", root);
        log.info("    API docs (Swagger) : {}", swagger);
        log.info("    OpenAPI JSON       : {}/v3/api-docs", root);
        log.info("    Health             : {}/actuator/health", root);
        if (!allowedOrigins.isBlank()) {
            log.info("    Browser origins allowed : {}", allowedOrigins);
        }
        log.info("");

        if (openBrowser) {
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
