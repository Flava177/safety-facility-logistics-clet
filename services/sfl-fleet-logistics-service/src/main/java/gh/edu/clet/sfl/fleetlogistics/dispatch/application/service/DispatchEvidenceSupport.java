package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchEvidencePort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.UUID;

/** Builds and registers a governed evidence reference when receipt/custody/distribution supplies one. */
public final class DispatchEvidenceSupport {
    private DispatchEvidenceSupport() {}

    /** Signature/scan document metadata supplied with a receipt, handover or distribution acknowledgement. */
    public record EvidenceMeta(String fileName, String contentType, String storageReference, String sha256Hash,
            String retentionClass, Instant retentionExpiresAt) {}

    /** Register governed evidence when a storage reference is supplied; returns the evidence id or null. */
    static UUID registerIfPresent(DispatchEvidencePort port, SiteCode site, String relatedType, String relatedId,
            String evidenceType, EvidenceMeta meta, ActorContext actor, SourceChannel channel) {
        if (meta == null || meta.storageReference() == null || meta.storageReference().isBlank()) return null;
        return port.register(new DispatchEvidencePort.EvidenceRegistration(site, relatedType, relatedId, evidenceType,
                meta.fileName() == null || meta.fileName().isBlank() ? evidenceType + ".dat" : meta.fileName(),
                meta.contentType() == null || meta.contentType().isBlank() ? "application/octet-stream" : meta.contentType(),
                meta.storageReference(),
                meta.sha256Hash() == null || meta.sha256Hash().isBlank() ? "0".repeat(64) : meta.sha256Hash(),
                parseRetention(meta.retentionClass()), meta.retentionExpiresAt(), actor, channel));
    }

    static EvidenceRetentionClass parseRetention(String value) {
        return value == null || value.isBlank()
                ? EvidenceRetentionClass.COMPLIANCE_7_YEARS
                : EvidenceRetentionClass.valueOf(value.trim().toUpperCase());
    }
}
