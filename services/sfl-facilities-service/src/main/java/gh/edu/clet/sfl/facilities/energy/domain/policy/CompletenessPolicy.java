package gh.edu.clet.sfl.facilities.energy.domain.policy;

import gh.edu.clet.sfl.facilities.energy.domain.CompletenessFlag;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * SRS-SFL-S157-03 validation: "A published metric always carries its computation period and a
 * data-completeness indicator, so a partial period is never presented as equivalent to a complete one."
 *
 * <h2>Expected and received, per meter, capped</h2>
 *
 * Expected readings for a meter are the period's length (from the later of period start and the meter's
 * registration, to the earlier of period end and its retirement) divided by the meter's expected interval,
 * with a floor of one: a monthly manual meter still owes one read to a daily KPI, and not having it is a
 * gap. The <em>whole</em> period counts even while it is still running, so a month-to-date KPI is always
 * shown as incomplete rather than extrapolated.
 *
 * <p>Received is capped per meter at that meter's expected count before summing. Without the cap, a
 * chatty meter sending twice as often as expected would cover for a meter that sent nothing, and a site
 * missing half its meters could report 100%.
 */
public final class CompletenessPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CompletenessPolicy() {
    }

    /** Readings this meter is expected to deliver in the period; zero when it did not exist during it. */
    public static int expected(EnergyMeter meter, EnergyPeriod period) {
        Instant from = later(period.startInstant(), meter.metadata().createdAt());
        Instant to = meter.retiredAt() == null ? period.endInstant() : earlier(period.endInstant(), meter.retiredAt());
        if (!to.isAfter(from)) {
            return 0;
        }
        long minutes = Duration.between(from, to).toMinutes();
        return (int) Math.max(1, minutes / meter.expectedIntervalMinutes());
    }

    /** One meter's contribution to "received", capped at what it owed. */
    public static int received(int expected, int received) {
        return Math.min(expected, Math.max(0, received));
    }

    public static BigDecimal percent(int expected, int received) {
        if (expected <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(Math.min(received, expected)).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(expected), 2, RoundingMode.DOWN);
    }

    /**
     * COMPLETE only at 100%, rounded down - 99.99% is PARTIAL. LOW below the configured minimum: that is
     * S157-03's Incomplete Period, which is published with the flag, never withheld.
     */
    public static CompletenessFlag flag(BigDecimal percent, BigDecimal minimumPct) {
        if (percent.compareTo(HUNDRED) >= 0) {
            return CompletenessFlag.COMPLETE;
        }
        return percent.compareTo(minimumPct) >= 0 ? CompletenessFlag.PARTIAL : CompletenessFlag.LOW;
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
