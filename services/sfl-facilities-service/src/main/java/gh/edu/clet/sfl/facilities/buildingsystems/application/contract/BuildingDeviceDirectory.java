package gh.edu.clet.sfl.facilities.buildingsystems.application.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * S156's device register, as other modules may ask about it.
 *
 * <p>S157 asks before registering a meter: if the physical device is already an S156 IoT device, S157
 * must consume the stream rather than register a second identity for it (S157-04 validation).
 */
public interface BuildingDeviceDirectory {

    Optional<BuildingDevice> findByAvampAssetId(String avampAssetId);

    record BuildingDevice(UUID deviceId, String avampAssetId, String deviceCode, String siteCode, String status) {
    }
}
