package gh.edu.clet.sfl.fleetlogistics.fuel.domain.exception;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetDomainException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import java.util.Map;
import java.util.UUID;

/**
 * A revision that changed how a policy judges, while reusing the version that says how it judged.
 *
 * <h2>The invariant</h2>
 *
 * <p>{@code (policyId, policyVersion)} must identify exactly one rule set, for as long as any
 * reconciliation cites it. A run stores that pair and nothing else about the rules - it does not
 * copy the limits it applied - so the pair is the whole account of why a transaction was judged the
 * way it was. Let a limit change under the same version and the account stops being one: "policy 7
 * version 1" means the old ceiling for the runs before the edit and the new one for the runs after,
 * and no record distinguishes them.
 *
 * <p>This is refused rather than corrected automatically. The version is the operator's own
 * numbering - it appears on their paperwork and in their reports - so the service is not entitled to
 * increment it behind them. It says what is wrong and lets them choose the number.
 *
 * <p>Only the fields that decide an outcome count; see {@link
 * gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPolicy#hasSameRulesAs}. Renaming a policy, or
 * correcting a date, needs no new version.
 */
public class FuelPolicyVersionNotAdvancedException extends FleetDomainException {

    public FuelPolicyVersionNotAdvancedException(Map<String, Object> details) {
        super(FleetErrorCode.FUEL_POLICY_VERSION_NOT_ADVANCED, details);
    }

    public static FuelPolicyVersionNotAdvancedException of(UUID policyId, int currentVersion) {
        return new FuelPolicyVersionNotAdvancedException(Map.of(
                "policyId", policyId.toString(),
                "policyVersion", currentVersion,
                // Named so the dashboard can offer it rather than making the operator work it out.
                "nextPolicyVersion", currentVersion + 1));
    }
}
