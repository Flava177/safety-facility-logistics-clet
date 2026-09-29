package gh.edu.clet.sfl.facilities.spaceplanning.application;

import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.policy.UtilisationPolicy;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * S158's business thresholds, read from runtime configuration when they are needed - never cached.
 *
 * <p>Same contract as {@code BookingConfiguration}: configurable and versioned without a redeploy
 * (Configuration Without Code), every read with a fallback so the module works on an unseeded database,
 * and V18 seeds the defaults. Site-scoped values override the platform default.
 *
 * <h2>The one value worth arguing about</h2>
 *
 * {@link #underUtilisedThreshold} defaults to 20% because the S158-03 acceptance criterion says so. It is
 * a rate under {@code UtilisationPolicy}'s definition (frequency x occupancy), which the SRS does not
 * give; a site that reads "20% of capacity" as occupancy alone should know that this setting is
 * stricter than that reading, and the runbook says so.
 */
@Component
public class SpacePlanningConfiguration {

    static final String KEY_UNDER_THRESHOLD = "space-planning.utilisation.under-threshold-percent";
    static final String KEY_PERIOD_DAYS = "space-planning.utilisation.period-days";
    static final String KEY_HOURS_PER_DAY = "space-planning.utilisation.available-hours-per-day";
    static final String KEY_AVAILABLE_DAYS = "space-planning.utilisation.available-days";
    static final String KEY_GAP_THRESHOLD = "space-planning.signal.gap-threshold-percent";
    static final String KEY_PERSISTENCE = "space-planning.signal.persistence-periods";

    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

    private final RuntimeConfigurationPort configuration;

    public SpacePlanningConfiguration(RuntimeConfigurationPort configuration) {
        this.configuration = configuration;
    }

    /** Under this utilisation rate, over one complete period, a space is on the under-utilisation list. */
    public BigDecimal underUtilisedThreshold(String siteCode) {
        return UtilisationPolicy.percent(clamp(configuration.integer(KEY_UNDER_THRESHOLD, siteCode, 20), 0, 100));
    }

    public int periodDays(String siteCode) {
        return clamp(configuration.integer(KEY_PERIOD_DAYS, siteCode, 7), 1, 366);
    }

    public int availableHoursPerDay(String siteCode) {
        return clamp(configuration.integer(KEY_HOURS_PER_DAY, siteCode, 10), 1, 24);
    }

    /**
     * Days that count towards available hours. An unparseable list falls back to weekdays rather than to
     * nothing: an empty set would make every period unevaluable and silently stop every signal.
     */
    public Set<DayOfWeek> availableDays(String siteCode) {
        return configuration.find(KEY_AVAILABLE_DAYS, siteCode).map(SpacePlanningConfiguration::parseDays)
                .filter(days -> !days.isEmpty()).orElse(WEEKDAYS);
    }

    /** How far the planned occupancy may exceed the observed rate before a period counts as a gap. */
    public BigDecimal gapThreshold(String siteCode) {
        return UtilisationPolicy.percent(clamp(configuration.integer(KEY_GAP_THRESHOLD, siteCode, 30), 1, 100));
    }

    /** How many consecutive gap periods make the gap "persistent". */
    public int persistencePeriods(String siteCode) {
        return clamp(configuration.integer(KEY_PERSISTENCE, siteCode, 2), 1, 52);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Set<DayOfWeek> parseDays(String raw) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(raw.split(",")).map(String::strip).filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT)).forEach(value -> {
                    try {
                        days.add(DayOfWeek.valueOf(value));
                    } catch (IllegalArgumentException unknown) {
                        // Ignored: one mistyped day should not stop the others counting.
                    }
                });
        return days;
    }
}
