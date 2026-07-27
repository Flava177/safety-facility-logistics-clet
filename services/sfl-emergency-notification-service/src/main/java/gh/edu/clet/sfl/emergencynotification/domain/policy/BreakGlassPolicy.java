package gh.edu.clet.sfl.emergencynotification.domain.policy;

/**
 * Break-glass eligibility (Arch §0E): a declared-emergency activation may send without per-message
 * approval only when the chosen template (and, where linked, scenario) is break-glass-eligible. Break-glass
 * must never be gated by routine approval; the authorization check (role) is enforced separately by the
 * access policy.
 */
public final class BreakGlassPolicy {

    private BreakGlassPolicy() {
    }

    public static boolean eligible(boolean templateEligible, boolean scenarioEligible) {
        return templateEligible || scenarioEligible;
    }

    public static void requireEligible(boolean templateEligible, boolean scenarioEligible) {
        if (!eligible(templateEligible, scenarioEligible)) {
            throw new IllegalStateException(
                    "Break-glass requires a break-glass-eligible template or scenario");
        }
    }
}
