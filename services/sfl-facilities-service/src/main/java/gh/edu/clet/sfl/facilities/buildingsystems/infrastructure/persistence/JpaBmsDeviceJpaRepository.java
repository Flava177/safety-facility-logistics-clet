package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaBmsDeviceJpaRepository extends JpaRepository<BmsDeviceRecord, UUID> {

    Optional<BmsDeviceRecord> findByAvampAssetId(String avampAssetId);

    Optional<BmsDeviceRecord> findBySiteCodeAndDeviceCodeAndStatus(String siteCode, String deviceCode,
            DeviceStatus status);

    List<BmsDeviceRecord> findBySiteCodeAndStatus(String siteCode, DeviceStatus status);

    List<BmsDeviceRecord> findByStatus(DeviceStatus status);

    List<BmsDeviceRecord> findBySiteCode(String siteCode);

    List<BmsDeviceRecord> findAllByOrderBySiteCode();
}
