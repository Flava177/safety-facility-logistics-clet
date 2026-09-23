package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CameraJpaRepository extends JpaRepository<CameraJpaEntity, UUID> {

    Optional<CameraJpaEntity> findBySiteCodeAndCameraId(String siteCode, String cameraId);

    List<CameraJpaEntity> findBySiteCode(String siteCode);
}
