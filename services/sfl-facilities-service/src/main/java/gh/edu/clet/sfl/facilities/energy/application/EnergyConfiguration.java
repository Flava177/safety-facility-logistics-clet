package gh.edu.clet.sfl.facilities.energy.application;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * S157's business thresholds, read from runtime configuration at the moment they are used.
 *
 * <p>Same contract as {@code BookingConfiguration}: versioned, changeable without a deploy, never cached
 * across a call, site-first then platform default, and a fallback on every read so the module works on a
 * database nobody seeded. The defaults below match the V17 seed exactly; a difference between the two would
 * mean the documented value is not the one in force on an unseeded database.
 *
 * <p>A value that cannot be parsed falls back rather than failing. For every key here the fallback is the
 * documented default, so a typo degrades to "as shipped" instead of refusing every reading at the site.
 */
@Component
public class EnergyConfiguration {

    static final String KEY_BAND_PCT = "energy.plausibility.band-pct";
    static final String KEY_TRAILING_DAYS = "energy.plausibility.trailing-days";
    static final String KEY_MIN_HISTORY = "energy.plausibility.min-history";
    static final String KEY_VARIANCE_THRESHOLD = "energy.variance.threshold-pct";
    static final String KEY_SPIKE_PCT = "energy.anomaly.spike-pct";
    static final String KEY_BASELINE_DAYS = "energy.anomaly.baseline-days";
    static final String KEY_MIN_BASELINE_DAYS = "energy.anomaly.min-baseline-days";
    static final String KEY_KPI_PERIOD = "energy.kpi.period";
    static final String KEY_MIN_COMPLETENESS = "energy.kpi.min-completeness-pct";
    static final String KEY_EXPECTED_INTERVAL = "energy.expected-interval-minutes.";
    static final String KEY_CLOSE_LOOKBACK = "energy.close.lookback-periods";
    static final String KEY_RETENTION_DAYS = "energy.retention.reading-days";

    private final RuntimeConfigurationPort configuration;

    public EnergyConfiguration(RuntimeConfigurationPort configuration) {
        this.configuration = configuration;
    }

    /** S157-01: the plausibility band, as a percentage either side of the trailing daily average. */
    public BigDecimal plausibilityBandPct(String siteCode) {
        return decimal(KEY_BAND_PCT, siteCode, BigDecimal.valueOf(50));
    }

    public int plausibilityTrailingDays(String siteCode) {
        return Math.max(1, configuration.integer(KEY_TRAILING_DAYS, siteCode, 90));
    }

    public int plausibilityMinimumHistory(String siteCode) {
        return Math.max(1, configuration.integer(KEY_MIN_HISTORY, siteCode, 2));
    }

    /** S157-02: overspend beyond this percentage of budget raises a variance alert at close. */
    public BigDecimal varianceThresholdPct(String siteCode) {
        return decimal(KEY_VARIANCE_THRESHOLD, siteCode, BigDecimal.TEN);
    }

    public BigDecimal anomalySpikePct(String siteCode) {
        return decimal(KEY_SPIKE_PCT, siteCode, BigDecimal.valueOf(50));
    }

    public int anomalyBaselineDays(String siteCode) {
        return Math.max(1, configuration.integer(KEY_BASELINE_DAYS, siteCode, 14));
    }

    public int anomalyMinimumBaselineDays(String siteCode) {
        return Math.max(1, configuration.integer(KEY_MIN_BASELINE_DAYS, siteCode, 7));
    }

    /**
     * S157-03: the KPI computation period. Read at platform scope only: the cluster-wide total has to be
     * computed over one period type for every site, so a per-site override would be meaningless.
     */
    public EnergyPeriod.PeriodType kpiPeriodType() {
        String value = configuration.find(KEY_KPI_PERIOD, null).orElse("MONTH").strip().toUpperCase(Locale.ROOT);
        try {
            return EnergyPeriod.PeriodType.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return EnergyPeriod.PeriodType.MONTH;
        }
    }

    /** S157-03: below this percentage of expected readings a KPI carries the LOW flag. Platform scope, as above. */
    public BigDecimal minimumCompletenessPct() {
        return decimal(KEY_MIN_COMPLETENESS, null, BigDecimal.valueOf(80));
    }

    /** The default expected reading interval for a newly registered meter of this source. */
    public int defaultExpectedIntervalMinutes(String siteCode, MeterSource source) {
        int fallback = source == MeterSource.MANUAL ? 43200 : 60;
        return Math.max(1, configuration.integer(KEY_EXPECTED_INTERVAL + source.name(), siteCode, fallback));
    }

    public int closeLookbackPeriods() {
        return Math.max(1, configuration.integer(KEY_CLOSE_LOOKBACK, null, 3));
    }

    /** SRS 4.2: how long a raw reading is kept. Never less than a year - the budget cycle needs it. */
    public int readingRetentionDays() {
        return Math.max(366, configuration.integer(KEY_RETENTION_DAYS, null, 2555));
    }

    private BigDecimal decimal(String key, String siteCode, BigDecimal fallback) {
        return configuration.find(key, siteCode).map(String::strip).map(value -> {
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException unparseable) {
                return fallback;
            }
        }).orElse(fallback);
    }
}
