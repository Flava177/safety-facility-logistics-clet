package gh.edu.clet.sfl.facilities.masterdata.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.BuildingResponse;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.DeviceReferenceResponse;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.FloorResponse;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.RoomResponse;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.SiteResponse;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.ZoneMemberResponse;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.ZoneResponse;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesMasterDataService;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.readiness.application.ReadinessApplicationService;
import gh.edu.clet.sfl.facilities.shared.api.IdempotencyKey;
import gh.edu.clet.sfl.facilities.shared.api.PageResponse;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The S152 estate endpoints (SRS-SFL-S152-01, -02).
 *
 * <p>The controller does no business work: it resolves the actor and source channel, binds and
 * validates the request, delegates to an application service and shapes the response. Authorisation,
 * invariants, audit and event publication all live behind the application boundary - the ArchUnit
 * boundary test enforces that this class cannot reach past it.
 */
@RestController
@RequestMapping("/api/v1/facilities")
@Tag(name = "S152 Estate", description = "Sites, buildings, floors, spaces, zones and device references")
public class FacilitiesMasterDataController {

    private final FacilitiesMasterDataService service;
    private final ReadinessApplicationService readiness;

    public FacilitiesMasterDataController(FacilitiesMasterDataService service,
            ReadinessApplicationService readiness) {
        this.service = service;
        this.readiness = readiness;
    }

    // ---- sites --------------------------------------------------------------------------------

    @PostMapping("/sites")
    @Operation(summary = "Register a site", description = "SRS-SFL-S152-01. Accepts an Idempotency-Key.")
    public ResponseEntity<ApiResponse<SiteResponse>> createSite(@Valid @RequestBody FacilitiesRequests.CreateSite request,
            ActorContext actor, SourceChannel channel, @IdempotencyKey String idempotencyKey) {
        SiteResponse result = SiteResponse.from(service.createSite(new FacilitiesCommands.CreateSite(
                request.siteCode(), request.name(), request.description(), actor, channel,
                idempotencyKey)));
        return ResponseEntity.created(URI.create("/api/v1/facilities/sites/" + result.id())).body(ApiResponse.ok(result));
    }

