package gh.edu.clet.sfl.facilities.construction.domain.policy;

import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.ContractorCompetency;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Whether a contractor may be on site today - SRS-SFL-S176-02.
 *
 * <p>"Refused if insurance/competency has lapsed", and "an expired insurance or competency record
 * automatically suspends associated site-access grants rather than requiring a manual check." One rule
 * serves both: the request path asks it once, the sweep asks it on a timer, and they cannot disagree
 * because they are the same method.
 *
 * <h2>When a date has "passed"</h2>
 *
 * An expiry date is the last day the cover or certificate is in force. It lapses the day after -
 * {@code today.isAfter(expiresOn)}. Dates are evaluated in UTC, which is Ghana's civil time, so "the
 * expiry date passes" at midnight in Accra and the next sweep after that suspends.
 *
 * <p>Every lapse is named, not just the first: a site supervisor told only "insurance expired" renews
 * it, re-requests access and is refused again for the certificate nobody mentioned.
 */
public final class ContractorCompliancePolicy {

    private ContractorCompliancePolicy() {
    }

    /**
     * @param lapses one plain sentence per lapsed item, empty when compliant
     * @param expiringSoon items still in force but expiring within the warning window, for the dashboard
     * @param earliestExpiry the first date anything this contractor holds expires
     */
    public record Compliance(boolean compliant, List<String> lapses, List<String> expiringSoon,
            LocalDate earliestExpiry, LocalDate evaluatedOn) {

        public String reason() {
            return String.join(" ", lapses);
        }
    }

    public static Compliance evaluate(Contractor contractor, List<ContractorCompetency> competencies,
            LocalDate today, int warningDays) {
        List<String> lapses = new ArrayList<>();
        List<String> soon = new ArrayList<>();
        LocalDate warnUntil = today.plusDays(Math.max(0, warningDays));
        LocalDate earliest = contractor.insuranceExpiresOn();

        if (today.isAfter(contractor.insuranceExpiresOn())) {
            lapses.add("Insurance" + (contractor.insurancePolicyReference() == null ? ""
                    : " policy " + contractor.insurancePolicyReference())
                    + " expired on " + contractor.insuranceExpiresOn() + ".");
        } else if (!contractor.insuranceExpiresOn().isAfter(warnUntil)) {
            soon.add("Insurance expires on " + contractor.insuranceExpiresOn() + ".");
        }
        for (ContractorCompetency competency : competencies.stream()
                .sorted(Comparator.comparing(ContractorCompetency::certificationCode)).toList()) {
            if (competency.expiresOn().isBefore(earliest)) {
                earliest = competency.expiresOn();
            }
            if (today.isAfter(competency.expiresOn())) {
                lapses.add("Competency " + competency.certificationCode() + " expired on "
                        + competency.expiresOn() + ".");
            } else if (!competency.expiresOn().isAfter(warnUntil)) {
                soon.add("Competency " + competency.certificationCode() + " expires on "
                        + competency.expiresOn() + ".");
            }
        }
        return new Compliance(lapses.isEmpty(), List.copyOf(lapses), List.copyOf(soon), earliest, today);
    }
}
