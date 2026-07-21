package gh.edu.clet.sfl.fleetlogistics.fleet.api.response;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.BlockerSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlockerCode;
import java.util.Map;

/**
 * One readiness or eligibility blocker: the machine-readable code a client switches on, the
 * human-readable explanation an operator reads, and the context needed to drill into it.
 */
public record BlockerResponse(
        ReadinessBlockerCode code,
        String message,
        BlockerSeverity severity,
        Map<String, Object> context) {
}
