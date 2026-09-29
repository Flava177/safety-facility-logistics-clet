package gh.edu.clet.sfl.facilities.energy.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.energy.application.EnergyCommands;
import gh.edu.clet.sfl.facilities.energy.application.EnergyHealthService;
import gh.edu.clet.sfl.facilities.energy.application.EnergyMeterService;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.shared.api.IdempotencyKey;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The S157 meter register and integration health - SRS-SFL-S157-01, -04; CORR-07. */
@RestController
@RequestMapping("/api/v1/facilities/energy")
@Tag(name = "S157 Energy meters", description = "Meter register, one AVAMP identity per device, integration health")
public class EnergyMeterController {

    private final EnergyMeterService meters;
    private final EnergyHealthService health;

    public EnergyMeterController(EnergyMeterService meters, EnergyHealthService health) {
        this.meters = meters;
        this.health = health;
    }

    @PostMapping("/meters")
    @Operation(summary = "Register a meter",
            description = "Resolved to an S152 site and building. A device S156 already ingests must be "
                    + "BMS_STREAM; registering it as AMI is refused with ENERGY_DEVICE_DOUBLE_REGISTERED (S157-04).")
    public ResponseEntity<ApiResponse<EnergyResponses.MeterResponse>> register(
            @Valid @RequestBody EnergyRequests.RegisterMeter request, ActorContext actor, SourceChannel channel,
            @IdempotencyKey String idempotencyKey) {
        EnergyResponses.MeterResponse meter = EnergyResponses.MeterResponse.from(meters.register(
                new EnergyCommands.RegisterMeter(request.siteCode(), request.buildingCode(), request.roomId(),
                        request.meterCode(), request.name(), request.utility(), request.source(),
                        request.avampAssetId(), request.vendorMeterRef(), request.expectedIntervalMinutes(), actor,
                        channel, idempotencyKey, request)));
        return ResponseEntity.created(URI.create("/api/v1/facilities/energy/meters/" + meter.id()))
                .body(ApiResponse.ok(meter));
    }

    @PatchMapping("/meters/{meterId}")
    @Operation(summary = "Rename, re-time, or move a meter onto the S156 stream",
            description = "The only source change accepted is onto BMS_STREAM - the repair for an AMI meter "
                    + "whose device S156 has since enrolled.")
    public ApiResponse<EnergyResponses.MeterResponse> update(@PathVariable UUID meterId,
            @Valid @RequestBody EnergyRequests.UpdateMeter request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.MeterResponse.from(meters.update(new EnergyCommands.UpdateMeter(meterId,
                request.name(), request.expectedIntervalMinutes(), request.source(), request.expectedVersion(), actor,
                channel))));
    }

    @PatchMapping("/meters/{meterId}/retirement")
    @Operation(summary = "Retire a meter, with a reason", description = "Its AVAMP identity and history stay.")
    public ApiResponse<EnergyResponses.MeterResponse> retire(@PathVariable UUID meterId,
            @Valid @RequestBody EnergyRequests.RetireMeter request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.MeterResponse.from(meters.retire(new EnergyCommands.RetireMeter(meterId,
                request.reason(), request.expectedVersion(), actor, channel))));
    }

    @GetMapping("/meters")
    @Operation(summary = "List meters", description = "Filtered to the caller's sites.")
    public ApiResponse<List<EnergyResponses.MeterResponse>> list(@RequestParam(required = false) String siteCode,
            @RequestParam(required = false) Utility utility,
            @RequestParam(defaultValue = "false") boolean activeOnly, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(meters.list(siteCode, utility, activeOnly, actor, channel).stream()
                .map(EnergyResponses.MeterResponse::from).toList());
    }

    @GetMapping("/meters/{meterId}")
    @Operation(summary = "One meter")
    public ApiResponse<EnergyResponses.MeterResponse> get(@PathVariable UUID meterId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(EnergyResponses.MeterResponse.from(meters.get(meterId, actor, channel)));
    }

    @GetMapping("/health")
    @Operation(summary = "Metering integration health",
            description = "Includes the procurement-gate status of energy-metering (CORR-07): "
                    + "SIMULATED_ADAPTER_ONLY until SRS 5.2 evidence is on file. Lists S157-04 conflicts.")
    public ApiResponse<EnergyHealthService.EnergyHealth> health(@RequestParam(required = false) String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(health.health(siteCode, actor, channel));
    }
}
