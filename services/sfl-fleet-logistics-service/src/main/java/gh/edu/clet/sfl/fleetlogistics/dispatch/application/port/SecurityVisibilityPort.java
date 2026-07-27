package gh.edu.clet.sfl.fleetlogistics.dispatch.application.port;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;

/**
 * Outbound seam that surfaces security-relevant dispatch variances (broken seal / tamper) to SSEMP.
 * Implementations MUST NOT write to any security database directly; visibility is delivered through the
 * transactional outbox so it is atomic with the state change and observable/replayable.
 */
public interface SecurityVisibilityPort {

    /** Surface a security-relevant exception (seal/tamper variance, custody gap) to SSEMP/security. */
    void surfaceSecurityVariance(DispatchExceptionCase exceptionCase, ActorContext actor);
}
