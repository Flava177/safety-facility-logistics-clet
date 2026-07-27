package gh.edu.clet.sfl.fleetlogistics.dispatch.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.UUID;

/**
 * Governed-evidence boundary for S171. Dispatch/receipt/scan/custody documents are registered as
 * governed evidence references (hash, uploader, retention class, related record, legal hold) on the
 * shared tamper-evident foundation — never as ungoverned binaries in a dispatch column.
 */
public interface DispatchEvidencePort {

    /** Register a governed evidence reference and return its id. Retention class is mandatory. */
    UUID register(EvidenceRegistration registration);

    /** Replay the tamper-evident audit hash chain and return the verification result. */
    AuditChainVerification verifyAuditChain();

    record EvidenceRegistration(SiteCode siteCode, String relatedRecordType, String relatedRecordId,
            String evidenceType, String fileName, String contentType, String storageReference, String sha256Hash,
            EvidenceRetentionClass retentionClass, Instant retentionExpiresAt, ActorContext actor,
            SourceChannel sourceChannel) {}
}
