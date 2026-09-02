package gh.edu.clet.sfl.safetysecurity.visitor.domain.model;

import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A host's decision on a visit request - SRS-SFL-S160-01, "host approval".
 *
 * <p>A record rather than a status, mirroring {@code BookingApproval} in the facilities module: the
 * interesting content is who decided and why, and a visit that was approved and one that never
 * needed approving are different facts a single {@code CONFIRMED} status cannot tell apart.
 *
 * <p>A rejection requires a reason; an approval does not.
 */
public record VisitorApproval(
        UUID id,
        UUID visitId,
        String siteCode,
        VisitorApprovalDecision decision,
        String reason,
        String decidedBy,
        Instant decidedAt) {

    public VisitorApproval {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(visitId, "visitId is required");
        siteCode = normalizeSite(siteCode);
        Objects.requireNonNull(decision, "decision is required");
        reason = blankToNull(reason);
        require(decidedBy, "decidedBy");
        decidedBy = decidedBy.strip();
        Objects.requireNonNull(decidedAt, "decidedAt is required");
        if (decision == VisitorApprovalDecision.REJECTED && reason == null) {
            throw new VisitorException(VisitorErrorCode.VISITOR_VALIDATION_FAILED,
                    java.util.Map.of("field", "reason", "message", "A rejected visit must say why."));
        }
    }

    public static VisitorApproval decide(UUID id, VisitorVisit visit, VisitorApprovalDecision decision,
            String reason, String actorId, Instant at) {
        return new VisitorApproval(id, visit.id(), visit.siteCode(), decision, reason, actorId, at);
    }

    private static String normalizeSite(String siteCode) {
        require(siteCode, "siteCode");
        return siteCode.strip().toUpperCase(Locale.ROOT);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
