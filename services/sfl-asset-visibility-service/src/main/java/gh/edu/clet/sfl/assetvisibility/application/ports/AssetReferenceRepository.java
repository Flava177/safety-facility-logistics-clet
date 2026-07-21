package gh.edu.clet.sfl.assetvisibility.application.ports;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import gh.edu.clet.sfl.assetvisibility.domain.AssetReference;
import gh.edu.clet.sfl.assetvisibility.domain.LocationType;

public interface AssetReferenceRepository {

    AssetReference save(AssetReference assetReference);

    Optional<AssetReference> findById(UUID id);

    Optional<AssetReference> findByAssetCode(String assetCode);

    List<AssetReference> findAll(String siteCode);

    List<AssetReference> findByLocation(String siteCode, LocationType locationType, String locationReference);
}