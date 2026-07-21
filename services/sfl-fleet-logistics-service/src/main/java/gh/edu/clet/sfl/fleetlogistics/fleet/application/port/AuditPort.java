package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.List;

/**
 * Outbound port for the append-only, hash-chained audit log (SRS-SFL-S166-03).
 *
 * <p>There is deliberately no update or delete operation: "Audit records cannot be modified or deleted
 * by normal application roles."
 */
public interface AuditPort {

    /**
     * Appends an audit record inside the caller's transaction, so the state change and its audit entry
     * commit or roll back together.
     *
     * @param sourceChannel how the change reached the system (SRS-SFL-S166-03 requires source channel)
     * @param beforeValue the record before the change, or {@code null} on creation; canonicalised by the adapter
     * @param afterValue the record after the change, or {@code null} where there is no post-image
     */
    AuditEvent record(ActorContext actor, SourceChannel sourceChannel, SiteCode siteScope, AuditAction action,
            String resourceType, String resourceId, Object beforeValue, Object afterValue);

    /**
     * Appends an authorisation-denial record in its own transaction.
     *
     * <p>Denials happen before any state change, so the caller's transaction rolls back; recording the
     * denial independently is what makes "denials are audited" actually hold.
     */
    void recordAuthorizationDenied(ActorContext actor, String siteScope, String resourceType, String resourceId,
            String requiredPermission, String reason);

    /** Ordered audit search, already restricted to the sites the caller may see. */
    List<AuditEvent> search(AuditQuery query);

    /** Replays the chain and reports the first divergence, if any. */
    AuditChainVerification verifyChain();

    /** Audit search criteria. All fields are optional filters except the site restriction. */
    record AuditQuery(
            List<String> siteScopes,
            String resourceType,
            String resourceId,
            String actorId,
            AuditAction action,
            Instant from,
            Instant to,
            int page,
            int size) {
    }
}
