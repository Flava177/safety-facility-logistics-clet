package gh.edu.clet.sfl.facilities.buildingsystems.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BmsDeviceService;
import gh.edu.clet.sfl.facilities.buildingsystems.application.BuildingSystemsCommands;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceStatus;
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

/** The S156 device inventory and lifecycle - SRS-SFL-S156-04. */
@RestController
@RequestMapping("/api/v1/facilities/building-systems/devices")
@Tag(name = "S156 Devices", description = "IoT device inventory and lifecycle via AVAMP")
public class BmsDeviceController {

    private final BmsDeviceService devices;

    public BmsDeviceController(BmsDeviceService devices) {
        this.devices = devices;
    }

    @PostMapping
    @Operation(summary = "Register a device", description = "SRS-SFL-S156-04. Requires FACILITIES_BMS_DEVICE_MANAGE "
            + "and an active AVAMP asset id already projected from sfl.avamp.asset-registered.v1.")
    public ResponseEntity<ApiResponse<BuildingSystemsResponses.DeviceResponse>> register(
            @Valid @RequestBody BuildingSystemsRequests.RegisterDevice request, ActorContext actor,
            SourceChannel channel) {
        BmsDevice device = devices.register(new BuildingSystemsCommands.RegisterDevice(request.siteCode(),
                request.deviceCode(), request.avampAssetId(), request.name(), request.systemType(), request.kind(),
                request.buildingCode(), request.roomId(), request.expectedIntervalSeconds(), request.installedOn(),
                request.firmwareVersion(), request.firmwareReviewDueOn(), request.warrantyExpiresOn(),
                request.calibrationDueOn(), actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/building-systems/devices/" + device.id()))
                .body(ApiResponse.ok(BuildingSystemsResponses.DeviceResponse.from(device)));
    }

    @PatchMapping("/{deviceId}")
    @Operation(summary = "Revise a device's lifecycle facts or location")
    public ApiResponse<BuildingSystemsResponses.DeviceResponse> revise(@PathVariable UUID deviceId,
            @Valid @RequestBody BuildingSystemsRequests.ReviseDevice request, ActorContext actor,
            SourceChannel channel) {
        BmsDevice device = devices.revise(new BuildingSystemsCommands.ReviseDevice(deviceId, request.name(),
                request.buildingCode(), request.roomId(), request.expectedIntervalSeconds(), request.installedOn(),
                request.firmwareVersion(), request.firmwareReviewDueOn(), request.warrantyExpiresOn(),
                request.calibrationDueOn(), request.expectedVersion(), actor, channel));
        return ApiResponse.ok(BuildingSystemsResponses.DeviceResponse.from(device));
    }

    @PatchMapping("/{deviceId}/retirement")
    @Operation(summary = "Retire a device", description = "Retires, never deletes - the record and its history stay.")
    public ApiResponse<BuildingSystemsResponses.DeviceResponse> retire(@PathVariable UUID deviceId,
            @Valid @RequestBody BuildingSystemsRequests.RetireDevice request, ActorContext actor,
            SourceChannel channel) {
        BmsDevice device = devices.retire(new BuildingSystemsCommands.RetireDevice(deviceId, request.reason(),
                request.expectedVersion(), actor, channel));
        return ApiResponse.ok(BuildingSystemsResponses.DeviceResponse.from(device));
    }

    @GetMapping
    @Operation(summary = "List devices")
    public ApiResponse<List<BuildingSystemsResponses.DeviceResponse>> list(
            @RequestParam(required = false) String siteCode, @RequestParam(required = false) DeviceStatus status,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(devices.devices(siteCode, status, actor, channel).stream()
                .map(BuildingSystemsResponses.DeviceResponse::from).toList());
    }

    @GetMapping("/{deviceId}")
    @Operation(summary = "Get a device")
    public ApiResponse<BuildingSystemsResponses.DeviceResponse> get(@PathVariable UUID deviceId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(BuildingSystemsResponses.DeviceResponse.from(devices.device(deviceId, actor, channel)));
    }
}
