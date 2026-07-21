package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaDeviceReferenceRepository extends JpaRepository<DeviceReferenceRecord, UUID> {
    List<DeviceReferenceRecord> findAllByOrderBySiteCodeAscDeviceCodeAsc();

    List<DeviceReferenceRecord> findBySiteCodeOrderByDeviceCodeAsc(String siteCode);
}
