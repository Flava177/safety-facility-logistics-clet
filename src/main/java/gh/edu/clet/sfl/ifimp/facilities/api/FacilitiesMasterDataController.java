package gh.edu.clet.sfl.ifimp.facilities.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import gh.edu.clet.sfl.ifimp.facilities.application.CreateBuildingCommand;
import gh.edu.clet.sfl.ifimp.facilities.application.CreateFloorCommand;
import gh.edu.clet.sfl.ifimp.facilities.application.CreateRoomCommand;
import gh.edu.clet.sfl.ifimp.facilities.application.CreateSiteCommand;
import gh.edu.clet.sfl.ifimp.facilities.application.CreateZoneCommand;
import gh.edu.clet.sfl.ifimp.facilities.application.FacilitiesMasterDataService;
import gh.edu.clet.sfl.ifimp.facilities.application.RegisterDeviceReferenceCommand;
import gh.edu.clet.sfl.ifimp.facilities.application.UpdateRoomReadinessCommand;
import gh.edu.clet.sfl.ifimp.facilities.domain.Building;
import gh.edu.clet.sfl.ifimp.facilities.domain.DeviceReference;
import gh.edu.clet.sfl.ifimp.facilities.domain.DeviceReferenceType;
import gh.edu.clet.sfl.ifimp.facilities.domain.FacilityFloor;
import gh.edu.clet.sfl.ifimp.facilities.domain.FacilityRoom;
import gh.edu.clet.sfl.ifimp.facilities.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.ifimp.facilities.domain.Site;
import gh.edu.clet.sfl.ifimp.facilities.domain.Zone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ifimp")
public class FacilitiesMasterDataController {

    private final FacilitiesMasterDataService service;

    public FacilitiesMasterDataController(FacilitiesMasterDataService service) {
        this.service = service;
    }

    @PostMapping("/sites")
    public ResponseEntity<Site> createSite(@Valid @RequestBody CreateSiteRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String actor,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        Site result = service.createSite(new CreateSiteCommand(request.siteCode(), request.name(),
                request.description(), actor, correlationId));
        return ResponseEntity.created(URI.create("/api/v1/ifimp/sites/" + result.id())).body(result);
    }

    @GetMapping("/sites")
    public List<Site> sites() {
        return service.sites();
    }

    @PostMapping("/buildings")
    public ResponseEntity<Building> createBuilding(@Valid @RequestBody CreateBuildingRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String actor,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        Building result = service.createBuilding(new CreateBuildingCommand(request.siteId(), request.siteCode(),
                request.buildingCode(), request.name(), request.description(), actor, correlationId));
        return ResponseEntity.created(URI.create("/api/v1/ifimp/buildings/" + result.id())).body(result);
    }

    @GetMapping("/buildings")
    public List<Building> buildings(@RequestParam(required = false) String siteCode) {
        return service.buildings(siteCode);
    }

    @PostMapping("/floors")
    public ResponseEntity<FacilityFloor> createFloor(@Valid @RequestBody CreateFloorRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String actor,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        FacilityFloor result = service.createFloor(new CreateFloorCommand(request.buildingId(), request.siteCode(),
                request.floorCode(), request.name(), request.levelNumber(), actor, correlationId));
        return ResponseEntity.created(URI.create("/api/v1/ifimp/floors/" + result.id())).body(result);
    }

    @GetMapping("/buildings/{buildingId}/floors")
    public List<FacilityFloor> floors(@PathVariable UUID buildingId) {
        return service.floors(buildingId);
    }

    @PostMapping("/rooms")
    public ResponseEntity<FacilityRoom> createRoom(@Valid @RequestBody CreateRoomRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String actor,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        FacilityRoom result = service.createRoom(new CreateRoomCommand(request.floorId(), request.siteCode(),
                request.roomCode(), request.name(), request.roomType(), request.capacity(), actor, correlationId));
        return ResponseEntity.created(URI.create("/api/v1/ifimp/rooms/" + result.id())).body(result);
    }

    @GetMapping("/rooms")
    public List<FacilityRoom> rooms(@RequestParam(required = false) String siteCode) {
        return service.rooms(siteCode);
    }

    @PatchMapping("/rooms/{roomId}/readiness")
    public FacilityRoom updateRoomReadiness(@PathVariable UUID roomId,
            @Valid @RequestBody UpdateRoomReadinessRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String actor,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        return service.updateRoomReadiness(new UpdateRoomReadinessCommand(roomId, request.status(), request.notes(),
                actor, correlationId));
    }

    @PostMapping("/zones")
    public ResponseEntity<Zone> createZone(@Valid @RequestBody CreateZoneRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String actor,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        Zone result = service.createZone(new CreateZoneCommand(request.siteCode(), request.zoneCode(), request.name(),
                request.purpose(), actor, correlationId));
        return ResponseEntity.created(URI.create("/api/v1/ifimp/zones/" + result.id())).body(result);
    }

    @GetMapping("/zones")
    public List<Zone> zones(@RequestParam(required = false) String siteCode) {
        return service.zones(siteCode);
    }

    @PostMapping("/device-references")
    public ResponseEntity<DeviceReference> registerDeviceReference(
            @Valid @RequestBody RegisterDeviceReferenceRequest request,
            @RequestHeader(name = "X-SFL-User", defaultValue = "development-user") String actor,
            @RequestHeader(name = "X-Correlation-ID", required = false) String correlationId) {
        DeviceReference result = service.registerDeviceReference(new RegisterDeviceReferenceCommand(
                request.siteCode(), request.deviceCode(), request.name(), request.type(), request.roomId(),
                request.locationCode(), request.vendor(), actor, correlationId));
        return ResponseEntity.created(URI.create("/api/v1/ifimp/device-references/" + result.id())).body(result);
    }

    @GetMapping("/device-references")
    public List<DeviceReference> deviceReferences(@RequestParam(required = false) String siteCode) {
        return service.deviceReferences(siteCode);
    }

    public record CreateSiteRequest(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description) {
    }

    public record CreateBuildingRequest(
            @NotNull UUID siteId,
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 60) String buildingCode,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description) {
    }

    public record CreateFloorRequest(
            @NotNull UUID buildingId,
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 60) String floorCode,
            @NotBlank @Size(max = 160) String name,
            Integer levelNumber) {
    }

    public record CreateRoomRequest(
            @NotNull UUID floorId,
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String roomCode,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String roomType,
            @Min(0) Integer capacity) {
    }

    public record UpdateRoomReadinessRequest(
            @NotNull LocationReadinessStatus status,
            @Size(max = 1000) String notes) {
    }

    public record CreateZoneRequest(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 60) String zoneCode,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 300) String purpose) {
    }

    public record RegisterDeviceReferenceRequest(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String deviceCode,
            @NotBlank @Size(max = 160) String name,
            @NotNull DeviceReferenceType type,
            UUID roomId,
            @Size(max = 120) String locationCode,
            @Size(max = 160) String vendor) {
    }
}
