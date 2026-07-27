package gh.edu.clet.sfl.emergencynotification.api;

import gh.edu.clet.sfl.emergencynotification.application.service.EmergencyDashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SRS-SFL-S174-05: authorised CSV export of the emergency activation register. */
@RestController
@RequestMapping("/api/v1/emergency/reports")
@Tag(name = "Dashboards and Reports")
public class EmergencyReportController {

    private final EmergencyDashboardService service;
    private final EmergencyActorResolver actors;

    public EmergencyReportController(EmergencyDashboardService service, EmergencyActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping(value = "/activations.csv", produces = "text/csv")
    public ResponseEntity<String> activations(@RequestParam String siteCode, HttpServletRequest h) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=emergency-activations-" + siteCode + ".csv")
                .body(service.activationsReportCsv(siteCode, actors.resolve(h)));
    }
}
