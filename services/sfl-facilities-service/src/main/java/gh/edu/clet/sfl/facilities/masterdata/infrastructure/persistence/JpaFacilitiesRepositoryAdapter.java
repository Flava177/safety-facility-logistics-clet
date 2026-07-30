package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the estate port. Translates codes, pages and nothing else. */
@Repository
class JpaFacilitiesRepositoryAdapter implements FacilitiesRepository {

    /** Guards against a caller asking for the whole estate in one page. */
    private static final int MAX_PAGE_SIZE = 200;

    private final JpaSiteRepository sites;
    private final JpaBuildingRepository buildings;
    private final JpaFacilityFloorRepository floors;
    private final JpaFacilityRoomRepository rooms;
    private final JpaZoneRepository zones;
    private final JpaDeviceReferenceRepository deviceReferences;
    private final JpaFacilityAssetRepository assets;
    private final JpaZoneMembershipRepository zoneMemberships;

    JpaFacilitiesRepositoryAdapter(JpaSiteRepository sites, JpaBuildingRepository buildings,
            JpaFacilityFloorRepository floors, JpaFacilityRoomRepository rooms, JpaZoneRepository zones,
            JpaDeviceReferenceRepository deviceReferences, JpaFacilityAssetRepository assets,
            JpaZoneMembershipRepository zoneMemberships) {
        this.sites = sites;
        this.buildings = buildings;
        this.floors = floors;
        this.rooms = rooms;
        this.zones = zones;
        this.deviceReferences = deviceReferences;
        this.assets = assets;
        this.zoneMemberships = zoneMemberships;
    }

    // ---- writes -------------------------------------------------------------------------------

    @Override
    public Site saveSite(Site site) {
        return sites.save(SiteRecord.from(site)).toDomain();
    }

    @Override
    public Building saveBuilding(Building building) {
        return buildings.save(BuildingRecord.from(building)).toDomain();
    }

    @Override
    public FacilityFloor saveFloor(FacilityFloor floor) {
        return floors.save(FacilityFloorRecord.from(floor)).toDomain();
    }

    @Override
    public FacilityRoom saveRoom(FacilityRoom room) {
        return rooms.save(FacilityRoomRecord.from(room)).toDomain();
    }

    @Override
    public Zone saveZone(Zone zone) {
        return zones.save(ZoneRecord.from(zone)).toDomain();
    }

    @Override
    public DeviceReference saveDeviceReference(DeviceReference deviceReference) {
        return deviceReferences.save(DeviceReferenceRecord.from(deviceReference)).toDomain();
    }

    @Override
    public FacilityAsset saveAsset(FacilityAsset asset) {
        return assets.save(FacilityAssetRecord.from(asset)).toDomain();
    }

    @Override
    public ZoneMembership saveZoneMembership(ZoneMembership membership) {
        return zoneMemberships.save(ZoneMembershipRecord.from(membership)).toDomain();
    }

    @Override
    @Transactional
    public void deleteZoneMembership(UUID zoneId, ZoneMemberType memberType, UUID memberId) {
        zoneMemberships.deleteByZoneIdAndMemberTypeAndMemberId(zoneId, memberType, memberId);
    }

    // ---- lookups by id ------------------------------------------------------------------------

    @Override
    public Optional<Site> findSite(UUID id) {
        return sites.findById(id).map(SiteRecord::toDomain);
    }

    @Override
    public Optional<Building> findBuilding(UUID id) {
        return buildings.findById(id).map(BuildingRecord::toDomain);
    }

    @Override
    public Optional<FacilityFloor> findFloor(UUID id) {
        return floors.findById(id).map(FacilityFloorRecord::toDomain);
    }

    @Override
    public Optional<FacilityRoom> findRoom(UUID id) {
        return rooms.findById(id).map(FacilityRoomRecord::toDomain);
    }

    @Override
    public Optional<Zone> findZone(UUID id) {
        return zones.findById(id).map(ZoneRecord::toDomain);
    }

    @Override
    public Optional<DeviceReference> findDeviceReference(UUID id) {
        return deviceReferences.findById(id).map(DeviceReferenceRecord::toDomain);
    }

