package gh.edu.clet.sfl.facilities.eventlogistics.domain.policy;

import gh.edu.clet.sfl.common.hse.RiskAssessmentCurrency;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventDetails;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which events are higher-risk, and how a refusal names why - SRS-SFL-S173-03.
 *
 * <p>The user story names the three elevated-risk markers - "large attendance, external contractors,
 * temporary structures" - and the requirement adds "event categories configured as higher-risk". Any
 * one is enough. Each is runtime configuration (see {@code EventLogisticsConfiguration}), because what
 * counts as a large crowd for a seminar room is an HSE judgement and changes without a deploy.
 *
 * <p>This class decides <em>whether</em> an assessment is required. Whether a linked one is
 * <em>current</em> is not decided here and must not be: S173-03's validation rule is that the check
 * "reuses the same logic as S164-01, not a separate implementation", which is
 * {@link RiskAssessmentCurrency}. This class only turns that rule's reason into the sentence a
 * coordinator reads.
 */
public final class EventRiskPolicy {

    private EventRiskPolicy() {
    }

    /**
     * @param attendanceThreshold expected attendance at or above this is higher-risk; zero or less
     *        switches the marker off
     * @param higherRiskCategories S078 event categories that are higher-risk whatever their size
     */
    public record RiskCriteria(int attendanceThreshold, boolean externalContractorsAreHigherRisk,
            boolean temporaryStructuresAreHigherRisk, Set<String> higherRiskCategories) {

        public RiskCriteria {
            higherRiskCategories = higherRiskCategories == null ? Set.of()
                    : higherRiskCategories.stream().map(value -> value.strip().toUpperCase(Locale.ROOT))
                            .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        }
    }

    /** What made an event higher-risk. Carried on the refusal and the readiness view. */
    public enum Trigger {
        LARGE_ATTENDANCE,
        EXTERNAL_CONTRACTORS,
        TEMPORARY_STRUCTURES,
        HIGHER_RISK_CATEGORY
    }

    /** Every marker this event trips. Empty means routine: no assessment is required (proportionate). */
    public static Set<Trigger> triggers(EventDetails event, RiskCriteria criteria) {
        EnumSet<Trigger> found = EnumSet.noneOf(Trigger.class);
        if (criteria.attendanceThreshold() > 0 && event.expectedAttendance() >= criteria.attendanceThreshold()) {
            found.add(Trigger.LARGE_ATTENDANCE);
        }
        if (criteria.externalContractorsAreHigherRisk() && event.externalContractors()) {
            found.add(Trigger.EXTERNAL_CONTRACTORS);
        }
        if (criteria.temporaryStructuresAreHigherRisk() && event.temporaryStructures()) {
            found.add(Trigger.TEMPORARY_STRUCTURES);
        }
        if (criteria.higherRiskCategories().contains(event.eventCategory())) {
            found.add(Trigger.HIGHER_RISK_CATEGORY);
        }
        return found;
    }

    public static boolean isHigherRisk(EventDetails event, RiskCriteria criteria) {
        return !triggers(event, criteria).isEmpty();
    }

    /**
     * The refusal sentence, naming the reason (S164-01's acceptance criterion: "refused with a named
     * reason", which S173-03 inherits by reusing its logic).
     *
     * @param linkedButUnknown an assessment is linked but S173 holds no S165 record of it - which, until
     *        S165 publishes, is every linked assessment
     */
    public static String explain(RiskAssessmentCurrency.Reason reason, String assessmentId, boolean linkedButUnknown,
            Set<Trigger> triggers) {
        String why = switch (reason) {
            case NONE_LINKED -> linkedButUnknown
                    ? "The linked assessment " + assessmentId + " is not known to S173: no S165 record of it has"
                            + " been received (S165 publishes none yet)."
                    : "No risk assessment is linked.";
            case SUPERSEDED -> "The linked assessment " + assessmentId
                    + " has been superseded; link its current version.";
            case NOT_PUBLISHED -> "The linked assessment " + assessmentId + " is not published.";
            case REVIEW_LAPSED -> "The linked assessment " + assessmentId
                    + " is past its review date and has lapsed.";
            case NOT_INDEPENDENTLY_SIGNED_OFF -> "The linked assessment " + assessmentId
                    + " has not been signed off by a reviewer independent of its author.";
        };
        return "This event is higher-risk (" + triggers.stream().map(Enum::name).sorted()
                .collect(Collectors.joining(", ")) + ") and needs a current S165 risk assessment. " + why;
    }
}
