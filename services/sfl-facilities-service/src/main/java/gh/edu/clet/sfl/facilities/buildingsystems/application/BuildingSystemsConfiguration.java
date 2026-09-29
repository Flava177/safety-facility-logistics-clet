package gh.edu.clet.sfl.facilities.buildingsystems.application;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.policy.TelemetryValidationPolicy.PlausibilityBand;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * S156's business thresholds, read from runtime configuration at the moment they are needed.
 *
 * <p>Same contract as {@code BookingConfiguration}: configurable and versioned without a redeploy
 * (Configuration Without Code), a site-level value overrides the estate default, nothing is cached across
 * a call, and every read has a fallback so the module works on a database nobody seeded. V16 seeds the
 * defaults below; the runbook lists every key.
 *
 * <h2>Values worth arguing about</h2>
 *
 * <ul>
 *   <li>{@link #clockSkewTolerance} - two minutes. Long enough for a gateway that batches and retries;
 *       short enough that a reading replayed from yesterday is quarantined as out of order.</li>
 *   <li>{@link #offlineWindow} - thirty minutes. Shorter raises offline alerts for every gateway reboot;
 *       longer means a dead sensor in an examination hall goes unnoticed for most of a paper.</li>
 *   <li>Retention - 400 days, so a year-on-year comparison of the same month always has both years.</li>
 * </ul>
 */
@Component
public class BuildingSystemsConfiguration {

    static final String KEY_CLOCK_SKEW = "bms.validation.clock-skew-tolerance";
    static final String KEY_DEFAULT_DEBOUNCE = "bms.rules.default-debounce";
    static final String KEY_OFFLINE_WINDOW = "bms.offline.window";
    static final String KEY_STALE_INTERVALS = "bms.health.stale-after-intervals";
    static final String KEY_LIFT_ENTRAPMENT_CODES = "bms.critical.lift-entrapment-codes";
    static final String KEY_OUTAGE_WINDOW = "bms.critical.outage-window";
    static final String KEY_REMINDER_LEAD_DAYS = "bms.lifecycle.reminder-lead-days";
    static final String KEY_RETENTION_DAYS = "bms.retention.readings-days";
    static final String KEY_SWEEP_BATCH = "bms.sweep.batch";
    static final String PLAUSIBILITY_PREFIX = "bms.plausibility.";

    /** Physical limits: what a working instrument can report at all, not what is comfortable. */
    static final Map<MeasuredQuantity, PlausibilityBand> DEFAULT_BANDS = defaultBands();

    private final RuntimeConfigurationPort configuration;

    public BuildingSystemsConfiguration(RuntimeConfigurationPort configuration) {
        this.configuration = configuration;
    }

    public Duration clockSkewTolerance(String siteCode) {
        return nonNegative(configuration.duration(KEY_CLOCK_SKEW, siteCode, Duration.ofMinutes(2)));
    }

    /** The debounce a rule gets when its author did not set one. */
    public Duration defaultDebounce(String siteCode) {
        return nonNegative(configuration.duration(KEY_DEFAULT_DEBOUNCE, siteCode, Duration.ofMinutes(5)));
    }

    public Duration offlineWindow(String siteCode) {
        Duration window = configuration.duration(KEY_OFFLINE_WINDOW, siteCode, Duration.ofMinutes(30));
        return window.isNegative() || window.isZero() ? Duration.ofMinutes(30) : window;
    }

    /** How many missed expected intervals make a device stale. At least one. */
    public int staleAfterIntervals(String siteCode) {
        return Math.max(1, configuration.integer(KEY_STALE_INTERVALS, siteCode, 2));
    }

    /** The normalised LIFT_STATUS codes that mean a passenger is trapped. Unparseable entries are ignored. */
    public Set<Integer> liftEntrapmentCodes(String siteCode) {
        Set<Integer> codes = new LinkedHashSet<>();
        String raw = configuration.find(KEY_LIFT_ENTRAPMENT_CODES, siteCode).orElse("3");
        Arrays.stream(raw.split(",")).map(String::strip).filter(value -> !value.isEmpty()).forEach(value -> {
            try {
                codes.add(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                // A typo drops one code rather than every entrapment signal at the site.
            }
        });
        // Never empty: an empty list would switch off entrapment escalation by configuration, which the
        // critical-fault design refuses to allow (see CriticalFaultPolicy).
        return codes.isEmpty() ? Set.of(3) : Set.copyOf(codes);
    }

    /** How recent a total-power-loss signal must be for a generator failed-start to count as "during an outage". */
    public Duration outageWindow(String siteCode) {
        return nonNegative(configuration.duration(KEY_OUTAGE_WINDOW, siteCode, Duration.ofMinutes(15)));
    }

    public int reminderLeadDays(String siteCode) {
        return Math.max(0, configuration.integer(KEY_REMINDER_LEAD_DAYS, siteCode, 30));
    }

    /** Readings older than this are purged, unless an alert cites them. Never less than one day. */
    public Duration readingRetention(String siteCode) {
        return Duration.ofDays(Math.max(1, configuration.integer(KEY_RETENTION_DAYS, siteCode, 400)));
    }

    public int sweepBatch(String siteCode) {
        return Math.max(1, configuration.integer(KEY_SWEEP_BATCH, siteCode, 500));
    }

    /**
     * The plausibility band for a quantity: {@code bms.plausibility.<quantity>.min} / {@code .max}.
     *
     * <p>An unparseable or inverted configured band falls back to the default rather than to "no band":
     * the direction that fails safe here is still flagging an impossible value.
     */
    public PlausibilityBand plausibility(MeasuredQuantity quantity, String siteCode) {
        PlausibilityBand fallback = DEFAULT_BANDS.get(quantity);
        String prefix = PLAUSIBILITY_PREFIX + quantity.configurationKey();
        try {
            BigDecimal min = decimal(prefix + ".min", siteCode).orElse(fallback.min());
            BigDecimal max = decimal(prefix + ".max", siteCode).orElse(fallback.max());
            return new PlausibilityBand(min, max);
        } catch (IllegalArgumentException misconfigured) {
            return fallback;
        }
    }

    private Optional<BigDecimal> decimal(String key, String siteCode) {
        return configuration.find(key, siteCode).map(String::strip).map(BigDecimal::new);
    }

    private static Duration nonNegative(Duration value) {
        return value.isNegative() ? Duration.ZERO : value;
    }

    private static Map<MeasuredQuantity, PlausibilityBand> defaultBands() {
        Map<MeasuredQuantity, PlausibilityBand> bands = new EnumMap<>(MeasuredQuantity.class);
        bands.put(MeasuredQuantity.TEMPERATURE_C, band("-50", "150"));
        bands.put(MeasuredQuantity.RELATIVE_HUMIDITY_PCT, band("0", "100"));
        bands.put(MeasuredQuantity.CO2_PPM, band("0", "20000"));
        bands.put(MeasuredQuantity.POWER_STATE, band("0", "1"));
        bands.put(MeasuredQuantity.ELECTRICAL_ENERGY_KWH, band("0", "10000000"));
        bands.put(MeasuredQuantity.WATER_VOLUME_M3, band("0", "1000000"));
        bands.put(MeasuredQuantity.GENERATOR_FUEL_LITRES, band("0", "100000"));
        bands.put(MeasuredQuantity.FUEL_TANK_LEVEL_PCT, band("0", "100"));
        bands.put(MeasuredQuantity.LIFT_STATUS, band("0", "9"));
        bands.put(MeasuredQuantity.GENERATOR_RUN_STATE, band("-1", "1"));
        bands.put(MeasuredQuantity.FAULT_CODE, band("0", "999999"));
        return Map.copyOf(bands);
    }

    private static PlausibilityBand band(String min, String max) {
        return new PlausibilityBand(new BigDecimal(min), new BigDecimal(max));
    }
}
