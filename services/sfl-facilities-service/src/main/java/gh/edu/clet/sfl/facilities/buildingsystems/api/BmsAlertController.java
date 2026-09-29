package gh.edu.clet.sfl.facilities.buildingsystems.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingAlertService;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertStatus;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Automated alerts - SRS-SFL-S156-02, -03. */
@RestController
@RequestMapping("/api/v1/facilities/building-systems/alerts")
@Tag(name = "S156 Alerts", description = "Automated threshold, fault-code and critical-fault alerts")
public class BmsAlertController {

    private final BuildingAlertService alerts;

    public BmsAlertController(BuildingAlertService alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    @Operation(summary = "List alerts for a site")
    public ApiResponse<List<BuildingSystemsResponses.AlertResponse>> list(@RequestParam String siteCode,
            @RequestParam(required = false) AlertStatus status, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(alerts.alerts(siteCode, status, actor, channel).stream()
                .map(BuildingSystemsResponses.AlertResponse::from).toList());
    }

    @GetMapping("/{alertId}")
    @Operation(summary = "Get an alert")
    public ApiResponse<BuildingSystemsResponses.AlertResponse> get(@PathVariable UUID alertId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(BuildingSystemsResponses.AlertResponse.from(alerts.alert(alertId, actor, channel)));
    }
}
