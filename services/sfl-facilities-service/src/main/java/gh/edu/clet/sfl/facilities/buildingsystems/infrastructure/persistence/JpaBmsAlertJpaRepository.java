package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertStatus;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaBmsAlertJpaRepository extends JpaRepository<BmsAlertRecord, UUID> {

    List<BmsAlertRecord> findBySiteCodeAndStatus(String siteCode, AlertStatus status);

    List<BmsAlertRecord> findBySiteCode(String siteCode);

    List<BmsAlertRecord> findByStatus(AlertStatus status);

    List<BmsAlertRecord> findAllByOrderBySiteCode();

    Optional<BmsAlertRecord> findByDeviceIdAndTypeAndStatus(UUID deviceId, AlertType type, AlertStatus status);

    List<BmsAlertRecord> findByDeviceIdInAndStatus(Collection<UUID> deviceIds, AlertStatus status);

    List<BmsAlertRecord> findByDeviceIdAndWorkOrderIdIsNotNullOrderByRaisedAtDesc(UUID deviceId);
}
