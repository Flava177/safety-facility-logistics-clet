package gh.edu.clet.sfl.portal.api;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The portal's answer to the one question the dashboard asks every origin at boot.
 *
 * <p>{@code platform} is {@code ALL}, and that is the whole difference between this origin and the
 * three platform services: the same bundle, told it is the portal, offers every platform's screens
 * and every seeded account, while on 8091 it offers only IFIMP. One fact, one place, no port numbers
 * anywhere in the front end.
 *
 * <p>The shape mirrors the platform services' {@code /api/v1/system/info} deliberately - the
 * dashboard has one reader for it and must not need a special case for the portal. It returns a bare
 * map rather than {@code ApiResponse} only because this module has no dependency on the API envelope
 * and adding one to serve a single route would be the wrong trade.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final String applicationName;

    public SystemController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("data", Map.of(
                "platform", "ALL",
                "service", applicationName,
                "architecture", "sfl-phase-1-microservice",
                "status", "portal"));
    }
}
