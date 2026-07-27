package gh.edu.clet.sfl.emergencynotification.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.emergencynotification.domain.model.RetentionClass;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;
import java.util.UUID;

/**
 * Governed-evidence boundary: dispatch/receipt/scan/custody documents are registered as governed evidence
 * references (hash, uploader, retention class, related activation) — never ungoverned binaries in a column.
 */
public interface EvidencePort {

    UUID register(EvidenceRegistration registration);

    record EvidenceRegistration(String siteCode, UUID activationId, String evidenceType, String fileName,
            String contentType, String storageReference, String sha256Hash, RetentionClass retentionClass,
            ActorContext actor, SourceChannel sourceChannel) {
    }
}
