package gh.edu.clet.sfl.fleetlogistics.assets.application;

import java.util.UUID;

public record AssignCustodyCommand(UUID assetId, String custodianReference, String actor, String correlationId) {
}