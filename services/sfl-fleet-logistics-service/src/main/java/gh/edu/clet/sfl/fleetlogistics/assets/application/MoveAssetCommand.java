package gh.edu.clet.sfl.fleetlogistics.assets.application;

import java.util.UUID;

import gh.edu.clet.sfl.fleetlogistics.assets.domain.LocationType;

public record MoveAssetCommand(UUID assetId, LocationType locationType, String locationReference, String actor,
        String correlationId) {
}