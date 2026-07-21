package gh.edu.clet.sfl.ifimp.facilities.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import gh.edu.clet.sfl.ifimp.facilities.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.ifimp.facilities.domain.Building;
import gh.edu.clet.sfl.ifimp.facilities.domain.DeviceReference;
import gh.edu.clet.sfl.ifimp.facilities.domain.FacilityFloor;
import gh.edu.clet.sfl.ifimp.facilities.domain.FacilityRoom;
import gh.edu.clet.sfl.ifimp.facilities.domain.Site;
import gh.edu.clet.sfl.ifimp.facilities.domain.Zone;
import org.springframework.stereotype.Repository;

@Repository
class JpaFacilitiesRepositoryAdapter implements FacilitiesRepository {

    private final JpaSiteRepository sites;
    private final JpaBuildingRepository buildings;
    private final JpaFacilityFloorRepository floors;
    private final JpaFacilityRoomRepository rooms;
    private final JpaZoneRepository zones;
    private final JpaDeviceReferenceRepository deviceReferences;

    JpaFacilitiesRepositoryAdapter(JpaSiteRepository sites, JpaBuildingRepository buildings,
            JpaFacilityFloorRepository floors, JpaFacilityRoomRepository rooms, JpaZoneRepository zones,
            JpaDeviceReferenceRepository deviceReferences) {
        this.sites = sites;
        this.buildings = buildings;
        this.floors = floors;
        this.rooms = rooms;
        this.zones = zones;
        this.deviceReferences = deviceReferences;
    }

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
    public List<Site> findSites() {
        return sites.findAllByOrderBySiteCodeAsc().stream().map(SiteRecord::toDomain).toList();
    }

    @Override
    public List<Building> findBuildings(String siteCode) {
        return siteCode == null || siteCode.isBlank()
                ? buildings.findAllByOrderBySiteCodeAscBuildingCodeAsc().stream().map(BuildingRecord::toDomain).toList()
                : buildings.findBySiteCodeOrderByBuildingCodeAsc(siteCode.strip().toUpperCase()).stream()
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
        return siteCode == null || siteCode.isBlank()
                ? rooms.findAllByOrderBySiteCodeAscRoomCodeAsc().stream().map(FacilityRoomRecord::toDomain).toList()
                : rooms.findBySiteCodeOrderByRoomCodeAsc(siteCode.strip().toUpperCase()).stream()
                        .map(FacilityRoomRecord::toDomain).toList();
    }

    @Override
    public List<Zone> findZones(String siteCode) {
        return siteCode == null || siteCode.isBlank()
                ? zones.findAllByOrderBySiteCodeAscZoneCodeAsc().stream().map(ZoneRecord::toDomain).toList()
                : zones.findBySiteCodeOrderByZoneCodeAsc(siteCode.strip().toUpperCase()).stream()
                        .map(ZoneRecord::toDomain).toList();
    }

    @Override
    public List<DeviceReference> findDeviceReferences(String siteCode) {
        return siteCode == null || siteCode.isBlank()
                ? deviceReferences.findAllByOrderBySiteCodeAscDeviceCodeAsc().stream()
                        .map(DeviceReferenceRecord::toDomain).toList()
                : deviceReferences.findBySiteCodeOrderByDeviceCodeAsc(siteCode.strip().toUpperCase()).stream()
                        .map(DeviceReferenceRecord::toDomain).toList();
    }
}
