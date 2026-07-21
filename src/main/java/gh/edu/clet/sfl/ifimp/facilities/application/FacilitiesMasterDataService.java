package gh.edu.clet.sfl.ifimp.facilities.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import gh.edu.clet.sfl.ifimp.facilities.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.ifimp.facilities.domain.Building;
import gh.edu.clet.sfl.ifimp.facilities.domain.DeviceReference;
import gh.edu.clet.sfl.ifimp.facilities.domain.FacilityFloor;
import gh.edu.clet.sfl.ifimp.facilities.domain.FacilityRoom;
import gh.edu.clet.sfl.ifimp.facilities.domain.Site;
import gh.edu.clet.sfl.ifimp.facilities.domain.Zone;
import gh.edu.clet.sfl.platform.persistence.AuditEventRecord;
import gh.edu.clet.sfl.platform.persistence.AuditEventRepository;
import gh.edu.clet.sfl.platform.persistence.OutboxMessageRecord;
import gh.edu.clet.sfl.platform.persistence.OutboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class FacilitiesMasterDataService {

    private final FacilitiesRepository facilities;
    private final AuditEventRepository auditEvents;
    private final OutboxMessageRepository outboxMessages;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public FacilitiesMasterDataService(FacilitiesRepository facilities, AuditEventRepository auditEvents,
            OutboxMessageRepository outboxMessages, ObjectMapper objectMapper, Clock clock) {
        this.facilities = facilities;
        this.auditEvents = auditEvents;
        this.outboxMessages = outboxMessages;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Site createSite(CreateSiteCommand command) {
        Site site = Site.create(UUID.randomUUID(), command.siteCode(), command.name(), command.description(), now());
        Site saved = facilities.saveSite(site);
        record("ifimp.site.created", "Site", saved.id(), command.actor(), command.correlationId(), saved);
        return saved;
    }

    @Transactional
    public Building createBuilding(CreateBuildingCommand command) {
        facilities.findSite(command.siteId()).orElseThrow(() -> new IllegalArgumentException("Site was not found: "
                + command.siteId()));
        Building building = Building.create(UUID.randomUUID(), command.siteId(), command.siteCode(),
                command.buildingCode(), command.name(), command.description(), now());
        Building saved = facilities.saveBuilding(building);
        record("ifimp.building.created", "Building", saved.id(), command.actor(), command.correlationId(), saved);
        return saved;
    }

    @Transactional
    public FacilityFloor createFloor(CreateFloorCommand command) {
        facilities.findBuilding(command.buildingId()).orElseThrow(() -> new IllegalArgumentException(
                "Building was not found: " + command.buildingId()));
        FacilityFloor floor = FacilityFloor.create(UUID.randomUUID(), command.buildingId(), command.siteCode(),
                command.floorCode(), command.name(), command.levelNumber(), now());
        FacilityFloor saved = facilities.saveFloor(floor);
        record("ifimp.floor.created", "FacilityFloor", saved.id(), command.actor(), command.correlationId(), saved);
        return saved;
    }

    @Transactional
    public FacilityRoom createRoom(CreateRoomCommand command) {
        facilities.findFloor(command.floorId()).orElseThrow(() -> new IllegalArgumentException("Floor was not found: "
                + command.floorId()));
        FacilityRoom room = FacilityRoom.create(UUID.randomUUID(), command.floorId(), command.siteCode(),
                command.roomCode(), command.name(), command.roomType(), command.capacity(), now());
        FacilityRoom saved = facilities.saveRoom(room);
        record("ifimp.room.created", "FacilityRoom", saved.id(), command.actor(), command.correlationId(), saved);
        return saved;
    }

    @Transactional
    public Zone createZone(CreateZoneCommand command) {
        Zone zone = Zone.create(UUID.randomUUID(), command.siteCode(), command.zoneCode(), command.name(),
                command.purpose(), now());
        Zone saved = facilities.saveZone(zone);
        record("ifimp.zone.created", "Zone", saved.id(), command.actor(), command.correlationId(), saved);
        return saved;
    }

    @Transactional
    public DeviceReference registerDeviceReference(RegisterDeviceReferenceCommand command) {
        DeviceReference deviceReference = DeviceReference.register(UUID.randomUUID(), command.siteCode(),
                command.deviceCode(), command.name(), command.type(), command.roomId(), command.locationCode(),
                command.vendor(), now());
        DeviceReference saved = facilities.saveDeviceReference(deviceReference);
        record("ifimp.device-reference.registered", "DeviceReference", saved.id(), command.actor(),
                command.correlationId(), saved);
        return saved;
    }

    @Transactional
    public FacilityRoom updateRoomReadiness(UpdateRoomReadinessCommand command) {
        FacilityRoom room = facilities.findRoom(command.roomId())
                .orElseThrow(() -> new IllegalArgumentException("Room was not found: " + command.roomId()));
        FacilityRoom saved = facilities.saveRoom(room.updateReadiness(command.status(), command.notes(), now()));
        record("ifimp.room-readiness.changed", "FacilityRoom", saved.id(), command.actor(),
                command.correlationId(), saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Site> sites() {
        return facilities.findSites();
    }

    @Transactional(readOnly = true)
    public List<Building> buildings(String siteCode) {
        return facilities.findBuildings(siteCode);
    }

    @Transactional(readOnly = true)
    public List<FacilityFloor> floors(UUID buildingId) {
        return facilities.findFloors(buildingId);
    }

    @Transactional(readOnly = true)
    public List<FacilityRoom> rooms(String siteCode) {
        return facilities.findRooms(siteCode);
    }

    @Transactional(readOnly = true)
    public List<Zone> zones(String siteCode) {
        return facilities.findZones(siteCode);
    }

    @Transactional(readOnly = true)
    public List<DeviceReference> deviceReferences(String siteCode) {
        return facilities.findDeviceReferences(siteCode);
    }

    private Instant now() {
        return clock.instant();
    }

    private void record(String eventType, String aggregateType, UUID aggregateId, String actor, String correlationId,
            Object payload) {
        String json = writeJson(payload);
        Instant occurredAt = now();
        auditEvents.save(new AuditEventRecord(UUID.randomUUID(), eventType, aggregateType, aggregateId,
                actorOrDevelopment(actor), "sfl-api", correlationId, occurredAt, json));
        outboxMessages.save(new OutboxMessageRecord(UUID.randomUUID(), eventType + ".v1", aggregateId,
                correlationId, json, occurredAt));
    }

    private String actorOrDevelopment(String actor) {
        return actor == null || actor.isBlank() ? "development-user" : actor;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize the integration event", exception);
        }
    }
}
