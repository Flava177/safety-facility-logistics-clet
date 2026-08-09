package gh.edu.clet.sfl.facilities.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                // The platform this origin serves. The dashboard reads it once at boot and shows
                // only this platform's screens, so a service never offers a screen it cannot back.
                "platform", "IFIMP",
                "service", applicationName,
                "architecture", "sfl-phase-1-microservice",
                "status", "foundation"));
    }
}

