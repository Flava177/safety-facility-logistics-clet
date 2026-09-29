package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves a device's location against the S152 register - SRS-SFL-S156-01 ("resolved against the S152
 * facilities register location reference") and S156-04 ("device-to-location mapping is resolved through
 * S152").
 *
 * <p>Asked twice: at registration, so a device cannot be mapped to a building that does not exist, and on
 * every reading, because the register moves on. A room archived after its sensor was mapped is exactly
 * the Unresolvable Location error state, and the answer then is to quarantine the reading, not to file it
 * against a room nobody can find.
 *
 * <p>A location resolves when the building is operational at the device's site and, where a room is
 * mapped, the room is operational, at the same site, and on a floor of that building.
 */
@Component
public class S152LocationResolver {

    private final FacilitiesRepository facilities;

    public S152LocationResolver(FacilitiesRepository facilities) {
        this.facilities = facilities;
    }

    /** The resolved S152 location. {@code room} fields are null for a building-level device. */
    public record ResolvedLocation(String buildingCode, UUID roomId, String roomCode) {

        public String locationCode() {
            return roomCode != null ? roomCode : buildingCode;
        }
    }

    public Optional<ResolvedLocation> resolve(String siteCode, String buildingCode, UUID roomId) {
        Optional<Building> building = facilities.findBuildingByCode(siteCode, buildingCode)
                .filter(found -> found.lifecycleStatus().isOperational());
        if (building.isEmpty()) {
            return Optional.empty();
        }
        if (roomId == null) {
            return Optional.of(new ResolvedLocation(building.get().buildingCode(), null, null));
        }
        Optional<FacilityRoom> room = facilities.findRoom(roomId)
                .filter(found -> found.lifecycleStatus().isOperational())
                .filter(found -> found.siteCode().equalsIgnoreCase(siteCode));
        if (room.isEmpty()) {
            return Optional.empty();
        }
        boolean inBuilding = facilities.findFloor(room.get().floorId())
                .map(FacilityFloor::buildingId)
                .filter(building.get().id()::equals)
                .isPresent();
        return inBuilding
                ? Optional.of(new ResolvedLocation(building.get().buildingCode(), room.get().id(), room.get().roomCode()))
                : Optional.empty();
    }
}
