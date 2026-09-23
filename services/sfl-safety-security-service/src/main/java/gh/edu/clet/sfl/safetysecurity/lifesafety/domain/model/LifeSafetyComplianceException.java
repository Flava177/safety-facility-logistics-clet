package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * SRS-SFL-S162a-03/05: a compliance gap - an overdue inspection, a panel fault, a coverage gap or a
 * failed functional test - raised against an inspection schedule or a detector, by reference only.
 */
public record LifeSafetyComplianceException(UUID id, String siteCode, ComplianceRefType refType, UUID refId,
        ComplianceExceptionKind kind, ComplianceExceptionStatus status, String note, Instant raisedAt,
        String resolvedBy, Instant resolvedAt, RecordMetadata metadata) {

    public LifeSafetyComplianceException resolve(String actorId, Instant now, RecordMetadata nextMeta) {
        return new LifeSafetyComplianceException(id, siteCode, refType, refId, kind,
                ComplianceExceptionStatus.RESOLVED, note, raisedAt, actorId, now, nextMeta);
    }
}
