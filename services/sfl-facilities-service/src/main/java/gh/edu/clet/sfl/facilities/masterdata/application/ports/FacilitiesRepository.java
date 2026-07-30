package gh.edu.clet.sfl.facilities.masterdata.application.ports;

import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReference;
import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMembership;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The estate's outbound persistence port.
 *
 * <p>One port for the whole estate rather than seven, because the aggregates are read together far
 * more often than separately: creating a floor validates its building, the dashboard reads spaces and
 * assets in one pass, and a zone's membership spans four record types. Seven ports would mean seven
 * injections at every call site to express one transaction.
 *
 * <p>Pagination is expressed as page and size rather than as Spring's {@code Pageable}, which is an
 * infrastructure type the application layer must not import — the ArchUnit boundary test enforces it.
 */
public interface FacilitiesRepository {

    /** A page of results with the total available, for the {@code PageResponse} envelope. */
    record Page<T>(List<T> items, long totalElements, int page, int size) {
        public Page {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public static <T> Page<T> of(List<T> items, long total, int page, int size) {
            return new Page<>(items, total, page, size);
        }
    }

    /** Filters for the space search. Any null field means "no filter". */
    record RoomQuery(
            String siteCode,
            UUID buildingId,
            UUID floorId,
            SpaceType spaceType,
            LocationReadinessStatus readinessStatus,
            Boolean bookable,
            Boolean examinationCapable,
            int page,
            int size) {
    }

    /** Filters for the asset search. Any null field means "no filter". */
    record AssetQuery(
            String siteCode,
            UUID roomId,
            AssetCategory category,
            AssetCriticality criticality,
            AssetOperationalStatus operationalStatus,
            int page,
            int size) {
    }

    // ---- writes -------------------------------------------------------------------------------

    Site saveSite(Site site);

    Building saveBuilding(Building building);

    FacilityFloor saveFloor(FacilityFloor floor);

    FacilityRoom saveRoom(FacilityRoom room);

    Zone saveZone(Zone zone);

    DeviceReference saveDeviceReference(DeviceReference deviceReference);

    FacilityAsset saveAsset(FacilityAsset asset);

    ZoneMembership saveZoneMembership(ZoneMembership membership);

    void deleteZoneMembership(UUID zoneId, ZoneMemberType memberType, UUID memberId);

    // ---- lookups by id ------------------------------------------------------------------------

    Optional<Site> findSite(UUID id);

    Optional<Building> findBuilding(UUID id);

    Optional<FacilityFloor> findFloor(UUID id);

    Optional<FacilityRoom> findRoom(UUID id);

    Optional<Zone> findZone(UUID id);

    Optional<DeviceReference> findDeviceReference(UUID id);

    Optional<FacilityAsset> findAsset(UUID id);

    // ---- duplicate-identifier checks (SRS-SFL-S152-01) ----------------------------------------

    Optional<Site> findSiteByCode(String siteCode);

    Optional<Building> findBuildingByCode(String siteCode, String buildingCode);

    Optional<FacilityFloor> findFloorByCode(UUID buildingId, String floorCode);

    Optional<FacilityRoom> findRoomByCode(String siteCode, String roomCode);

    Optional<Zone> findZoneByCode(String siteCode, String zoneCode);

    Optional<DeviceReference> findDeviceReferenceByCode(String siteCode, String deviceCode);

    Optional<FacilityAsset> findAssetByCode(String siteCode, String assetCode);

    // ---- lists --------------------------------------------------------------------------------

    List<Site> findSites();

    List<Building> findBuildings(String siteCode);

    List<FacilityFloor> findFloors(UUID buildingId);

    List<FacilityRoom> findRooms(String siteCode);

    List<FacilityRoom> findRoomsByFloor(UUID floorId);

    Page<FacilityRoom> searchRooms(RoomQuery query);

    List<Zone> findZones(String siteCode);

    List<DeviceReference> findDeviceReferences(String siteCode);

    List<DeviceReference> findDeviceReferences(String siteCode, DeviceReferenceType type, UUID roomId);

    Page<FacilityAsset> searchAssets(AssetQuery query);

    List<ZoneMembership> findZoneMembers(UUID zoneId);

    List<ZoneMembership> findZonesContaining(ZoneMemberType memberType, UUID memberId);

    // ---- readiness and dashboard inputs -------------------------------------------------------

    /** Assets in a space that are degraded, under maintenance or out of service. */
    List<FacilityAsset> findImpairingAssets(UUID roomId);

    /** Active spaces whose readiness was last assessed before {@code threshold}, or never. */
    List<FacilityRoom> findStaleReadiness(String siteCode, Instant threshold);

    /** Every active space in scope, for the dashboard. */
    List<FacilityRoom> findActiveRooms(String siteCode);

    /** Every active asset in scope, for the dashboard. */
    List<FacilityAsset> findActiveAssets(String siteCode);
}
