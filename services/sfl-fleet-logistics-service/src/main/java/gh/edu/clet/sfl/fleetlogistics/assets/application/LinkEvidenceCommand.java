package gh.edu.clet.sfl.fleetlogistics.assets.application;

import java.util.UUID;

public record LinkEvidenceCommand(UUID assetId, String evidenceReference, String actor, String correlationId) {
}