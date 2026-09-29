package gh.edu.clet.sfl.facilities.energy.application.ports;

import java.util.Optional;
import java.util.UUID;

/**
 * S157's question to S156: "is this physical device already one of yours?" - SRS-SFL-S157-04.
 *
 * <p>S157's own port rather than S156's contract used directly, so the application layer depends on the
 * question it needs answered and not on another Phase 2 module's types, and the tests can answer it with
 * a double while S156 is being built elsewhere. The adapter delegates to S156's published
 * {@code BuildingDeviceDirectory}.
 */
public interface EnergyDeviceDirectoryPort {

    Optional<DeviceIdentity> findByAvampAssetId(String avampAssetId);

    record DeviceIdentity(UUID deviceId, String avampAssetId, String deviceCode, String siteCode, String status) {
    }
}
