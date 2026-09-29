package gh.edu.clet.sfl.facilities.energy.infrastructure.integration;

import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.BuildingDeviceDirectory;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyDeviceDirectoryPort;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * S157's device-identity question, answered by S156's published contract (SRS-SFL-S157-04).
 *
 * <p>One of the two classes in the energy module allowed to name {@code buildingsystems} - and only its
 * {@code application.contract} package, never S156's internals (enforced by {@code EnergyArchitectureTest}).
 * In process, because S156 lives in this deployable: this is a real call to the real register, not a
 * recorded stand-in. Until S156 lands, the contract's scaffold answers "no such device", which is the true
 * answer while no device register exists.
 */
@Component
public class BuildingDeviceDirectoryAdapter implements EnergyDeviceDirectoryPort {

    private final BuildingDeviceDirectory directory;

    public BuildingDeviceDirectoryAdapter(BuildingDeviceDirectory directory) {
        this.directory = directory;
    }

    @Override
    public Optional<DeviceIdentity> findByAvampAssetId(String avampAssetId) {
        if (avampAssetId == null || avampAssetId.isBlank()) {
            return Optional.empty();
        }
        return directory.findByAvampAssetId(avampAssetId.strip()).map(device -> new DeviceIdentity(device.deviceId(),
                device.avampAssetId(), device.deviceCode(), device.siteCode(), device.status()));
    }
}
