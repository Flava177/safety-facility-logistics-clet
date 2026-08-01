package gh.edu.clet.sfl.assetvisibility.infrastructure.persistence;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import gh.edu.clet.sfl.assetvisibility.domain.LocationType;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaAssetReferenceRepository extends JpaRepository<AssetReferenceRecord, UUID> {

    Optional<AssetReferenceRecord> findByAssetCode(String assetCode);

    List<AssetReferenceRecord> findBySiteCodeOrderByAssetCodeAsc(String siteCode);

    List<AssetReferenceRecord> findAllByOrderBySiteCodeAscAssetCodeAsc();

    List<AssetReferenceRecord> findBySiteCodeInOrderBySiteCodeAscAssetCodeAsc(Set<String> siteCodes);

    List<AssetReferenceRecord> findBySiteCodeAndLocationTypeAndLocationReferenceOrderByAssetCodeAsc(
            String siteCode, LocationType locationType, String locationReference);
}