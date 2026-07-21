package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditChainVerification;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Audit use cases that are not tied to a single aggregate: recording authorisation denials, searching
 * the log and replaying the hash chain.
 */
@Service
public class FleetAuditService {

    private static final Logger log = LoggerFactory.getLogger(FleetAuditService.class);

    private final AuditPort auditPort;

    public FleetAuditService(AuditPort auditPort) {
        this.auditPort = auditPort;
    }

    /**
     * Records an authorisation denial.
     *
     * <p>Called after the denied request's transaction has rolled back. A failure to write the denial
     * must not replace the 403 the caller is about to receive, so it is logged at error level and
     * swallowed — losing the response would hide the denial from the client as well as from the log.
     */
    public void recordAuthorizationDenial(ActorContext actor, String siteScope, String resourceType,
            String resourceId, String requiredPermission, String reason) {
        try {
            auditPort.recordAuthorizationDenied(actor, siteScope, resourceType, resourceId, requiredPermission,
                    reason);
        } catch (RuntimeException exception) {
            log.error("Could not write the authorisation-denial audit record for actor {} on {}:{}",
                    actor.actorId(), resourceType, resourceId, exception);
        }
    }

    public List<AuditEvent> search(AuditPort.AuditQuery query) {
        return auditPort.search(query);
    }

    public AuditChainVerification verifyChain() {
        return auditPort.verifyChain();
    }
}
