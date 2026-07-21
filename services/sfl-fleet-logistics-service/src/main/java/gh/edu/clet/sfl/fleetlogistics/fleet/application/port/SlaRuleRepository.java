package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.SlaPolicy;
import java.time.Instant;
import java.util.List;

/**
 * Reads the SLA rules in force.
 *
 * <p>SRS-SFL-S166-02 requires escalation to be "evaluated using the runtime configuration active at the
 * time of evaluation", so this is queried on every evaluation rather than cached at startup.
 */
public interface SlaRuleRepository {

    /** Every rule effective at {@code at}. */
    List<SlaPolicy.SlaRule> findEffectiveRules(Instant at);
}
