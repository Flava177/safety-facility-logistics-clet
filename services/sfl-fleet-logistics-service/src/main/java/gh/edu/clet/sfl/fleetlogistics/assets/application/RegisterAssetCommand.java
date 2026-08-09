package gh.edu.clet.sfl.fleetlogistics.assets.application;

import gh.edu.clet.sfl.fleetlogistics.assets.domain.AssetCategory;
import gh.edu.clet.sfl.fleetlogistics.assets.domain.LocationType;

public record RegisterAssetCommand(String assetCode, String name, AssetCategory category, String siteCode,
        LocationType locationType, String locationReference, String custodianReference, String externalReference,
        String actor, String correlationId) {
}