package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMembership;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * An in-memory estate, for exercising the application services without a database.
 *
 * <p>The workflow tests are about authorisation, idempotency, duplicate detection and readiness
 * recomputation — decisions that live above persistence. Running them against maps keeps them fast
 * and keeps a failure pointing at the rule rather than at a mapping. Persistence itself is covered by
 * the Testcontainers integration test.
 */
public class InMemoryFacilitiesRepository implements FacilitiesRepository {

    private final Map<UUID, Site> sites = new LinkedHashMap<>();
    private final Map<UUID, Building> buildings = new LinkedHashMap<>();
    private final Map<UUID, FacilityFloor> floors = new LinkedHashMap<>();
    private final Map<UUID, FacilityRoom> rooms = new LinkedHashMap<>();
    private final Map<UUID, Zone> zones = new LinkedHashMap<>();
    private final Map<UUID, DeviceReference> devices = new LinkedHashMap<>();
    private final Map<UUID, FacilityAsset> assets = new LinkedHashMap<>();
    private final List<ZoneMembership> memberships = new ArrayList<>();

    @Override
    public Site saveSite(Site site) {
        sites.put(site.id(), site);
        return site;
    }

    @Override
    public Building saveBuilding(Building building) {
        buildings.put(building.id(), building);
        return building;
    }

    @Override
    public FacilityFloor saveFloor(FacilityFloor floor) {
        floors.put(floor.id(), floor);
        return floor;
    }

    @Override
    public FacilityRoom saveRoom(FacilityRoom room) {
        rooms.put(room.id(), room);
        return room;
    }

    @Override
    public Zone saveZone(Zone zone) {
        zones.put(zone.id(), zone);
        return zone;
    }

    @Override
    public DeviceReference saveDeviceReference(DeviceReference deviceReference) {
        devices.put(deviceReference.id(), deviceReference);
        return deviceReference;
    }

    @Override
    public FacilityAsset saveAsset(FacilityAsset asset) {
        assets.put(asset.id(), asset);
        return asset;
    }

    @Override
    public ZoneMembership saveZoneMembership(ZoneMembership membership) {
        memberships.removeIf(existing -> existing.zoneId().equals(membership.zoneId())
                && existing.memberType() == membership.memberType()
                && existing.memberId().equals(membership.memberId()));
        memberships.add(membership);
        return membership;
    }

    @Override
    public void deleteZoneMembership(UUID zoneId, ZoneMemberType memberType, UUID memberId) {
        memberships.removeIf(existing -> existing.zoneId().equals(zoneId)
                && existing.memberType() == memberType
                && existing.memberId().equals(memberId));
    }

    @Override
    public Optional<Site> findSite(UUID id) {
        return Optional.ofNullable(sites.get(id));
    }

    @Override
    public Optional<Building> findBuilding(UUID id) {
        return Optional.ofNullable(buildings.get(id));
    }

    @Override
    public Optional<FacilityFloor> findFloor(UUID id) {
        return Optional.ofNullable(floors.get(id));
    }

    @Override
    public Optional<FacilityRoom> findRoom(UUID id) {
        return Optional.ofNullable(rooms.get(id));
    }

    @Override
    public Optional<Zone> findZone(UUID id) {
        return Optional.ofNullable(zones.get(id));
    }

    @Override
    public Optional<DeviceReference> findDeviceReference(UUID id) {
        return Optional.ofNullable(devices.get(id));
    }

    @Override
    public Optional<FacilityAsset> findAsset(UUID id) {
        return Optional.ofNullable(assets.get(id));
    }

    @Override
    public Optional<Site> findSiteByCode(String siteCode) {
        return sites.values().stream().filter(site -> site.siteCode().equals(upper(siteCode))).findFirst();
    }

    @Override
    public Optional<Building> findBuildingByCode(String siteCode, String buildingCode) {
        return buildings.values().stream()
                .filter(building -> building.siteCode().equals(upper(siteCode))
                        && building.buildingCode().equals(upper(buildingCode)))
                .findFirst();
    }

    @Override
    public Optional<FacilityFloor> findFloorByCode(UUID buildingId, String floorCode) {
        return floors.values().stream()
                .filter(floor -> floor.buildingId().equals(buildingId)
                        && floor.floorCode().equals(upper(floorCode)))
                .findFirst();
    }

    @Override
    public Optional<FacilityRoom> findRoomByCode(String siteCode, String roomCode) {
        return rooms.values().stream()
                .filter(room -> room.siteCode().equals(upper(siteCode))
                        && room.roomCode().equals(upper(roomCode)))
                .findFirst();
    }

    @Override
    public Optional<Zone> findZoneByCode(String siteCode, String zoneCode) {
        return zones.values().stream()
                .filter(zone -> zone.siteCode().equals(upper(siteCode))
                        && zone.zoneCode().equals(upper(zoneCode)))
                .findFirst();
    }

    @Override
    public Optional<DeviceReference> findDeviceReferenceByCode(String siteCode, String deviceCode) {
        return devices.values().stream()
                .filter(device -> device.siteCode().equals(upper(siteCode))
                        && device.deviceCode().equals(upper(deviceCode)))
                .findFirst();
    }