    @GetMapping("/sites")
    @Operation(summary = "List the sites the actor is scoped to")
    public ApiResponse<List<SiteResponse>> sites(ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.sites(actor, channel).stream().map(SiteResponse::from).toList());
    }

    @GetMapping("/sites/{siteId}")
    @Operation(summary = "Read one site")
    public ApiResponse<SiteResponse> site(@PathVariable UUID siteId, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(SiteResponse.from(service.site(siteId, actor, channel)));
    }

    @PatchMapping("/sites/{siteId}")
    @Operation(summary = "Update a site's operational attributes")
    public ApiResponse<SiteResponse> updateSite(@PathVariable UUID siteId,
            @Valid @RequestBody FacilitiesRequests.UpdateSite request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(SiteResponse.from(service.updateSite(new FacilitiesCommands.UpdateSite(siteId, request.name(),
                request.description(), request.expectedVersion(), actor, channel))));
    }

    @PatchMapping("/sites/{siteId}/lifecycle")
    @Operation(summary = "Move a site through its lifecycle",
            description = "ACTIVE, INACTIVE, SUSPENDED or ARCHIVED. ARCHIVED is terminal.")
    public ApiResponse<SiteResponse> changeSiteLifecycle(@PathVariable UUID siteId,
            @Valid @RequestBody FacilitiesRequests.ChangeLifecycle request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(SiteResponse.from(service.changeSiteLifecycle(new FacilitiesCommands.ChangeSiteLifecycle(siteId,
                request.status(), request.expectedVersion(), actor, channel))));
    }

    @PatchMapping("/sites/{siteId}/operating-mode")
    @Operation(summary = "Declare or stand down examination mode",
            description = "NFR 23.3. Requires FACILITIES_OPERATING_MODE_CHANGE and is audited.")
    public ApiResponse<SiteResponse> changeOperatingMode(@PathVariable UUID siteId,
            @Valid @RequestBody FacilitiesRequests.ChangeOperatingMode request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(SiteResponse.from(service.changeOperatingMode(new FacilitiesCommands.ChangeOperatingMode(siteId,
                request.operatingMode(), request.reason(), actor, channel))));
    }

    // ---- buildings ----------------------------------------------------------------------------

    @PostMapping("/buildings")
    @Operation(summary = "Register a building")
    public ResponseEntity<ApiResponse<BuildingResponse>> createBuilding(
            @Valid @RequestBody FacilitiesRequests.CreateBuilding request, ActorContext actor,
            SourceChannel channel, @IdempotencyKey String idempotencyKey) {
        BuildingResponse result = BuildingResponse.from(service.createBuilding(
                new FacilitiesCommands.CreateBuilding(request.siteId(), request.buildingCode(), request.name(),
                        request.description(), actor, channel, idempotencyKey)));
        return ResponseEntity.created(URI.create("/api/v1/facilities/buildings/" + result.id())).body(ApiResponse.ok(result));
    }

    @GetMapping("/buildings")
    @Operation(summary = "List buildings, optionally for one site")
    public ApiResponse<List<BuildingResponse>> buildings(@RequestParam(required = false) String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.buildings(siteCode, actor, channel).stream()
                .map(BuildingResponse::from).toList());
    }

    @GetMapping("/buildings/{buildingId}")
    @Operation(summary = "Read one building")
    public ApiResponse<BuildingResponse> building(@PathVariable UUID buildingId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(BuildingResponse.from(service.building(buildingId, actor, channel)));
    }

    // ---- floors -------------------------------------------------------------------------------

    @PatchMapping("/buildings/{buildingId}")
    @Operation(summary = "Update a building's name or description",
            description = "Requires FACILITIES_SPACE_MANAGE. Recorded as a known gap in "
                    + "S152_UI_Gap_Report §3 until this existed.")
    public ApiResponse<BuildingResponse> updateBuilding(@PathVariable UUID buildingId,
            @Valid @RequestBody FacilitiesRequests.UpdateBuilding request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(BuildingResponse.from(service.updateBuilding(
                new FacilitiesCommands.UpdateBuilding(buildingId, request.name(), request.description(),
                        request.expectedVersion(), actor, channel))));
    }

    @PatchMapping("/buildings/{buildingId}/lifecycle")
    @Operation(summary = "Move a building through its lifecycle", description = "ARCHIVED is terminal.")
    public ApiResponse<BuildingResponse> changeBuildingLifecycle(@PathVariable UUID buildingId,
            @Valid @RequestBody FacilitiesRequests.ChangeLifecycle request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(BuildingResponse.from(service.changeBuildingLifecycle(
                new FacilitiesCommands.ChangeBuildingLifecycle(buildingId, request.status(),
                        request.expectedVersion(), actor, channel))));
    }

    @PostMapping("/floors")
    @Operation(summary = "Register a floor")
    public ResponseEntity<ApiResponse<FloorResponse>> createFloor(@Valid @RequestBody FacilitiesRequests.CreateFloor request,
            ActorContext actor, SourceChannel channel, @IdempotencyKey String idempotencyKey) {
        FloorResponse result = FloorResponse.from(service.createFloor(new FacilitiesCommands.CreateFloor(
                request.buildingId(), request.floorCode(), request.name(), request.levelNumber(), actor,
                channel, idempotencyKey)));
        return ResponseEntity.created(URI.create("/api/v1/facilities/floors/" + result.id())).body(ApiResponse.ok(result));
    }

    @GetMapping("/buildings/{buildingId}/floors")
    @Operation(summary = "List a building's floors, lowest level first")
    public ApiResponse<List<FloorResponse>> floors(@PathVariable UUID buildingId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(service.floors(buildingId, actor, channel).stream().map(FloorResponse::from).toList());
    }

    @GetMapping("/floors/{floorId}")
    @Operation(summary = "Read one floor")
    public ApiResponse<FloorResponse> floor(@PathVariable UUID floorId, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(FloorResponse.from(service.floor(floorId, actor, channel)));
    }

    // ---- spaces -------------------------------------------------------------------------------

    @PatchMapping("/floors/{floorId}")
    @Operation(summary = "Update a floor's name or level",
            description = "Requires FACILITIES_SPACE_MANAGE. A null level is a mezzanine, not a missing value.")
    public ApiResponse<FloorResponse> updateFloor(@PathVariable UUID floorId,
            @Valid @RequestBody FacilitiesRequests.UpdateFloor request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(FloorResponse.from(service.updateFloor(
                new FacilitiesCommands.UpdateFloor(floorId, request.name(), request.levelNumber(),
                        request.expectedVersion(), actor, channel))));
    }

    @PatchMapping("/floors/{floorId}/lifecycle")
    @Operation(summary = "Move a floor through its lifecycle", description = "ARCHIVED is terminal.")
    public ApiResponse<FloorResponse> changeFloorLifecycle(@PathVariable UUID floorId,
            @Valid @RequestBody FacilitiesRequests.ChangeLifecycle request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(FloorResponse.from(service.changeFloorLifecycle(
                new FacilitiesCommands.ChangeFloorLifecycle(floorId, request.status(),
                        request.expectedVersion(), actor, channel))));
    }

    @PostMapping("/rooms")
    @Operation(summary = "Register a space",
            description = "Room, hall, moot courtroom or plant room. Bookable and examination-capable "
                    + "default from the space type unless stated.")
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(@Valid @RequestBody FacilitiesRequests.CreateRoom request,
            ActorContext actor, SourceChannel channel, @IdempotencyKey String idempotencyKey) {
        RoomResponse result = RoomResponse.from(service.createRoom(new FacilitiesCommands.CreateRoom(
                request.floorId(), request.roomCode(), request.name(), request.spaceType(), request.capacity(),
                request.areaSqm(), request.costCentre(), request.bookable(), request.examinationCapable(),
                actor, channel, idempotencyKey)));
        return ResponseEntity.created(URI.create("/api/v1/facilities/rooms/" + result.id())).body(ApiResponse.ok(result));
    }

    /**
     * Lists spaces for a site.
     *
     * <p>A plain list, preserving the shape the existing facilities dashboard page reads. Filtering and
     * paging live on {@code /rooms/search} rather than being added here, so an existing caller cannot
     * be broken by a default page size it never asked for.
     */
    @GetMapping("/rooms")
    @Operation(summary = "List spaces for a site")
    public ApiResponse<List<RoomResponse>> rooms(@RequestParam(required = false) String siteCode, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(service.rooms(siteCode, actor, channel).stream().map(RoomResponse::from).toList());
    }

    @GetMapping("/rooms/search")
    @Operation(summary = "Search spaces by site, building, floor, type, readiness and availability")
    public ApiResponse<PageResponse<RoomResponse>> searchRooms(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID floorId,
            @RequestParam(required = false) SpaceType spaceType,
            @RequestParam(required = false) LocationReadinessStatus readinessStatus,
            @RequestParam(required = false) Boolean bookable,
            @RequestParam(required = false) Boolean examinationCapable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            ActorContext actor, SourceChannel channel) {
        FacilitiesRepository.RoomQuery query = new FacilitiesRepository.RoomQuery(siteCode, buildingId, floorId,
                spaceType, readinessStatus, bookable, examinationCapable, page, size);
        return ApiResponse.ok(PageResponse.from(service.searchRooms(query, actor, channel), RoomResponse::from));
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Read one space")
    public ApiResponse<RoomResponse> room(@PathVariable UUID roomId, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(RoomResponse.from(service.room(roomId, actor, channel)));
    }

    @PatchMapping("/rooms/{roomId}")
    @Operation(summary = "Update a space's attributes",
            description = "Refused while the space's readiness lock is engaged.")
    public ApiResponse<RoomResponse> updateRoom(@PathVariable UUID roomId,
            @Valid @RequestBody FacilitiesRequests.UpdateRoom request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(RoomResponse.from(service.updateRoom(new FacilitiesCommands.UpdateRoom(roomId, request.name(),
                request.spaceType(), request.capacity(), request.areaSqm(), request.costCentre(),
                request.bookable(), request.examinationCapable(), request.expectedVersion(), actor,
                channel))));
    }

    /**
     * Sets a space's readiness by hand.
     *
     * <p>Delegates to the readiness module rather than to the estate service, because the
     * critical-blocker rule has to apply to a manual status exactly as it does to a derived one.
     */
    @PatchMapping("/rooms/{roomId}/readiness")
    @Operation(summary = "Set a space's readiness directly",
            description = "SRS-SFL-S152-01. Refused with READINESS_BLOCKED when READY is requested and a "
                    + "critical blocker is open.")
    public ApiResponse<RoomResponse> updateRoomReadiness(@PathVariable UUID roomId,
            @Valid @RequestBody FacilitiesRequests.UpdateRoomReadiness request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(RoomResponse.from(readiness.setReadinessDirectly(new FacilitiesCommands.UpdateRoomReadiness(
                roomId, request.status(), request.notes(), actor, channel))));
    }

    @PatchMapping("/rooms/{roomId}/lifecycle")
    @Operation(summary = "Move a space through its lifecycle")
    public ApiResponse<RoomResponse> changeRoomLifecycle(@PathVariable UUID roomId,
            @Valid @RequestBody FacilitiesRequests.ChangeLifecycle request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(RoomResponse.from(service.changeRoomLifecycle(new FacilitiesCommands.ChangeRoomLifecycle(roomId,
                request.status(), request.expectedVersion(), actor, channel))));
    }

    // ---- zones --------------------------------------------------------------------------------

    @PostMapping("/zones")
    @Operation(summary = "Register a zone")
    public ResponseEntity<ApiResponse<ZoneResponse>> createZone(@Valid @RequestBody FacilitiesRequests.CreateZone request,
            ActorContext actor, SourceChannel channel, @IdempotencyKey String idempotencyKey) {
        ZoneResponse result = ZoneResponse.from(service.createZone(new FacilitiesCommands.CreateZone(
                request.siteCode(), request.zoneCode(), request.name(), request.purpose(),
                request.parentZoneId(), actor, channel, idempotencyKey)));
        return ResponseEntity.created(URI.create("/api/v1/facilities/zones/" + result.id())).body(ApiResponse.ok(result));
    }

    @GetMapping("/zones")
    @Operation(summary = "List zones, optionally for one site")
    public ApiResponse<List<ZoneResponse>> zones(@RequestParam(required = false) String siteCode, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(service.zones(siteCode, actor, channel).stream().map(ZoneResponse::from).toList());
    }

    @GetMapping("/zones/{zoneId}")
    @Operation(summary = "Read one zone")
    public ApiResponse<ZoneResponse> zone(@PathVariable UUID zoneId, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(ZoneResponse.from(service.zone(zoneId, actor, channel)));
    }

    @GetMapping("/zones/{zoneId}/members")
    @Operation(summary = "List what a zone covers",
            description = "Buildings, floors, spaces and devices. What S162a and S174 resolve against.")
    public ApiResponse<List<ZoneMemberResponse>> zoneMembers(@PathVariable UUID zoneId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(service.zoneMembers(zoneId, actor, channel).stream()
                .map(ZoneMemberResponse::from).toList());
    }

    @PostMapping("/zones/{zoneId}/members")
    @Operation(summary = "Add a record to a zone",
            description = "The member must belong to the zone's own site.")
    public ResponseEntity<ApiResponse<ZoneMemberResponse>> addZoneMember(@PathVariable UUID zoneId,
            @Valid @RequestBody FacilitiesRequests.AddZoneMember request, ActorContext actor, SourceChannel channel) {
        ZoneMemberResponse result = ZoneMemberResponse.from(service.addZoneMember(
                new FacilitiesCommands.AddZoneMember(zoneId, request.memberType(), request.memberId(),
                        actor, channel)));
        return ResponseEntity.created(URI.create("/api/v1/facilities/zones/" + zoneId + "/members/"
                + result.memberType() + "/" + result.memberId())).body(ApiResponse.ok(result));
    }

    @DeleteMapping("/zones/{zoneId}/members/{memberType}/{memberId}")
    @Operation(summary = "Remove a record from a zone")
    public ResponseEntity<Void> removeZoneMember(@PathVariable UUID zoneId,
            @PathVariable ZoneMemberType memberType, @PathVariable UUID memberId, ActorContext actor,
            SourceChannel channel) {
        service.removeZoneMember(new FacilitiesCommands.RemoveZoneMember(zoneId, memberType, memberId,
                actor, channel));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/zones/{zoneId}/lifecycle")
    @Operation(summary = "Move a zone through its lifecycle",
            description = "An archived zone still resolves for historical events; it takes no new members.")
    public ApiResponse<ZoneResponse> changeZoneLifecycle(@PathVariable UUID zoneId,
            @Valid @RequestBody FacilitiesRequests.ChangeLifecycle request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(ZoneResponse.from(service.changeZoneLifecycle(
                new FacilitiesCommands.ChangeZoneLifecycle(zoneId, request.status(),
                        request.expectedVersion(), actor, channel))));
    }

    // ---- device references --------------------------------------------------------------------

    @PostMapping("/device-references")
    @Operation(summary = "Register a device reference",
            description = "SRS-SFL-S152-04. SFL owns the device's identity and location; the vendor system "
                    + "operates the device.")
    public ResponseEntity<ApiResponse<DeviceReferenceResponse>> registerDeviceReference(
            @Valid @RequestBody FacilitiesRequests.RegisterDeviceReference request, ActorContext actor,
            SourceChannel channel, @IdempotencyKey String idempotencyKey) {
        DeviceReferenceResponse result = DeviceReferenceResponse.from(service.registerDeviceReference(
                new FacilitiesCommands.RegisterDeviceReference(request.siteCode(), request.deviceCode(),
                        request.name(), request.type(), request.roomId(), request.locationCode(),
                        request.vendor(), request.externalReference(), actor, channel,
                        idempotencyKey)));
        return ResponseEntity.created(URI.create("/api/v1/facilities/device-references/" + result.id()))
                .body(ApiResponse.ok(result));
    }

    @GetMapping("/device-references")
    @Operation(summary = "List device references by site, type or space")
    public ApiResponse<List<DeviceReferenceResponse>> deviceReferences(@RequestParam(required = false) String siteCode,
            @RequestParam(required = false) DeviceReferenceType type,
            @RequestParam(required = false) UUID roomId, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(service.deviceReferences(siteCode, type, roomId, actor, channel).stream()
                .map(DeviceReferenceResponse::from).toList());
    }

    @GetMapping("/device-references/{deviceId}")
    @Operation(summary = "Read one device reference")
    public ApiResponse<DeviceReferenceResponse> deviceReference(@PathVariable UUID deviceId, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(DeviceReferenceResponse.from(service.deviceReference(deviceId, actor, channel)));
    }

    @PatchMapping("/device-references/{deviceId}")
    @Operation(summary = "Correct a device reference",
            description = "Name, type, vendor and vendor reference. The status is the vendor feed's to "
                    + "report and is not settable here. Requires FACILITIES_DEVICE_REFERENCE_REGISTER.")
    public ApiResponse<DeviceReferenceResponse> updateDeviceReference(@PathVariable UUID deviceId,
            @Valid @RequestBody FacilitiesRequests.UpdateDeviceReference request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(DeviceReferenceResponse.from(service.updateDeviceReference(
                new FacilitiesCommands.UpdateDeviceReference(deviceId, request.name(), request.type(),
                        request.vendor(), request.externalReference(), request.expectedVersion(),
                        actor, channel))));
    }

    @PatchMapping("/device-references/{deviceId}/lifecycle")
    @Operation(summary = "Move a device reference through its lifecycle",
            description = "A decommissioned device stops being part of the estate map. ARCHIVED is terminal.")
    public ApiResponse<DeviceReferenceResponse> changeDeviceReferenceLifecycle(@PathVariable UUID deviceId,
            @Valid @RequestBody FacilitiesRequests.ChangeLifecycle request, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(DeviceReferenceResponse.from(service.changeDeviceReferenceLifecycle(
                new FacilitiesCommands.ChangeDeviceReferenceLifecycle(deviceId, request.status(),
                        request.expectedVersion(), actor, channel))));
    }
}
