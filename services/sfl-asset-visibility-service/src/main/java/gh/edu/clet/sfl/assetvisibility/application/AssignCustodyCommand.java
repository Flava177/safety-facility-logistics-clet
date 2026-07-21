package gh.edu.clet.sfl.assetvisibility.application;

import java.util.UUID;

public record AssignCustodyCommand(UUID assetId, String custodianReference, String actor, String correlationId) {
}