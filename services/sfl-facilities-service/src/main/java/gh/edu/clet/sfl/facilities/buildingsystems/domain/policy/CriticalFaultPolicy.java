package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.CriticalFaultType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/**
 * Recognises the three critical faults - SRS-SFL-S156-03: "critical faults (total power loss, lift
 * entrapment signal, generator failed-start during an outage) escalate immediately to the fast lane shared
 * with S162a/S174 rather than waiting for routine review".
 *
 * <h2>Why this is not a rule</h2>
 *
 * <p>Threshold rules are configuration: an engineer writes them, and an override can disable them. These
 * three are not left to configuration because the failure a mis-set or disabled rule would cause here is a
 * person trapped in a lift with nobody told. They are recognised on every accepted reading, bypass debounce
 * entirely, and cannot be switched off from the rules screen. The only configurable part is which lift
 * codes mean entrapment, because that is vendor vocabulary rather than policy.
 *
 * <h2>The three signals</h2>
 *
 * <ul>
 *   <li><strong>Total power loss</strong> - a {@code POWER_STATE} of 0 from an {@link BuildingSystemType#ELECTRICAL}
 *       device. An electrical device's supply channel is taken to monitor the building supply; a lighting
 *       circuit reporting 0 is a {@code LIGHTING} device and is not a total loss. Flagged in the gap report:
 *       a site with several incomers will want "all incomers lost", which needs a supply topology S152
 *       does not hold.</li>
 *   <li><strong>Lift entrapment</strong> - a {@code LIFT_STATUS} whose normalised code is one of the
 *       configured entrapment codes (default {@code 3}).</li>
 *   <li><strong>Generator failed-start during an outage</strong> - a {@code GENERATOR_RUN_STATE} of -1
 *       while the same building has a recent total-power-loss signal. A failed start on a test run with
 *       the mains present is a maintenance fault, handled by ordinary rules; during an outage it means the
 *       building is dark with no backup, which is the case the SRS names.</li>
 * </ul>
 */
public final class CriticalFaultPolicy {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal FAILED_START = BigDecimal.valueOf(-1);

    private CriticalFaultPolicy() {
    }

    /**
     * @param outageInProgress whether the device's building currently has a total-power-loss signal within
     *        the configured outage window - the caller reads it from the building's channel states
     */
    public static Optional<CriticalFaultType> classify(BuildingSystemType systemType, MeasuredQuantity quantity,
            BigDecimal value, Set<Integer> liftEntrapmentCodes, boolean outageInProgress) {
        if (quantity == MeasuredQuantity.POWER_STATE && systemType == BuildingSystemType.ELECTRICAL
                && value.compareTo(ZERO) == 0) {
            return Optional.of(CriticalFaultType.TOTAL_POWER_LOSS);
        }
        if (quantity == MeasuredQuantity.LIFT_STATUS && isCode(value, liftEntrapmentCodes)) {
            return Optional.of(CriticalFaultType.LIFT_ENTRAPMENT);
        }
        if (quantity == MeasuredQuantity.GENERATOR_RUN_STATE && value.compareTo(FAILED_START) == 0
                && outageInProgress) {
            return Optional.of(CriticalFaultType.GENERATOR_FAILED_START_DURING_OUTAGE);
        }
        return Optional.empty();
    }

    /** Whether a building-supply reading says the power is off - the input to "during an outage". */
    public static boolean indicatesOutage(BuildingSystemType systemType, MeasuredQuantity quantity, BigDecimal value) {
        return quantity == MeasuredQuantity.POWER_STATE && systemType == BuildingSystemType.ELECTRICAL
                && value != null && value.compareTo(ZERO) == 0;
    }

    private static boolean isCode(BigDecimal value, Set<Integer> codes) {
        try {
            return codes != null && codes.contains(value.intValueExact());
        } catch (ArithmeticException notAnInteger) {
            return false;
        }
    }
}
