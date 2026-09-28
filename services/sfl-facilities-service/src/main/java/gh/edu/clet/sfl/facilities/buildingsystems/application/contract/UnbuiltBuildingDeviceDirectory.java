package gh.edu.clet.sfl.facilities.buildingsystems.application.contract;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Build scaffolding: stands in for S156's device register until the S156 module lands, so modules built
 * against {@link BuildingDeviceDirectory} in parallel can start. The S156 build deletes this file and
 * provides the real implementation. It answers "no such device", which is the true answer while no S156
 * device register exists.
 */
@Component
class UnbuiltBuildingDeviceDirectory implements BuildingDeviceDirectory {

    @Override
    public Optional<BuildingDevice> findByAvampAssetId(String avampAssetId) {
        return Optional.empty();
    }
}
