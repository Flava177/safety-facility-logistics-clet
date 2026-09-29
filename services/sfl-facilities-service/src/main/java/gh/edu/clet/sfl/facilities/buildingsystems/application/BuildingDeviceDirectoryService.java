package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.facilities.buildingsystems.application.contract.BuildingDeviceDirectory;
import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingSystemsRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S156's implementation of the {@link BuildingDeviceDirectory} contract - the question S157 asks before
 * registering a meter: "is this AVAMP asset already an S156 device?" (S157-04 validation).
 *
 * <p>Answers for retired devices too, with their status. A meter whose AVAMP asset was once an S156 device
 * is still the same physical identity, and S157 should see that history rather than a blank.
 *
 * <p>Unauthorised by design: an in-process contract between two modules of one deployable, consulted
 * inside a caller that has already authorised its own actor. It reveals an id, a code, a site and a
 * status - no telemetry.
 */
@Service
public class BuildingDeviceDirectoryService implements BuildingDeviceDirectory {

    private final BuildingSystemsRepository repository;

    public BuildingDeviceDirectoryService(BuildingSystemsRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BuildingDevice> findByAvampAssetId(String avampAssetId) {
        if (avampAssetId == null || avampAssetId.isBlank()) {
            return Optional.empty();
        }
        return repository.findDeviceByAvampAssetId(avampAssetId.strip())
                .map(device -> new BuildingDevice(device.id(), device.avampAssetId(), device.deviceCode(),
                        device.siteCode(), device.status().name()));
    }
}