    @Override
    public Optional<FacilityAsset> findAsset(UUID id) {
        return assets.findById(id).map(FacilityAssetRecord::toDomain);
    }

    // ---- duplicate-identifier checks ----------------------------------------------------------

    @Override
    public Optional<Site> findSiteByCode(String siteCode) {
        return sites.findBySiteCode(normalize(siteCode)).map(SiteRecord::toDomain);
    }

    @Override
    public Optional<Building> findBuildingByCode(String siteCode, String buildingCode) {
        return buildings.findBySiteCodeAndBuildingCode(normalize(siteCode), normalize(buildingCode))
                .map(BuildingRecord::toDomain);
    }

    @Override
    public Optional<FacilityFloor> findFloorByCode(UUID buildingId, String floorCode) {
        return floors.findByBuildingIdAndFloorCode(buildingId, normalize(floorCode))
                .map(FacilityFloorRecord::toDomain);
    }

    @Override
    public Optional<FacilityRoom> findRoomByCode(String siteCode, String roomCode) {
        return rooms.findBySiteCodeAndRoomCode(normalize(siteCode), normalize(roomCode))
                .map(FacilityRoomRecord::toDomain);
    }

    @Override
    public Optional<Zone> findZoneByCode(String siteCode, String zoneCode) {
        return zones.findBySiteCodeAndZoneCode(normalize(siteCode), normalize(zoneCode))
                .map(ZoneRecord::toDomain);
    }

    @Override
    public Optional<DeviceReference> findDeviceReferenceByCode(String siteCode, String deviceCode) {
        return deviceReferences.findBySiteCodeAndDeviceCode(normalize(siteCode), normalize(deviceCode))
                .map(DeviceReferenceRecord::toDomain);
    }

    @Override
    public Optional<FacilityAsset> findAssetByCode(String siteCode, String assetCode) {
        return assets.findBySiteCodeAndAssetCode(normalize(siteCode), normalize(assetCode))
                .map(FacilityAssetRecord::toDomain);
    }

    // ---- lists --------------------------------------------------------------------------------

    @Override
    public List<Site> findSites() {
        return sites.findAllByOrderBySiteCodeAsc().stream().map(SiteRecord::toDomain).toList();
    }

    @Override
    public List<Building> findBuildings(String siteCode) {
        return blank(siteCode)
                ? buildings.findAllByOrderBySiteCodeAscBuildingCodeAsc().stream()
                        .map(BuildingRecord::toDomain).toList()
                : buildings.findBySiteCodeOrderByBuildingCodeAsc(normalize(siteCode)).stream()
                        .map(BuildingRecord::toDomain).toList();
    }

    @Override
    public List<FacilityFloor> findFloors(UUID buildingId) {
        if (buildingId == null) {
            return List.of();
        }
        return floors.findByBuildingIdOrderByLevelNumberAscFloorCodeAsc(buildingId).stream()
                .map(FacilityFloorRecord::toDomain).toList();
    }

    @Override
    public List<FacilityRoom> findRooms(String siteCode) {
        return blank(siteCode)
                ? rooms.findAllByOrderBySiteCodeAscRoomCodeAsc().stream().map(FacilityRoomRecord::toDomain).toList()
                : rooms.findBySiteCodeOrderByRoomCodeAsc(normalize(siteCode)).stream()
                        .map(FacilityRoomRecord::toDomain).toList();
    }

    @Override
    public List<FacilityRoom> findRoomsByFloor(UUID floorId) {
        if (floorId == null) {
            return List.of();
        }
        return rooms.findByFloorIdOrderByRoomCodeAsc(floorId).stream().map(FacilityRoomRecord::toDomain).toList();
    }

    @Override
    public Page<FacilityRoom> searchRooms(RoomQuery query) {
        org.springframework.data.domain.Page<FacilityRoomRecord> page = rooms.search(
                blank(query.siteCode()) ? null : normalize(query.siteCode()),
                query.buildingId(), query.floorId(), query.spaceType(), query.readinessStatus(),
                query.bookable(), query.examinationCapable(), pageRequest(query.page(), query.size()));
        return Page.of(page.getContent().stream().map(FacilityRoomRecord::toDomain).toList(),
                page.getTotalElements(), query.page(), clampSize(query.size()));
    }

