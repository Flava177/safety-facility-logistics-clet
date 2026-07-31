package gh.edu.clet.sfl.facilities.masterdata.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMembership;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The estate's write and read use cases (SRS-SFL-S152-01, -02).
 *
 * <p>Every command follows the same five steps, in this order, and the order matters:
 * <ol>
 *   <li><strong>Authorise</strong> — permission and site scope, before anything is read or written, so
 *       an unauthorised caller cannot learn from a 404 that a record exists.</li>
 *   <li><strong>Replay check</strong> — an {@code Idempotency-Key} already seen with the same payload
 *       returns the original result rather than creating a second record.</li>
 *   <li><strong>Validate</strong> — parent references resolve, identifiers are free, the caller's
 *       version is current.</li>
 *   <li><strong>Apply</strong> — the domain decides; this layer never encodes a business rule.</li>
 *   <li><strong>Record</strong> — audit and outbox, in the same transaction as the change.</li>
 * </ol>
 */
@Service
public class FacilitiesMasterDataService {

    private final FacilitiesRepository facilities;
    private final ServiceOutbox outbox;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final FacilitiesAuthorization authorization;
    private final Clock clock;

    public FacilitiesMasterDataService(FacilitiesRepository facilities, ServiceOutbox outbox, AuditPort audit,
            IdempotencyPort idempotency, FacilitiesAuthorization authorization, Clock clock) {
        this.facilities = facilities;
        this.outbox = outbox;
        this.audit = audit;
        this.idempotency = idempotency;
        this.authorization = authorization;
        this.clock = clock;
    }

    // =========================================================================================
    // Sites
    // =========================================================================================

    @Transactional
    public Site createSite(FacilitiesCommands.CreateSite command) {
        ActorContext actor = command.actor();
        String siteCode = normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_SITE_MANAGE, siteCode, command.channel(),
                "Site", siteCode);

