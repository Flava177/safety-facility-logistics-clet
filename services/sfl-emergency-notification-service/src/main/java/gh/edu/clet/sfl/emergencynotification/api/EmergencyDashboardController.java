package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.emergencynotification.application.service.EmergencyDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SRS-SFL-S174-05: emergency dashboard (active activations, break-glass, failed recipients, freshness). */
@RestController
@RequestMapping("/api/v1/emergency/dashboard")
@Tag(name = "Dashboards and Reports")
public class EmergencyDashboardController {

    private final EmergencyDashboardService service;
    private final EmergencyActorResolver actors;

    public EmergencyDashboardController(EmergencyDashboardService service, EmergencyActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> dashboard(@RequestParam String siteCode, HttpServletRequest h) {
        return ApiResponse.ok(service.dashboard(siteCode, actors.resolve(h)));
    }
}
