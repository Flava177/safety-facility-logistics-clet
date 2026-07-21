package gh.edu.clet.sfl.assetvisibility.application;

import java.util.UUID;

import gh.edu.clet.sfl.assetvisibility.domain.LocationType;

public record MoveAssetCommand(UUID assetId, LocationType locationType, String locationReference, String actor,
        String correlationId) {
}