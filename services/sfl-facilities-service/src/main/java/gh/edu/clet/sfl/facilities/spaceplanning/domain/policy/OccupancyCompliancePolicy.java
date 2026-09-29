package gh.edu.clet.sfl.facilities.spaceplanning.domain.policy;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ComplianceStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyStandard;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.RoomCompliance;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Checks a space's allocated headcount against its occupancy standard - SRS-SFL-S158-02.
 *
 * <p>Pure arithmetic over values S152 already holds, so the rule can be tested without a register.
 *
 * <h2>The order of the verdicts, which is the design</h2>
 *
 * <ol>
 *   <li><strong>No standard for the space type</strong> - {@code NOT_EVALUATED} with
 *       {@code SPACE_STANDARD_NOT_DEFINED}. The SRS names this error state and says what it must look
 *       like: "not-evaluated, not ... compliant by default".</li>
 *   <li><strong>A standard, but nothing to check it against</strong> - the room has no recorded
 *       capacity and the standard is written in capacity, or no recorded area and it is written in area -
 *       also {@code NOT_EVALUATED}, with {@code ROOM_MEASURES_UNKNOWN}. The same principle one level down:
 *       an unmeasured room is unchecked, not fine.</li>
 *   <li><strong>Any evaluable criterion fails</strong> - {@code NON_COMPLIANT}.</li>
 *   <li>Otherwise {@code COMPLIANT}. A criterion that could not be evaluated is named in the findings
 *       even then, so "compliant on capacity, area unknown" is visible rather than rounded up.</li>
 * </ol>
 */
public final class OccupancyCompliancePolicy {

    public static final String STANDARD_NOT_DEFINED = "SPACE_STANDARD_NOT_DEFINED";
    public static final String MEASURES_UNKNOWN = "ROOM_MEASURES_UNKNOWN";
    public static final String OVER_CAPACITY_SHARE = "OVER_CAPACITY_SHARE";
    public static final String UNDER_AREA_PER_PERSON = "UNDER_AREA_PER_PERSON";
    public static final String WITHIN_STANDARD = "WITHIN_STANDARD";

    private OccupancyCompliancePolicy() {
    }

    /**
     * @param standard the active standard for the space's type at its site, or {@code null} when none is
     *        configured
     */
    public static RoomCompliance evaluate(UUID roomId, String roomCode, SpaceType spaceType, Integer capacity,
            BigDecimal areaSqm, int totalHeadcount, OccupancyStandard standard) {
        if (standard == null) {
            return new RoomCompliance(roomId, roomCode, spaceType, totalHeadcount, capacity, areaSqm,
                    ComplianceStatus.NOT_EVALUATED, STANDARD_NOT_DEFINED,
                    List.of("No occupancy standard is configured for " + spaceType
                            + "; compliance is not evaluated."),
                    null, null, false);
        }
        List<String> findings = new ArrayList<>();
        String failure = null;
        boolean evaluatedAny = false;

        if (standard.maxCapacityPercent() != null) {
            if (capacity == null || capacity <= 0) {
                findings.add("Capacity share not checked: S152 records no capacity for " + roomCode + ".");
            } else {
                evaluatedAny = true;
                int limit = capacity * standard.maxCapacityPercent() / 100;
                if (totalHeadcount > limit) {
                    failure = OVER_CAPACITY_SHARE;
                    findings.add(totalHeadcount + " allocated against a limit of " + limit + " ("
                            + standard.maxCapacityPercent() + "% of capacity " + capacity + ").");
                } else {
                    findings.add(totalHeadcount + " allocated within the limit of " + limit + ".");
                }
            }
        }
        if (standard.minAreaPerPersonSqm() != null) {
            if (areaSqm == null || areaSqm.signum() <= 0) {
                findings.add("Area per person not checked: S152 records no area for " + roomCode + ".");
            } else {
                evaluatedAny = true;
                if (totalHeadcount > 0) {
                    BigDecimal perPerson = areaSqm.divide(BigDecimal.valueOf(totalHeadcount), 2, RoundingMode.HALF_UP);
                    if (perPerson.compareTo(standard.minAreaPerPersonSqm()) < 0) {
                        failure = failure == null ? UNDER_AREA_PER_PERSON : failure;
                        findings.add(perPerson.toPlainString() + " sqm per person against a minimum of "
                                + standard.minAreaPerPersonSqm().toPlainString() + ".");
                    } else {
                        findings.add(perPerson.toPlainString() + " sqm per person meets the minimum of "
                                + standard.minAreaPerPersonSqm().toPlainString() + ".");
                    }
                } else {
                    findings.add("Nobody allocated; area per person is met.");
                }
            }
        }

        ComplianceStatus status;
        String reason;
        if (!evaluatedAny) {
            status = ComplianceStatus.NOT_EVALUATED;
            reason = MEASURES_UNKNOWN;
        } else if (failure != null) {
            status = ComplianceStatus.NON_COMPLIANT;
            reason = failure;
        } else {
            status = ComplianceStatus.COMPLIANT;
            reason = WITHIN_STANDARD;
        }
        return new RoomCompliance(roomId, roomCode, spaceType, totalHeadcount, capacity, areaSqm, status, reason,
                findings, standard.id(), standard.versionNumber(), false);
    }
}
