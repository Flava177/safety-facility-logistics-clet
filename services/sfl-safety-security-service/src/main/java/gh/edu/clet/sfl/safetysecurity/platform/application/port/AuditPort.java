package gh.edu.clet.sfl.safetysecurity.platform.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;

/**
 * Append-only, tamper-evident audit log for the {@code safety_security} schema.
 *
 * <p>Shared across every module that lands in {@code safety_security} (S160 first, S160a-S163 as
 * they are built), unlike S174's {@code emergency_notification} audit table, which that module owns
 * outright and does not share. One chain, one {@code safety_security.audit_log} table, so a module
 * added later does not have to stand up its own copy of this - see {@code
 * infrastructure/persistence/AuditAdapter} for why this stayed JDBC rather than JPA despite the
 * schema's JPA default.
 */
public interface AuditPort {

    void record(ActorContext actor, String sourceChannel, String siteScope, String action, String resourceType,
            String resourceId, Object beforeValue, Object afterValue, String reason);

    /** Replays the chain and reports the first divergence, if any. */
    AuditVerification verifyChain();

    record AuditVerification(boolean intact, long recordsChecked, Long firstDivergentSequence, String reason) {
    }
}
