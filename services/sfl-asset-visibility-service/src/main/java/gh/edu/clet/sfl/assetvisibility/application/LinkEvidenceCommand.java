package gh.edu.clet.sfl.assetvisibility.application;

import java.util.UUID;

public record LinkEvidenceCommand(UUID assetId, String evidenceReference, String actor, String correlationId) {
}