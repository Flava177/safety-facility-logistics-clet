package gh.edu.clet.sfl.facilities.masterdata.application.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;

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
