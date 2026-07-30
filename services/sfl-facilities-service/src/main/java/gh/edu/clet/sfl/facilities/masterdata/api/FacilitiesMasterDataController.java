package gh.edu.clet.sfl.facilities.masterdata.api;

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
import gh.edu.clet.sfl.facilities.shared.api.FacilitiesActorResolver;
import gh.edu.clet.sfl.facilities.shared.api.PageResponse;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * invariants, audit and event publication all live behind the application boundary — the ArchUnit
 * boundary test enforces that this class cannot reach past it.
 */
@RestController
@RequestMapping("/api/v1/facilities")
@Tag(name = "S152 Estate", description = "Sites, buildings, floors, spaces, zones and device references")
public class FacilitiesMasterDataController {

    private final FacilitiesMasterDataService service;
    private final ReadinessApplicationService readiness;
    private final FacilitiesActorResolver actorResolver;

    public FacilitiesMasterDataController(FacilitiesMasterDataService service,
            ReadinessApplicationService readiness, FacilitiesActorResolver actorResolver) {
        this.service = service;
        this.readiness = readiness;
        this.actorResolver = actorResolver;
    }

    // ---- sites --------------------------------------------------------------------------------

    @PostMapping("/sites")
    @Operation(summary = "Register a site", description = "SRS-SFL-S152-01. Accepts an Idempotency-Key.")
    public ResponseEntity<SiteResponse> createSite(@Valid @RequestBody FacilitiesRequests.CreateSite request,
            HttpServletRequest http) {
        SiteResponse result = SiteResponse.from(service.createSite(new FacilitiesCommands.CreateSite(
                request.siteCode(), request.name(), request.description(), actor(http), channel(http),
                idempotencyKey(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/sites/" + result.id())).body(result);
    }

    @GetMapping("/sites")
    @Operation(summary = "List the sites the actor is scoped to")
    public List<SiteResponse> sites(HttpServletRequest http) {
        return service.sites(actor(http), channel(http)).stream().map(SiteResponse::from).toList();
    }

    @GetMapping("/sites/{siteId}")
    @Operation(summary = "Read one site")
    public SiteResponse site(@PathVariable UUID siteId, HttpServletRequest http) {
        return SiteResponse.from(service.site(siteId, actor(http), channel(http)));
    }

    @PatchMapping("/sites/{siteId}")
    @Operation(summary = "Update a site's operational attributes")
    public SiteResponse updateSite(@PathVariable UUID siteId,
            @Valid @RequestBody FacilitiesRequests.UpdateSite request, HttpServletRequest http) {
        return SiteResponse.from(service.updateSite(new FacilitiesCommands.UpdateSite(siteId, request.name(),
                request.description(), request.expectedVersion(), actor(http), channel(http))));
    }

    @PatchMapping("/sites/{siteId}/lifecycle")
    @Operation(summary = "Move a site through its lifecycle",
            description = "ACTIVE, INACTIVE, SUSPENDED or ARCHIVED. ARCHIVED is terminal.")
    public SiteResponse changeSiteLifecycle(@PathVariable UUID siteId,
            @Valid @RequestBody FacilitiesRequests.ChangeLifecycle request, HttpServletRequest http) {
        return SiteResponse.from(service.changeSiteLifecycle(new FacilitiesCommands.ChangeSiteLifecycle(siteId,
                request.status(), request.expectedVersion(), actor(http), channel(http))));
    }

    @PatchMapping("/sites/{siteId}/operating-mode")
    @Operation(summary = "Declare or stand down examination mode",
            description = "NFR 23.3. Requires FACILITIES_OPERATING_MODE_CHANGE and is audited.")
    public SiteResponse changeOperatingMode(@PathVariable UUID siteId,
            @Valid @RequestBody FacilitiesRequests.ChangeOperatingMode request, HttpServletRequest http) {
        return SiteResponse.from(service.changeOperatingMode(new FacilitiesCommands.ChangeOperatingMode(siteId,
                request.operatingMode(), request.reason(), actor(http), channel(http))));
    }

    // ---- buildings ----------------------------------------------------------------------------

    @PostMapping("/buildings")
    @Operation(summary = "Register a building")
    public ResponseEntity<BuildingResponse> createBuilding(
            @Valid @RequestBody FacilitiesRequests.CreateBuilding request, HttpServletRequest http) {
        BuildingResponse result = BuildingResponse.from(service.createBuilding(
                new FacilitiesCommands.CreateBuilding(request.siteId(), request.buildingCode(), request.name(),
                        request.description(), actor(http), channel(http), idempotencyKey(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/buildings/" + result.id())).body(result);
    }

    @GetMapping("/buildings")
    @Operation(summary = "List buildings, optionally for one site")
    public List<BuildingResponse> buildings(@RequestParam(required = false) String siteCode,
            HttpServletRequest http) {
        return service.buildings(siteCode, actor(http), channel(http)).stream()
                .map(BuildingResponse::from).toList();
    }

    @GetMapping("/buildings/{buildingId}")
    @Operation(summary = "Read one building")
    public BuildingResponse building(@PathVariable UUID buildingId, HttpServletRequest http) {
        return BuildingResponse.from(service.building(buildingId, actor(http), channel(http)));
    }

    // ---- floors -------------------------------------------------------------------------------

    @PostMapping("/floors")
    @Operation(summary = "Register a floor")
    public ResponseEntity<FloorResponse> createFloor(@Valid @RequestBody FacilitiesRequests.CreateFloor request,
            HttpServletRequest http) {
        FloorResponse result = FloorResponse.from(service.createFloor(new FacilitiesCommands.CreateFloor(
                request.buildingId(), request.floorCode(), request.name(), request.levelNumber(), actor(http),
                channel(http), idempotencyKey(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/floors/" + result.id())).body(result);
    }

    @GetMapping("/buildings/{buildingId}/floors")
    @Operation(summary = "List a building's floors, lowest level first")
    public List<FloorResponse> floors(@PathVariable UUID buildingId, HttpServletRequest http) {
        return service.floors(buildingId, actor(http), channel(http)).stream().map(FloorResponse::from).toList();
    }

    @GetMapping("/floors/{floorId}")
    @Operation(summary = "Read one floor")
    public FloorResponse floor(@PathVariable UUID floorId, HttpServletRequest http) {
        return FloorResponse.from(service.floor(floorId, actor(http), channel(http)));
    }

    // ---- spaces -------------------------------------------------------------------------------

    @PostMapping("/rooms")
    @Operation(summary = "Register a space",
            description = "Room, hall, moot courtroom or plant room. Bookable and examination-capable "
                    + "default from the space type unless stated.")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody FacilitiesRequests.CreateRoom request,
            HttpServletRequest http) {
        RoomResponse result = RoomResponse.from(service.createRoom(new FacilitiesCommands.CreateRoom(
                request.floorId(), request.roomCode(), request.name(), request.spaceType(), request.capacity(),
                request.areaSqm(), request.costCentre(), request.bookable(), request.examinationCapable(),
                actor(http), channel(http), idempotencyKey(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/rooms/" + result.id())).body(result);
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
    public List<RoomResponse> rooms(@RequestParam(required = false) String siteCode, HttpServletRequest http) {
        return service.rooms(siteCode, actor(http), channel(http)).stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/rooms/search")
    @Operation(summary = "Search spaces by site, building, floor, type, readiness and availability")
    public PageResponse<RoomResponse> searchRooms(
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID floorId,
            @RequestParam(required = false) SpaceType spaceType,
            @RequestParam(required = false) LocationReadinessStatus readinessStatus,
            @RequestParam(required = false) Boolean bookable,
            @RequestParam(required = false) Boolean examinationCapable,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest http) {
        FacilitiesRepository.RoomQuery query = new FacilitiesRepository.RoomQuery(siteCode, buildingId, floorId,
                spaceType, readinessStatus, bookable, examinationCapable, page, size);
        return PageResponse.from(service.searchRooms(query, actor(http), channel(http)), RoomResponse::from);
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Read one space")
    public RoomResponse room(@PathVariable UUID roomId, HttpServletRequest http) {
        return RoomResponse.from(service.room(roomId, actor(http), channel(http)));
    }

    @PatchMapping("/rooms/{roomId}")
    @Operation(summary = "Update a space's attributes",
            description = "Refused while the space's readiness lock is engaged.")
    public RoomResponse updateRoom(@PathVariable UUID roomId,
            @Valid @RequestBody FacilitiesRequests.UpdateRoom request, HttpServletRequest http) {
        return RoomResponse.from(service.updateRoom(new FacilitiesCommands.UpdateRoom(roomId, request.name(),
                request.spaceType(), request.capacity(), request.areaSqm(), request.costCentre(),
                request.bookable(), request.examinationCapable(), request.expectedVersion(), actor(http),
                channel(http))));
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
    public RoomResponse updateRoomReadiness(@PathVariable UUID roomId,
            @Valid @RequestBody FacilitiesRequests.UpdateRoomReadiness request, HttpServletRequest http) {
        return RoomResponse.from(readiness.setReadinessDirectly(new FacilitiesCommands.UpdateRoomReadiness(
                roomId, request.status(), request.notes(), actor(http), channel(http))));
    }

    @PatchMapping("/rooms/{roomId}/lifecycle")
    @Operation(summary = "Move a space through its lifecycle")
    public RoomResponse changeRoomLifecycle(@PathVariable UUID roomId,
            @Valid @RequestBody FacilitiesRequests.ChangeLifecycle request, HttpServletRequest http) {
        return RoomResponse.from(service.changeRoomLifecycle(new FacilitiesCommands.ChangeRoomLifecycle(roomId,
                request.status(), request.expectedVersion(), actor(http), channel(http))));
    }

    // ---- zones --------------------------------------------------------------------------------

    @PostMapping("/zones")
    @Operation(summary = "Register a zone")
    public ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody FacilitiesRequests.CreateZone request,
            HttpServletRequest http) {
        ZoneResponse result = ZoneResponse.from(service.createZone(new FacilitiesCommands.CreateZone(
                request.siteCode(), request.zoneCode(), request.name(), request.purpose(),
                request.parentZoneId(), actor(http), channel(http), idempotencyKey(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/zones/" + result.id())).body(result);
    }

    @GetMapping("/zones")
    @Operation(summary = "List zones, optionally for one site")
    public List<ZoneResponse> zones(@RequestParam(required = false) String siteCode, HttpServletRequest http) {
        return service.zones(siteCode, actor(http), channel(http)).stream().map(ZoneResponse::from).toList();
    }

    @GetMapping("/zones/{zoneId}")
    @Operation(summary = "Read one zone")
    public ZoneResponse zone(@PathVariable UUID zoneId, HttpServletRequest http) {
        return ZoneResponse.from(service.zone(zoneId, actor(http), channel(http)));
    }

    @GetMapping("/zones/{zoneId}/members")
    @Operation(summary = "List what a zone covers",
            description = "Buildings, floors, spaces and devices. What S162a and S174 resolve against.")
    public List<ZoneMemberResponse> zoneMembers(@PathVariable UUID zoneId, HttpServletRequest http) {
        return service.zoneMembers(zoneId, actor(http), channel(http)).stream()
                .map(ZoneMemberResponse::from).toList();
    }

    @PostMapping("/zones/{zoneId}/members")
    @Operation(summary = "Add a record to a zone",
            description = "The member must belong to the zone's own site.")
    public ResponseEntity<ZoneMemberResponse> addZoneMember(@PathVariable UUID zoneId,
            @Valid @RequestBody FacilitiesRequests.AddZoneMember request, HttpServletRequest http) {
        ZoneMemberResponse result = ZoneMemberResponse.from(service.addZoneMember(
                new FacilitiesCommands.AddZoneMember(zoneId, request.memberType(), request.memberId(),
                        actor(http), channel(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/zones/" + zoneId + "/members/"
                + result.memberType() + "/" + result.memberId())).body(result);
    }

    @DeleteMapping("/zones/{zoneId}/members/{memberType}/{memberId}")
    @Operation(summary = "Remove a record from a zone")
    public ResponseEntity<Void> removeZoneMember(@PathVariable UUID zoneId,
            @PathVariable ZoneMemberType memberType, @PathVariable UUID memberId, HttpServletRequest http) {
        service.removeZoneMember(new FacilitiesCommands.RemoveZoneMember(zoneId, memberType, memberId,
                actor(http), channel(http)));
        return ResponseEntity.noContent().build();
    }

    // ---- device references --------------------------------------------------------------------

    @PostMapping("/device-references")
    @Operation(summary = "Register a device reference",
            description = "SRS-SFL-S152-04. SFL owns the device's identity and location; the vendor system "
                    + "operates the device.")
    public ResponseEntity<DeviceReferenceResponse> registerDeviceReference(
            @Valid @RequestBody FacilitiesRequests.RegisterDeviceReference request, HttpServletRequest http) {
        DeviceReferenceResponse result = DeviceReferenceResponse.from(service.registerDeviceReference(
                new FacilitiesCommands.RegisterDeviceReference(request.siteCode(), request.deviceCode(),
                        request.name(), request.type(), request.roomId(), request.locationCode(),
                        request.vendor(), request.externalReference(), actor(http), channel(http),
                        idempotencyKey(http))));
        return ResponseEntity.created(URI.create("/api/v1/facilities/device-references/" + result.id()))
                .body(result);
    }

    @GetMapping("/device-references")
    @Operation(summary = "List device references by site, type or space")
    public List<DeviceReferenceResponse> deviceReferences(@RequestParam(required = false) String siteCode,
            @RequestParam(required = false) DeviceReferenceType type,
            @RequestParam(required = false) UUID roomId, HttpServletRequest http) {
        return service.deviceReferences(siteCode, type, roomId, actor(http), channel(http)).stream()
                .map(DeviceReferenceResponse::from).toList();
    }

    @GetMapping("/device-references/{deviceId}")
    @Operation(summary = "Read one device reference")
    public DeviceReferenceResponse deviceReference(@PathVariable UUID deviceId, HttpServletRequest http) {
        return DeviceReferenceResponse.from(service.deviceReference(deviceId, actor(http), channel(http)));
    }

    private ActorContext actor(HttpServletRequest http) {
        return actorResolver.resolve(http);
    }

    private SourceChannel channel(HttpServletRequest http) {
        return actorResolver.resolveSourceChannel(http);
    }

    private String idempotencyKey(HttpServletRequest http) {
        return actorResolver.resolveIdempotencyKey(http);
    }
}
