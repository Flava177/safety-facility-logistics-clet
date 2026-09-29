package gh.edu.clet.sfl.facilities.energy.domain.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * SRS-SFL-S157-01 validation: "A manual reading must fall within a configurable plausibility band of
 * the trailing average or it is flagged for verification before acceptance."
 *
 * <h2>What is compared with what</h2>
 *
 * Not the register value - a register only ever grows, so any band around its average would hold every
 * honest read. The comparison is the <em>implied daily consumption</em> (the delta divided by the days
 * since the previous posted read) against the meter's trailing daily average. That is what a person
 * checking a meter walk actually does: "this says we used three times what we normally use a day".
 *
 * <h2>What the band cannot judge</h2>
 *
 * <ul>
 *   <li>A register lower than the previous read is never plausible. It is a typo, a rolled-over dial or a
 *       replaced meter, and only a person can say which.</li>
 *   <li>A meter with too little history has no average to be outside of. The read posts, and the reading
 *       records {@code plausibilityChecked=false} so nobody later mistakes "unchecked" for "checked and
 *       fine". Holding every new meter's second read would teach supervisors to verify without looking.</li>
 * </ul>
 */
public final class PlausibilityPolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MINUTES_PER_DAY = BigDecimal.valueOf(1440);
    /** A floor on the elapsed time, so two reads a minute apart do not imply an absurd daily rate. */
    private static final Duration MINIMUM_ELAPSED = Duration.ofHours(1);

    private PlausibilityPolicy() {
    }

    /**
     * @param evaluated {@code false} when there was no average to compare against
     * @param reason why the reading is held, in SRS wording plus the numbers; {@code null} when plausible
     */
    public record Outcome(boolean evaluated, boolean plausible, BigDecimal impliedDaily,
            BigDecimal trailingDailyAverage, BigDecimal bandLow, BigDecimal bandHigh, String reason) {

        public static Outcome baseline() {
            return new Outcome(false, true, null, null, null, null, null);
        }
    }

    /**
     * @param consumption the register delta; negative when the register went backwards
     * @param elapsed time since the previous posted register read
     * @param trailingDailyAverage the meter's average daily consumption over the trailing window, or
     *        {@code null} if it has none
     * @param history how many posted readings that average is built from
     */
    public static Outcome evaluate(BigDecimal consumption, Duration elapsed, BigDecimal trailingDailyAverage,
            int history, int minimumHistory, BigDecimal bandPct) {
        if (consumption.signum() < 0) {
            return new Outcome(true, false, null, trailingDailyAverage, null, null,
                    "Implausible Reading - the register is lower than the previous posted read ("
                            + consumption.negate().stripTrailingZeros().toPlainString()
                            + " below it); held for supervisor verification.");
        }
        BigDecimal daily = dailyRate(consumption, elapsed);
        if (trailingDailyAverage == null || history < minimumHistory) {
            return new Outcome(false, true, daily, trailingDailyAverage, null, null, null);
        }
        BigDecimal fraction = bandPct.max(BigDecimal.ZERO).divide(HUNDRED, 6, RoundingMode.HALF_UP);
        BigDecimal low = trailingDailyAverage.multiply(BigDecimal.ONE.subtract(fraction)).max(BigDecimal.ZERO)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal high = trailingDailyAverage.multiply(BigDecimal.ONE.add(fraction)).setScale(4, RoundingMode.HALF_UP);
        boolean plausible = daily.compareTo(low) >= 0 && daily.compareTo(high) <= 0;
        return new Outcome(true, plausible, daily, trailingDailyAverage, low, high, plausible ? null
                : "Implausible Reading - implied " + daily.stripTrailingZeros().toPlainString()
                        + " per day is outside the plausibility band " + low.stripTrailingZeros().toPlainString()
                        + ".." + high.stripTrailingZeros().toPlainString() + " of the trailing average "
                        + trailingDailyAverage.stripTrailingZeros().toPlainString()
                        + "; held for supervisor verification, not silently accepted.");
    }

    /** Consumption per day over {@code elapsed}, with a one-hour floor. */
    public static BigDecimal dailyRate(BigDecimal consumption, Duration elapsed) {
        Duration span = elapsed == null || elapsed.compareTo(MINIMUM_ELAPSED) < 0 ? MINIMUM_ELAPSED : elapsed;
        return consumption.multiply(MINUTES_PER_DAY)
                .divide(BigDecimal.valueOf(span.toMinutes()), 4, RoundingMode.HALF_UP);
    }
}
