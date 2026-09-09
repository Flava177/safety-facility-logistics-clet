package gh.edu.clet.sfl.fleetlogistics.dispatch.api;

import gh.edu.clet.sfl.common.web.ContentDispositionFilenames;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchDashboardService;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S171 authorised CSV exports of the courier item register and the exception queue. */
@RestController
@RequestMapping("/api/v1/dispatch/reports")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dispatch Dashboards and Reports")
public class DispatchReportController {

    private final DispatchDashboardService service;
    private final FleetActorResolver actors;

    public DispatchReportController(DispatchDashboardService service, FleetActorResolver actors) {
        this.service = service;
        this.actors = actors;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Exports the courier item register for a site as CSV")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Actor lacks the required dispatch report export permission for the site")
    @GetMapping(value = "/items.csv", produces = "text/csv")
    public ResponseEntity<String> items(@RequestParam String siteCode, HttpServletRequest h) {
        return csv("dispatch-items-" + ContentDispositionFilenames.sanitizeSegment(siteCode) + ".csv",
                service.itemsReportCsv(siteCode, actors.resolve(h)));
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Exports the exception case queue for a site as CSV")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Actor lacks the required dispatch report export permission for the site")
    @GetMapping(value = "/exceptions.csv", produces = "text/csv")
    public ResponseEntity<String> exceptions(@RequestParam String siteCode, HttpServletRequest h) {
        return csv("dispatch-exceptions-" + ContentDispositionFilenames.sanitizeSegment(siteCode) + ".csv",
                service.exceptionsReportCsv(siteCode, actors.resolve(h)));
    }

    private static ResponseEntity<String> csv(String fileName, String body) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName).body(body);
    }
}
