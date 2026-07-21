package gh.edu.clet.sfl.ifimp.facilities.application.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import gh.edu.clet.sfl.ifimp.facilities.domain.Building;
import gh.edu.clet.sfl.ifimp.facilities.domain.DeviceReference;
import gh.edu.clet.sfl.ifimp.facilities.domain.FacilityFloor;
import gh.edu.clet.sfl.ifimp.facilities.domain.FacilityRoom;
import gh.edu.clet.sfl.ifimp.facilities.domain.Site;
import gh.edu.clet.sfl.ifimp.facilities.domain.Zone;

public interface FacilitiesRepository {
    Site saveSite(Site site);

    Building saveBuilding(Building building);

    FacilityFloor saveFloor(FacilityFloor floor);

    FacilityRoom saveRoom(FacilityRoom room);

    Zone saveZone(Zone zone);

    DeviceReference saveDeviceReference(DeviceReference deviceReference);

    Optional<Site> findSite(UUID id);

    Optional<Building> findBuilding(UUID id);

    Optional<FacilityFloor> findFloor(UUID id);

    Optional<FacilityRoom> findRoom(UUID id);

    List<Site> findSites();

    List<Building> findBuildings(String siteCode);

    List<FacilityFloor> findFloors(UUID buildingId);

    List<FacilityRoom> findRooms(String siteCode);

    List<Zone> findZones(String siteCode);

    List<DeviceReference> findDeviceReferences(String siteCode);
}
