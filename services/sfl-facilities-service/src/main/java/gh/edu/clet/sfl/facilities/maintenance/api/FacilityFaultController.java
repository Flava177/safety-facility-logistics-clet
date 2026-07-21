package gh.edu.clet.sfl.facilities.maintenance.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import gh.edu.clet.sfl.facilities.maintenance.application.FacilityFaultService;
import gh.edu.clet.sfl.facilities.maintenance.application.ReportFacilityFaultCommand;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/facilities/faults")
public class FacilityFaultController {

    private final FacilityFaultService service;

    public FacilityFaultController(FacilityFaultService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FacilityFault> report(
            @Valid @RequestBody ReportFacilityFaultRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String actor,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        FacilityFault result = service.report(new ReportFacilityFaultCommand(
                request.siteCode(), request.locationCode(), request.title(), request.description(),
                request.category(), request.priority(), actor, correlationId));
        return ResponseEntity.created(URI.create("/api/v1/facilities/faults/" + result.id())).body(result);
    }

    @GetMapping
    public List<FacilityFault> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public FacilityFault findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    public record ReportFacilityFaultRequest(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String locationCode,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String description,
            @Size(max = 120) String category,
            @NotNull FaultPriority priority) {
    }
}

