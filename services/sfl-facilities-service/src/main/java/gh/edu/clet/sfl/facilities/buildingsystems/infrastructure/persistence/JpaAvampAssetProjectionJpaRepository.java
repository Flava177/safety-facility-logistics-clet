package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaAvampAssetProjectionJpaRepository extends JpaRepository<AvampAssetProjectionRecord, UUID> {

    Optional<AvampAssetProjectionRecord> findByAvampAssetId(String avampAssetId);
}
