package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One row per view/download/export of an {@link EvidenceItem} - SRS-SFL-S161-03: "every view,
 * download and export ... logged with actor and timestamp". This is a queryable read model for "who
 * accessed this item"; the tamper-evident guarantee itself comes from the platform's shared,
 * hash-chained {@code safety_security.audit_log} (see {@code AuditPort}), which every access here is
 * also recorded against - this table is not itself hash-chained, so it must never be the only record
 * of an access.
 */
public record EvidenceAccessLog(UUID id, UUID evidenceItemId, String actorId, EvidenceAccessAction action,
        Instant occurredAt) {

    public EvidenceAccessLog {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(evidenceItemId, "evidenceItemId is required");
        require(actorId, "actorId");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    public static EvidenceAccessLog record(UUID id, UUID evidenceItemId, String actorId, EvidenceAccessAction action,
            Instant occurredAt) {
        return new EvidenceAccessLog(id, evidenceItemId, actorId, action, occurredAt);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
