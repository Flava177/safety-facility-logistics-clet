package gh.edu.clet.sfl.emergencynotification.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;

/** Append-only, tamper-evident audit log (SRS-SFL-S174-03). No update/delete by application roles. */
public interface AuditPort {

    void record(ActorContext actor, SourceChannel sourceChannel, String siteScope, String action, String resourceType,
            String resourceId, Object beforeValue, Object afterValue, String reason);

    /** Replays the chain and reports the first divergence, if any. */
    AuditVerification verifyChain();

    record AuditVerification(boolean intact, long recordsChecked, Long firstDivergentSequence, String reason) {
    }
}