    @Override
    public List<Zone> findZones(String siteCode) {
        return blank(siteCode)
                ? zones.findAllByOrderBySiteCodeAscZoneCodeAsc().stream().map(ZoneRecord::toDomain).toList()
                : zones.findBySiteCodeOrderByZoneCodeAsc(normalize(siteCode)).stream()
                        .map(ZoneRecord::toDomain).toList();
    }

    @Override
    public List<DeviceReference> findDeviceReferences(String siteCode) {
        return blank(siteCode)
                ? deviceReferences.findAllByOrderBySiteCodeAscDeviceCodeAsc().stream()
                        .map(DeviceReferenceRecord::toDomain).toList()
                : deviceReferences.findBySiteCodeOrderByDeviceCodeAsc(normalize(siteCode)).stream()
                        .map(DeviceReferenceRecord::toDomain).toList();
    }

    @Override
    public List<DeviceReference> findDeviceReferences(String siteCode, DeviceReferenceType type, UUID roomId) {
        if (roomId != null) {
            return deviceReferences.findByRoomIdOrderByDeviceCodeAsc(roomId).stream()
                    .map(DeviceReferenceRecord::toDomain).toList();
        }
        if (type != null && !blank(siteCode)) {
            return deviceReferences.findBySiteCodeAndTypeOrderByDeviceCodeAsc(normalize(siteCode), type).stream()
                    .map(DeviceReferenceRecord::toDomain).toList();
        }
        List<DeviceReference> all = findDeviceReferences(siteCode);
        return type == null ? all : all.stream().filter(device -> device.type() == type).toList();
    }

    @Override
    public Page<FacilityAsset> searchAssets(AssetQuery query) {
        org.springframework.data.domain.Page<FacilityAssetRecord> page = assets.search(
                blank(query.siteCode()) ? null : normalize(query.siteCode()),
                query.roomId(), query.category(), query.criticality(), query.operationalStatus(),
                pageRequest(query.page(), query.size()));
        return Page.of(page.getContent().stream().map(FacilityAssetRecord::toDomain).toList(),
                page.getTotalElements(), query.page(), clampSize(query.size()));
    }

    @Override
    public List<ZoneMembership> findZoneMembers(UUID zoneId) {
        return zoneMemberships.findByZoneIdOrderByMemberTypeAscAddedAtAsc(zoneId).stream()
                .map(ZoneMembershipRecord::toDomain).toList();
    }

    @Override
    public List<ZoneMembership> findZonesContaining(ZoneMemberType memberType, UUID memberId) {
        return zoneMemberships.findByMemberTypeAndMemberId(memberType, memberId).stream()
                .map(ZoneMembershipRecord::toDomain).toList();
    }

    // ---- readiness and dashboard inputs -------------------------------------------------------

    @Override
    public List<FacilityAsset> findImpairingAssets(UUID roomId) {
        if (roomId == null) {
            return List.of();
        }
        return assets.findImpairingByRoom(roomId).stream().map(FacilityAssetRecord::toDomain).toList();
    }

    @Override
    public List<FacilityRoom> findStaleReadiness(String siteCode, Instant threshold) {
        return rooms.findStaleReadiness(blank(siteCode) ? null : normalize(siteCode), threshold).stream()
                .map(FacilityRoomRecord::toDomain).toList();
    }

    @Override
    public List<FacilityRoom> findActiveRooms(String siteCode) {
        return rooms.findActiveForDashboard(blank(siteCode) ? null : normalize(siteCode)).stream()
                .map(FacilityRoomRecord::toDomain).toList();
    }

    @Override
    public List<FacilityAsset> findActiveAssets(String siteCode) {
        return assets.findActiveForDashboard(blank(siteCode) ? null : normalize(siteCode)).stream()
                .map(FacilityAssetRecord::toDomain).toList();
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), clampSize(size));
    }

    private static int clampSize(int size) {
        return size <= 0 ? 50 : Math.min(size, MAX_PAGE_SIZE);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
