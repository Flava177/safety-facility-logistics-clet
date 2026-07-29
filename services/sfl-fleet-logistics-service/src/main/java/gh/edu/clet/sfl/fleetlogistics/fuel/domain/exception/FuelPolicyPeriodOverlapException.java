package gh.edu.clet.sfl.fleetlogistics.fuel.domain.exception;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetDomainException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The {@code FuelPolicy} invariant the domain model documented and nothing enforced.
 *
 * <p>Reconciliation resolves the policy in force at a transaction's own {@code occurredAt}. With two
 * ACTIVE policies covering that instant, {@code findApplicablePolicy} returns whichever row the
 * ordering happens to surface — so the rules a transaction is judged against, and the policy version
 * stamped on its reconciliation record, stop being reproducible. That is the whole point of an
 * effective-dated policy, so the overlap is refused at creation rather than left to be discovered.
 *
 * <p>The conflicting policies travel in {@link #details()} so the dashboard can name them.
 */
public class FuelPolicyPeriodOverlapException extends FleetDomainException {

    public FuelPolicyPeriodOverlapException(Map<String, Object> details) {
        super(FleetErrorCode.FUEL_POLICY_PERIOD_OVERLAP, details);
    }

    public static FuelPolicyPeriodOverlapException of(String siteCode, Instant effectiveFrom,
            Instant effectiveTo, List<Conflict> conflicts) {
        return new FuelPolicyPeriodOverlapException(Map.of(
                "siteCode", siteCode,
                "effectiveFrom", effectiveFrom.toString(),
                "effectiveTo", effectiveTo == null ? "" : effectiveTo.toString(),
                "conflictingPolicies", conflicts.stream().map(Conflict::asMap).toList()));
    }

    /** One policy already covering part of the requested period. */
    public record Conflict(UUID id, String name, int policyVersion, Instant effectiveFrom, Instant effectiveTo) {

        Map<String, Object> asMap() {
            return Map.of(
                    "id", id.toString(),
                    "name", name,
                    "policyVersion", policyVersion,
                    "effectiveFrom", effectiveFrom.toString(),
                    "effectiveTo", effectiveTo == null ? "" : effectiveTo.toString());
        }
    }
}
