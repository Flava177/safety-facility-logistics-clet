package gh.edu.clet.sfl.facilities.shared.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditChainVerification;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditEvent;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The append-only audit trail required by SRS-SFL-S152-03.
 *
 * <p>Writes happen inside the caller's transaction: "the system writes the operational record and
 * audit entry in the same unit of work where possible". A change that commits without its audit
 * record, or an audit record that survives a rolled-back change, are both failures the requirement
 * exists to prevent.
 */
public interface AuditPort {

    /** Appends a sealed record to the chain and returns it. */
    AuditEvent record(ActorContext actor, SourceChannel channel, AuditAction action, String resourceType,
            String resourceId, String siteScope, Object before, Object after);

    /**
     * Appends a record for a refused command or query.
     *
     * <p>Separate from {@link #record} because a denial has no before/after state and because
     * forgetting to audit one is the failure mode that matters: an unaudited denial is an attempt
     * nobody can see.
     */
    AuditEvent recordDenial(ActorContext actor, SourceChannel channel, String resourceType, String resourceId,
            String siteScope, String reason);

    /** Records ordered by sequence, filtered by whatever the caller supplied. */
    List<AuditEvent> search(String siteScope, String resourceType, String resourceId, String actorId,
            AuditAction action, Instant from, Instant to, int limit);

    /** Replays the whole chain and reports whether it is intact. */
    AuditChainVerification verifyChain();

    /** Replays the chain for one resource. */
    List<AuditEvent> historyFor(String resourceType, UUID resourceId);
}
