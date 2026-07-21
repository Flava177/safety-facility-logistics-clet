package gh.edu.clet.sfl.assetvisibility.application;

import gh.edu.clet.sfl.assetvisibility.domain.AssetCategory;
import gh.edu.clet.sfl.assetvisibility.domain.LocationType;

public record RegisterAssetCommand(String assetCode, String name, AssetCategory category, String siteCode,
        LocationType locationType, String locationReference, String custodianReference, String externalReference,
        String actor, String correlationId) {
}