package gh.edu.clet.sfl.portal.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
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
 * <p>The shape mirrors the platform services' {@code /api/v1/system/info} exactly - both return
 * {@code ApiResponse.ok(...)}, so the dashboard has one reader for it and never needs a special case
 * for the portal. This module already depends on {@code sfl-service-common} (for the shared
 * dashboard auto-configuration), so wrapping the response the same way the platform services'
 * {@code SystemController} does costs nothing extra.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final String applicationName;

    public SystemController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, String>> info() {
        return ApiResponse.ok(Map.of(
                "platform", "ALL",
                "service", applicationName,
                "architecture", "sfl-phase-1-microservice",
                "status", "portal"));
    }
}
