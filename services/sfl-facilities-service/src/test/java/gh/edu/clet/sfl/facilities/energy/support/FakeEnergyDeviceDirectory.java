package gh.edu.clet.sfl.facilities.energy.support;

import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyDeviceDirectoryPort;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A double for S156's device register, so an S157 test can say "this AVAMP asset is already an S156 device". */
public final class FakeEnergyDeviceDirectory implements EnergyDeviceDirectoryPort {

    private final Map<String, DeviceIdentity> devices = new LinkedHashMap<>();

    public FakeEnergyDeviceDirectory enrol(String avampAssetId, String deviceCode, String siteCode) {
        devices.put(avampAssetId, new DeviceIdentity(UUID.randomUUID(), avampAssetId, deviceCode, siteCode, "ACTIVE"));
        return this;
    }

    @Override
    public Optional<DeviceIdentity> findByAvampAssetId(String avampAssetId) {
        return Optional.ofNullable(devices.get(avampAssetId));
    }
}