        Optional<Site> replayed = replay("create-site", command.idempotencyKey(), command.idempotencyPayload(),
                facilities::findSite);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        facilities.findSiteByCode(siteCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("site", siteCode, siteCode);
            }
        });

        Site saved = facilities.saveSite(Site.create(UUID.randomUUID(), siteCode, command.name(),
                command.description(), actor.actorId(), now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.SITE_CREATED, "Site", saved.id().toString(),
                saved.siteCode(), null, saved);
        publish("ifimp.site.created", "Site", saved.id(), saved.siteCode(), actor, saved);
        remember("create-site", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    @Transactional
    public Site updateSite(FacilitiesCommands.UpdateSite command) {
        ActorContext actor = command.actor();
        Site site = requireSite(command.siteId());
        authorization.require(actor, SflPermission.FACILITIES_SITE_MANAGE, site.siteCode(), command.channel(),
                "Site", site.id().toString());
        site.metadata().requireVersion(command.expectedVersion(), "Site", site.id());

        Site saved = facilities.saveSite(site.update(command.name(), command.description(), actor.actorId(),
                now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.SITE_UPDATED, "Site", saved.id().toString(),
                saved.siteCode(), site, saved);
        publish("ifimp.site.updated", "Site", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    @Transactional
    public Site changeSiteLifecycle(FacilitiesCommands.ChangeSiteLifecycle command) {
        ActorContext actor = command.actor();
        Site site = requireSite(command.siteId());
        authorization.require(actor, SflPermission.FACILITIES_SITE_MANAGE, site.siteCode(), command.channel(),
                "Site", site.id().toString());
        site.metadata().requireVersion(command.expectedVersion(), "Site", site.id());

        Site saved = facilities.saveSite(site.changeLifecycle(command.status(), actor.actorId(), now(),
                command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.SITE_LIFECYCLE_CHANGED, "Site",
                saved.id().toString(), saved.siteCode(), site.lifecycleStatus(), saved.lifecycleStatus());
        publish("ifimp.site.lifecycle-changed", "Site", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    /**
     * Declares or stands down examination mode (NFR 23.3).
     *
     * <p>Its own permission, its own audit action and its own event, because the mode change is the
     * decision — every stricter rule that follows is a consequence of it, and burying it inside a
     * general site update would make the one thing a reviewer looks for invisible.
     */
    @Transactional
    public Site changeOperatingMode(FacilitiesCommands.ChangeOperatingMode command) {
        ActorContext actor = command.actor();
        Site site = requireSite(command.siteId());
        authorization.require(actor, SflPermission.FACILITIES_OPERATING_MODE_CHANGE, site.siteCode(),
                command.channel(), "Site", site.id().toString());

        OperatingMode previous = site.operatingMode();
        Site saved = facilities.saveSite(site.changeOperatingMode(command.operatingMode(), actor.actorId(),
                now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.SITE_OPERATING_MODE_CHANGED, "Site",
                saved.id().toString(), saved.siteCode(),
                new ModeChange(previous, null), new ModeChange(saved.operatingMode(), command.reason()));
        publish("ifimp.site.operating-mode-changed", "Site", saved.id(), saved.siteCode(), actor,
                new ModeChangeEvent(saved.siteCode(), previous, saved.operatingMode(), command.reason(),
                        actor.actorId(), saved.operatingModeChangedAt()));
        return saved;
    }

    private record ModeChange(OperatingMode operatingMode, String reason) {
    }

    private record ModeChangeEvent(String siteCode, OperatingMode from, OperatingMode to, String reason,
            String changedBy, Instant changedAt) {
    }

    // =========================================================================================
    // Buildings and floors
    // =========================================================================================

    @Transactional
    public Building createBuilding(FacilitiesCommands.CreateBuilding command) {
        ActorContext actor = command.actor();
        Site site = facilities.findSite(command.siteId())
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Site",
                        command.siteId()));
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, site.siteCode(), command.channel(),
                "Building", normalize(command.buildingCode()));

        Optional<Building> replayed = replay("create-building", command.idempotencyKey(),
                command.idempotencyPayload(), facilities::findBuilding);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String buildingCode = normalize(command.buildingCode());
        facilities.findBuildingByCode(site.siteCode(), buildingCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("building", buildingCode,
                        site.siteCode());
            }
        });

        Building saved = facilities.saveBuilding(Building.create(UUID.randomUUID(), site.id(), site.siteCode(),
                buildingCode, command.name(), command.description(), actor.actorId(), now(), command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.BUILDING_CREATED, "Building",
                saved.id().toString(), saved.siteCode(), null, saved);
        publish("ifimp.building.created", "Building", saved.id(), saved.siteCode(), actor, saved);
        remember("create-building", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    @Transactional
    public FacilityFloor createFloor(FacilitiesCommands.CreateFloor command) {
        ActorContext actor = command.actor();
        Building building = facilities.findBuilding(command.buildingId())
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Building",
                        command.buildingId()));
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, building.siteCode(),
                command.channel(), "FacilityFloor", normalize(command.floorCode()));

        Optional<FacilityFloor> replayed = replay("create-floor", command.idempotencyKey(),
                command.idempotencyPayload(), facilities::findFloor);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String floorCode = normalize(command.floorCode());
        facilities.findFloorByCode(building.id(), floorCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("floor", floorCode,
                        building.siteCode());
            }
        });

        FacilityFloor saved = facilities.saveFloor(FacilityFloor.create(UUID.randomUUID(), building.id(),
                building.siteCode(), floorCode, command.name(), command.levelNumber(), actor.actorId(), now(),
                command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.FLOOR_CREATED, "FacilityFloor",
                saved.id().toString(), saved.siteCode(), null, saved);
        publish("ifimp.floor.created", "FacilityFloor", saved.id(), saved.siteCode(), actor, saved);
        remember("create-floor", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    // =========================================================================================
    // Spaces
    // =========================================================================================

    @Transactional
    public FacilityRoom createRoom(FacilitiesCommands.CreateRoom command) {
        ActorContext actor = command.actor();
        FacilityFloor floor = facilities.findFloor(command.floorId())
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Floor",
                        command.floorId()));
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, floor.siteCode(), command.channel(),
                "FacilityRoom", normalize(command.roomCode()));

        Optional<FacilityRoom> replayed = replay("create-room", command.idempotencyKey(),
                command.idempotencyPayload(), facilities::findRoom);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String roomCode = normalize(command.roomCode());
        facilities.findRoomByCode(floor.siteCode(), roomCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("space", roomCode, floor.siteCode());
            }
        });

        FacilityRoom saved = facilities.saveRoom(FacilityRoom.create(UUID.randomUUID(), floor.id(),
                floor.siteCode(), roomCode, command.name(), command.spaceType(), command.capacity(),
                command.areaSqm(), command.costCentre(), command.bookable(), command.examinationCapable(),
                actor.actorId(), now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ROOM_CREATED, "FacilityRoom",
                saved.id().toString(), saved.siteCode(), null, saved);
        publish("ifimp.room.created", "FacilityRoom", saved.id(), saved.siteCode(), actor, saved);
        remember("create-room", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    @Transactional
    public FacilityRoom updateRoom(FacilitiesCommands.UpdateRoom command) {
        ActorContext actor = command.actor();
        FacilityRoom room = requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, room.siteCode(), command.channel(),
                "FacilityRoom", room.id().toString());
        room.metadata().requireVersion(command.expectedVersion(), "Space", room.id());

        FacilityRoom saved = facilities.saveRoom(room.update(command.name(), command.spaceType(),
                command.capacity(), command.areaSqm(), command.costCentre(), command.bookable(),
                command.examinationCapable(), actor.actorId(), now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ROOM_UPDATED, "FacilityRoom", saved.id().toString(),
                saved.siteCode(), room, saved);
        publish("ifimp.room.updated", "FacilityRoom", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    @Transactional
    public FacilityRoom changeRoomLifecycle(FacilitiesCommands.ChangeRoomLifecycle command) {
        ActorContext actor = command.actor();
        FacilityRoom room = requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, room.siteCode(), command.channel(),
                "FacilityRoom", room.id().toString());
        room.metadata().requireVersion(command.expectedVersion(), "Space", room.id());

        FacilityRoom saved = facilities.saveRoom(room.changeLifecycle(command.status(), actor.actorId(), now(),
                command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ROOM_LIFECYCLE_CHANGED, "FacilityRoom",
                saved.id().toString(), saved.siteCode(), room.lifecycleStatus(), saved.lifecycleStatus());
        publish("ifimp.room.lifecycle-changed", "FacilityRoom", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    // =========================================================================================
    // Zones
    // =========================================================================================

    @Transactional
    public Zone createZone(FacilitiesCommands.CreateZone command) {
        ActorContext actor = command.actor();
        String siteCode = normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_ZONE_MANAGE, siteCode, command.channel(),
                "Zone", normalize(command.zoneCode()));

        Optional<Zone> replayed = replay("create-zone", command.idempotencyKey(), command.idempotencyPayload(),
                facilities::findZone);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String zoneCode = normalize(command.zoneCode());
        facilities.findZoneByCode(siteCode, zoneCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("zone", zoneCode, siteCode);
            }
        });
        if (command.parentZoneId() != null) {
            Zone parent = facilities.findZone(command.parentZoneId())
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Zone",
                            command.parentZoneId()));
            if (!parent.siteCode().equals(siteCode)) {
                throw new FacilitiesException.ValidationFailedException(
                        "A zone's parent must belong to the same site.");
            }
        }

        Zone saved = facilities.saveZone(Zone.create(UUID.randomUUID(), siteCode, zoneCode, command.name(),
                command.purpose(), command.parentZoneId(), actor.actorId(), now(), command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ZONE_CREATED, "Zone", saved.id().toString(),
                saved.siteCode(), null, saved);
        publish("ifimp.zone.created", "Zone", saved.id(), saved.siteCode(), actor, saved);
        remember("create-zone", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    /**
     * Adds a record to a zone.
     *
     * <p>The member must belong to the zone's own site. Without that check a zone could reach across
     * sites, and an evacuation broadcast addressed to it would page a building three hundred kilometres
     * from the fire.
     */
    @Transactional
    public ZoneMembership addZoneMember(FacilitiesCommands.AddZoneMember command) {
        ActorContext actor = command.actor();
        Zone zone = facilities.findZone(command.zoneId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Zone", command.zoneId()));
        authorization.require(actor, SflPermission.FACILITIES_ZONE_MANAGE, zone.siteCode(), command.channel(),
                "Zone", zone.id().toString());

        String memberSite = resolveMemberSite(command.memberType(), command.memberId());
        if (!zone.siteCode().equals(memberSite)) {
            throw new FacilitiesException.ValidationFailedException(
                    "A zone member must belong to the zone's site (" + zone.siteCode() + ").");
        }

        ZoneMembership saved = facilities.saveZoneMembership(ZoneMembership.of(zone.id(), command.memberType(),
                command.memberId(), zone.siteCode(), actor.actorId(), now()));

        audit.record(actor, command.channel(), AuditAction.ZONE_MEMBER_ADDED, "Zone", zone.id().toString(),
                zone.siteCode(), null, saved);
        publish("ifimp.zone.member-added", "Zone", zone.id(), zone.siteCode(), actor, saved);
        return saved;
    }

    @Transactional
    public void removeZoneMember(FacilitiesCommands.RemoveZoneMember command) {
        ActorContext actor = command.actor();
        Zone zone = facilities.findZone(command.zoneId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Zone", command.zoneId()));
        authorization.require(actor, SflPermission.FACILITIES_ZONE_MANAGE, zone.siteCode(), command.channel(),
                "Zone", zone.id().toString());

        facilities.deleteZoneMembership(zone.id(), command.memberType(), command.memberId());

        ZoneMemberRef removed = new ZoneMemberRef(command.memberType(), command.memberId());
        audit.record(actor, command.channel(), AuditAction.ZONE_MEMBER_REMOVED, "Zone", zone.id().toString(),
                zone.siteCode(), removed, null);
        publish("ifimp.zone.member-removed", "Zone", zone.id(), zone.siteCode(), actor, removed);
    }

    private record ZoneMemberRef(ZoneMemberType memberType, UUID memberId) {
    }

    // =========================================================================================
    // Device references
    // =========================================================================================

    @Transactional
    public DeviceReference registerDeviceReference(FacilitiesCommands.RegisterDeviceReference command) {
        ActorContext actor = command.actor();
        String siteCode = normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_DEVICE_REFERENCE_REGISTER, siteCode,
                command.channel(), "DeviceReference", normalize(command.deviceCode()));

        Optional<DeviceReference> replayed = replay("register-device-reference", command.idempotencyKey(),
                command.idempotencyPayload(), facilities::findDeviceReference);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String deviceCode = normalize(command.deviceCode());
        facilities.findDeviceReferenceByCode(siteCode, deviceCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("device reference", deviceCode,
                        siteCode);
            }
        });
        if (command.roomId() != null) {
            requireRoomInSite(command.roomId(), siteCode);
        }

        DeviceReference saved = facilities.saveDeviceReference(DeviceReference.register(UUID.randomUUID(),
                siteCode, deviceCode, command.name(), command.type(), command.roomId(), command.locationCode(),
                command.vendor(), command.externalReference(), actor.actorId(), now(), command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.DEVICE_REFERENCE_REGISTERED, "DeviceReference",
                saved.id().toString(), saved.siteCode(), null, saved);
        publish("ifimp.device-reference.registered", "DeviceReference", saved.id(), saved.siteCode(), actor,
                saved);
        remember("register-device-reference", command.idempotencyKey(), command.idempotencyPayload(),
                saved.id(), saved.siteCode(), actor);
        return saved;
    }

    // =========================================================================================
    // Queries
    // =========================================================================================

    @Transactional(readOnly = true)
    public List<Site> sites(ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SITE_READ, channel, "Site", "list", null);
        return authorization.filterBySite(actor, facilities.findSites(), Site::siteCode);
    }

    @Transactional(readOnly = true)
    public Site site(UUID id, ActorContext actor, SourceChannel channel) {
        Site site = requireSite(id);
        authorization.require(actor, SflPermission.FACILITIES_SITE_READ, site.siteCode(), channel, "Site",
                id.toString());
        return site;
    }

    @Transactional(readOnly = true)
    public List<Building> buildings(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, channel, "Building", "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "Building");
        return authorization.filterBySite(actor, facilities.findBuildings(siteCode), Building::siteCode);
    }

    @Transactional(readOnly = true)
    public Building building(UUID id, ActorContext actor, SourceChannel channel) {
        Building building = facilities.findBuilding(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Building", id));
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, building.siteCode(), channel,
                "Building", id.toString());
        return building;
    }

    @Transactional(readOnly = true)
    public List<FacilityFloor> floors(UUID buildingId, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, channel, "FacilityFloor", "list", null);
        return authorization.filterBySite(actor, facilities.findFloors(buildingId), FacilityFloor::siteCode);
    }

    @Transactional(readOnly = true)
    public FacilityFloor floor(UUID id, ActorContext actor, SourceChannel channel) {
        FacilityFloor floor = facilities.findFloor(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Floor", id));
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, floor.siteCode(), channel,
                "FacilityFloor", id.toString());
        return floor;
    }

    @Transactional(readOnly = true)
    public List<FacilityRoom> rooms(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, channel, "FacilityRoom", "list",
                siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "FacilityRoom");
        return authorization.filterBySite(actor, facilities.findRooms(siteCode), FacilityRoom::siteCode);
    }

    @Transactional(readOnly = true)
    public FacilitiesRepository.Page<FacilityRoom> searchRooms(FacilitiesRepository.RoomQuery query,
            ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, channel, "FacilityRoom", "search",
                query.siteCode());
        authorization.requireRequestedSite(actor, query.siteCode(), channel, "FacilityRoom");
        FacilitiesRepository.Page<FacilityRoom> page = facilities.searchRooms(query);
        List<FacilityRoom> visible = authorization.filterBySite(actor, page.items(), FacilityRoom::siteCode);
        // When filtering removed rows, the total is reported as what remains: a total counting records
        // the caller may not see would let them infer another site's estate size.
        return visible.size() == page.items().size()
                ? page
                : FacilitiesRepository.Page.of(visible, visible.size(), page.page(), page.size());
    }

    @Transactional(readOnly = true)
    public FacilityRoom room(UUID id, ActorContext actor, SourceChannel channel) {
        FacilityRoom room = requireRoom(id);
        authorization.require(actor, SflPermission.FACILITIES_SPACE_READ, room.siteCode(), channel,
                "FacilityRoom", id.toString());
        return room;
    }

    @Transactional(readOnly = true)
    public List<Zone> zones(String siteCode, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_ZONE_READ, channel, "Zone", "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "Zone");
        return authorization.filterBySite(actor, facilities.findZones(siteCode), Zone::siteCode);
    }

    @Transactional(readOnly = true)
    public Zone zone(UUID id, ActorContext actor, SourceChannel channel) {
        Zone zone = facilities.findZone(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Zone", id));
        authorization.require(actor, SflPermission.FACILITIES_ZONE_READ, zone.siteCode(), channel, "Zone",
                id.toString());
        return zone;
    }

    @Transactional(readOnly = true)
    public List<ZoneMembership> zoneMembers(UUID zoneId, ActorContext actor, SourceChannel channel) {
        Zone zone = facilities.findZone(zoneId)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Zone", zoneId));
        authorization.require(actor, SflPermission.FACILITIES_ZONE_READ, zone.siteCode(), channel, "Zone",
                zoneId.toString());
        return facilities.findZoneMembers(zoneId);
    }

    @Transactional(readOnly = true)
    public List<DeviceReference> deviceReferences(String siteCode, DeviceReferenceType type, UUID roomId,
            ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_DEVICE_REFERENCE_READ, channel, "DeviceReference",
                "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "DeviceReference");
        return authorization.filterBySite(actor, facilities.findDeviceReferences(siteCode, type, roomId),
                DeviceReference::siteCode);
    }

    @Transactional(readOnly = true)
    public DeviceReference deviceReference(UUID id, ActorContext actor, SourceChannel channel) {
        DeviceReference device = facilities.findDeviceReference(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Device reference", id));
        authorization.require(actor, SflPermission.FACILITIES_DEVICE_REFERENCE_READ, device.siteCode(), channel,
                "DeviceReference", id.toString());
        return device;
    }

    // =========================================================================================
    // Internals
    // =========================================================================================

    private Site requireSite(UUID id) {
        return facilities.findSite(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Site", id));
    }

    private FacilityRoom requireRoom(UUID id) {
        return facilities.findRoom(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Space", id));
    }

    private void requireRoomInSite(UUID roomId, String siteCode) {
        FacilityRoom room = requireRoom(roomId);
        if (!room.siteCode().equals(siteCode)) {
            throw new FacilitiesException.ValidationFailedException(
                    "Space " + room.roomCode() + " belongs to site " + room.siteCode() + ", not " + siteCode
                            + ".");
        }
    }

    /** The site a zone member belongs to, which is what constrains it to the zone's own site. */
    private String resolveMemberSite(ZoneMemberType memberType, UUID memberId) {
        return switch (memberType) {
            case BUILDING -> facilities.findBuilding(memberId)
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Building",
                            memberId))
                    .siteCode();
            case FLOOR -> facilities.findFloor(memberId)
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Floor", memberId))
                    .siteCode();
            case ROOM -> requireRoom(memberId).siteCode();
            case DEVICE -> facilities.findDeviceReference(memberId)
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException(
                            "Device reference", memberId))
                    .siteCode();
        };
    }

    private <T> Optional<T> replay(String operation, String idempotencyKey, Object payload,
            Function<UUID, Optional<T>> lookup) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return idempotency.findExistingResult(operation, idempotencyKey, idempotency.fingerprint(payload))
                .flatMap(lookup);
    }

    private void remember(String operation, String idempotencyKey, Object payload, UUID resultId,
            String siteCode, ActorContext actor) {
        idempotency.recordResult(operation, idempotencyKey, idempotency.fingerprint(payload), resultId,
                siteCode, actor.actorId());
    }

    private void publish(String eventType, String aggregateType, UUID aggregateId, String siteScope,
            ActorContext actor, Object payload) {
        outbox.record(eventType, 1, aggregateType, aggregateId, siteScope, actor.correlationId(),
                actor.actorId(), payload);
    }

    private Instant now() {
        return clock.instant();
    }

    /**
     * Normalises an identifier, refusing a blank one.
     *
     * <p>A blank site code is {@code MISSING_SITE_SCOPE} rather than a validation failure, because
     * that is the SRS's own name for it: "Select a valid CLET site before saving this record."
     */
    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new FacilitiesException.MissingSiteScopeException();
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }
}