    @Override
    public Optional<FacilityAsset> findAssetByCode(String siteCode, String assetCode) {
        return assets.values().stream()
                .filter(asset -> asset.siteCode().equals(upper(siteCode))
                        && asset.assetCode().equals(upper(assetCode)))
                .findFirst();
    }

    @Override
    public List<Site> findSites() {
        return sites.values().stream().sorted(Comparator.comparing(Site::siteCode)).toList();
    }

    @Override
    public List<Building> findBuildings(String siteCode) {
        return buildings.values().stream()
                .filter(building -> siteCode == null || building.siteCode().equals(upper(siteCode)))
                .toList();
    }

    @Override
    public List<FacilityFloor> findFloors(UUID buildingId) {
        return floors.values().stream()
                .filter(floor -> buildingId == null || floor.buildingId().equals(buildingId))
                .toList();
    }

    @Override
    public List<FacilityRoom> findRooms(String siteCode) {
        return rooms.values().stream()
                .filter(room -> siteCode == null || room.siteCode().equals(upper(siteCode)))
                .toList();
    }

    @Override
    public List<FacilityRoom> findRoomsByFloor(UUID floorId) {
        return rooms.values().stream().filter(room -> room.floorId().equals(floorId)).toList();
    }

    @Override
    public Page<FacilityRoom> searchRooms(RoomQuery query) {
        List<FacilityRoom> matches = rooms.values().stream()
                .filter(room -> query.siteCode() == null || room.siteCode().equals(upper(query.siteCode())))
                .filter(room -> query.floorId() == null || room.floorId().equals(query.floorId()))
                .filter(room -> query.spaceType() == null || room.spaceType() == query.spaceType())
                .filter(room -> query.readinessStatus() == null
                        || room.readinessStatus() == query.readinessStatus())
                .filter(room -> query.bookable() == null || room.bookable() == query.bookable())
                .filter(room -> query.examinationCapable() == null
                        || room.examinationCapable() == query.examinationCapable())
                .toList();
        return Page.of(matches, matches.size(), query.page(), query.size());
    }

    @Override
    public List<Zone> findZones(String siteCode) {
        return zones.values().stream()
                .filter(zone -> siteCode == null || zone.siteCode().equals(upper(siteCode)))
                .toList();
    }

    @Override
    public List<DeviceReference> findDeviceReferences(String siteCode) {
        return devices.values().stream()
                .filter(device -> siteCode == null || device.siteCode().equals(upper(siteCode)))
                .toList();
    }

    @Override
    public List<DeviceReference> findDeviceReferences(String siteCode, DeviceReferenceType type, UUID roomId) {
        return findDeviceReferences(siteCode).stream()
                .filter(device -> type == null || device.type() == type)
                .filter(device -> roomId == null || roomId.equals(device.roomId()))
                .toList();
    }

    @Override
    public Page<FacilityAsset> searchAssets(AssetQuery query) {
        List<FacilityAsset> matches = assets.values().stream()
                .filter(asset -> query.siteCode() == null || asset.siteCode().equals(upper(query.siteCode())))
                .filter(asset -> query.roomId() == null || query.roomId().equals(asset.roomId()))
                .filter(asset -> query.category() == null || asset.category() == query.category())
                .filter(asset -> query.criticality() == null || asset.criticality() == query.criticality())
                .filter(asset -> query.operationalStatus() == null
                        || asset.operationalStatus() == query.operationalStatus())
                .toList();
        return Page.of(matches, matches.size(), query.page(), query.size());
    }

    @Override
    public List<ZoneMembership> findZoneMembers(UUID zoneId) {
        return memberships.stream().filter(member -> member.zoneId().equals(zoneId)).toList();
    }

    @Override
    public List<ZoneMembership> findZonesContaining(ZoneMemberType memberType, UUID memberId) {
        return memberships.stream()
                .filter(member -> member.memberType() == memberType && member.memberId().equals(memberId))
                .toList();
    }

    @Override
    public List<FacilityAsset> findImpairingAssets(UUID roomId) {
        return assets.values().stream()
                .filter(asset -> roomId != null && roomId.equals(asset.roomId()))
                .filter(FacilityAsset::impairsReadiness)
                .toList();
    }

    @Override
    public List<FacilityRoom> findStaleReadiness(String siteCode, Instant threshold) {
        return rooms.values().stream()
                .filter(room -> siteCode == null || room.siteCode().equals(upper(siteCode)))
                .filter(room -> room.lifecycleStatus().isOperational())
                .filter(room -> room.readinessUpdatedAt() == null
                        || room.readinessUpdatedAt().isBefore(threshold))
                .toList();
    }

    @Override
    public List<FacilityRoom> findActiveRooms(String siteCode) {
        return findRooms(siteCode).stream().filter(room -> room.lifecycleStatus().isOperational()).toList();
    }

    @Override
    public List<FacilityAsset> findActiveAssets(String siteCode) {
        return assets.values().stream()
                .filter(asset -> siteCode == null || asset.siteCode().equals(upper(siteCode)))
                .filter(asset -> asset.lifecycleStatus().isOperational())
                .toList();
    }

    private static String upper(String value) {
        return value == null ? null : value.strip().toUpperCase(Locale.ROOT);
    }
}
