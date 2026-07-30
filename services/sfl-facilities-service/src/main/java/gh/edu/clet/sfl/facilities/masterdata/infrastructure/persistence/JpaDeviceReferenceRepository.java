package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import gh.edu.clet.sfl.facilities.masterdata.domain.DeviceReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaDeviceReferenceRepository extends JpaRepository<DeviceReferenceRecord, UUID> {

    List<DeviceReferenceRecord> findAllByOrderBySiteCodeAscDeviceCodeAsc();

    List<DeviceReferenceRecord> findBySiteCodeOrderByDeviceCodeAsc(String siteCode);

    List<DeviceReferenceRecord> findBySiteCodeAndTypeOrderByDeviceCodeAsc(String siteCode,
            DeviceReferenceType type);

    List<DeviceReferenceRecord> findByRoomIdOrderByDeviceCodeAsc(UUID roomId);

    /** Backs the duplicate-identifier check: a device code is unique within its site. */
    Optional<DeviceReferenceRecord> findBySiteCodeAndDeviceCode(String siteCode, String deviceCode);
}
