package gh.edu.clet.sfl.portal.config;

import gh.edu.clet.sfl.common.ui.SflDashboardBundle;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints the portal's address once it is up, and opens it.
 *
 * <p>The address printed is {@code sfl.portal.public-url}, not the port this process bound to. They
 * are the same host and port by default, but the public URL is the one to hand to a person: it is
 * what the three platform services point their users at, and what a hosts-file entry makes work.
 * Printing the bind address instead would put a URL in the log that nobody should be using.
 *
 * <p>Opening a browser is off unless a run configuration or start script asks for it - a test run or
 * a container start that opened tabs would be a defect - and it fails silently, because a
 * convenience must never be able to take the portal down.
 */
@Component
class PortalStartupReporter {

    private static final Logger log = LoggerFactory.getLogger(PortalStartupReporter.class);

    private final boolean openBrowser;
    private final String publicUrl;
    private final String port;

    PortalStartupReporter(
            @Value("${sfl.portal.open-browser:false}") boolean openBrowser,
            @Value("${sfl.portal.public-url:http://localhost:8090/home}") String publicUrl,
            @Value("${server.port:8090}") String port) {
        this.openBrowser = openBrowser;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    void report() {
        boolean bundled = SflDashboardBundle.isPresent();

        log.info("");
        log.info("  SFL Operations portal is ready");
        if (bundled) {
            log.info("    Portal             : {}", publicUrl);
            log.info("    Direct (no hosts)  : {}", "http://localhost:" + port + SflDashboardBundle.PATH);
        } else {
            log.info("    Portal             : not bundled. Build it with");
            log.info("                         cd services && ..\\mvnw.cmd -Pui install");
            log.info("                         or run it separately with 'npm run dev' on port 5005");
        }
        log.info("");

        if (!openBrowser || !bundled) {
            return;
        }
        open(publicUrl);
    }

    private void open(String url) {
        try {
            new ProcessBuilder(openCommand(url)).start();
        } catch (Exception exception) {
            // A convenience feature must never affect the portal.
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
