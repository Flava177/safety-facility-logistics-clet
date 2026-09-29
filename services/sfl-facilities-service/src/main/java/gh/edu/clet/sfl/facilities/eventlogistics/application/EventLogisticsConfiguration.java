package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.OwningSystem;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.policy.EventRiskPolicy;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * S173's rules, read from runtime configuration at the moment they are needed - the same contract as
 * {@code BookingConfiguration}: nothing cached across a call, every read with a fallback so the module
 * works on a database nobody seeded, defaults seeded by V20, site values overriding platform ones
 * (NFR "Configuration Without Code").
 *
 * <h2>The one fallback worth arguing about</h2>
 *
 * <p>{@link #owningSystemAvailable} falls back to <em>unavailable</em> for S172 and available for the
 * three built systems. A missing row therefore never makes catering look like a system request - the
 * safe direction for S173-02's "not falsely shown as fulfilled".
 */
@Component
public class EventLogisticsConfiguration {

    public static final String KEY_RISK_ATTENDANCE = "event-logistics.risk.attendance-threshold";
    public static final String KEY_RISK_EXTERNAL_CONTRACTORS = "event-logistics.risk.external-contractors";
    public static final String KEY_RISK_TEMPORARY_STRUCTURES = "event-logistics.risk.temporary-structures";
    public static final String KEY_RISK_CATEGORIES = "event-logistics.risk.categories";
    public static final String KEY_ESCALATION_WINDOW = "event-logistics.escalation.window";
    public static final String KEY_TEMPLATE_GAP_THRESHOLD = "event-logistics.template.gap-threshold";
    public static final String KEY_SWEEP_BATCH = "event-logistics.sweep.batch";
    public static final String KEY_UPCOMING_HORIZON = "event-logistics.readiness.upcoming-days";
    static final String KEY_OWNING_SYSTEM_PREFIX = "event-logistics.owning-system.";

    static final int DEFAULT_ATTENDANCE_THRESHOLD = 500;
    static final String DEFAULT_RISK_CATEGORIES = "GRADUATION,CONCERT,EXHIBITION,SPORTS";
    static final Duration DEFAULT_ESCALATION_WINDOW = Duration.ofHours(48);

    private final RuntimeConfigurationPort configuration;

    public EventLogisticsConfiguration(RuntimeConfigurationPort configuration) {
        this.configuration = configuration;
    }

    /** SRS-SFL-S173-03: which events are higher-risk at this site, right now. */
    public EventRiskPolicy.RiskCriteria riskCriteria(String siteCode) {
        return new EventRiskPolicy.RiskCriteria(
                configuration.integer(KEY_RISK_ATTENDANCE, siteCode, DEFAULT_ATTENDANCE_THRESHOLD),
                flag(KEY_RISK_EXTERNAL_CONTRACTORS, siteCode, true),
                flag(KEY_RISK_TEMPORARY_STRUCTURES, siteCode, true),
                configuration.find(KEY_RISK_CATEGORIES, siteCode).map(EventLogisticsConfiguration::categories)
                        .orElseGet(() -> categories(DEFAULT_RISK_CATEGORIES)));
    }

    /**
     * SRS-SFL-S173-04: how long before an event starts an unresolved request escalates. Two days by
     * default - long enough for somebody to find another projector, which is the point of escalating
     * before the event rather than on the morning.
     */
    public Duration escalationWindow(String siteCode) {
        Duration window = configuration.duration(KEY_ESCALATION_WINDOW, siteCode, DEFAULT_ESCALATION_WINDOW);
        return window.isNegative() || window.isZero() ? DEFAULT_ESCALATION_WINDOW : window;
    }

    /** How many reconciliation gaps make a template line "persistent" and pre-populate decompositions. */
    public int templateGapThreshold(String siteCode) {
        return Math.max(1, configuration.integer(KEY_TEMPLATE_GAP_THRESHOLD, siteCode, 2));
    }

    public int sweepBatchSize() {
        return Math.max(1, configuration.integer(KEY_SWEEP_BATCH, null, 200));
    }

    /** How far ahead the upcoming-events view looks when the caller does not say. */
    public Duration upcomingHorizon(String siteCode) {
        return Duration.ofDays(Math.max(1, configuration.integer(KEY_UPCOMING_HORIZON, siteCode, 30)));
    }

    /**
     * The owning-system availability registry (S173-02 validation). S172 is Phase 3 and defaults to
     * unavailable; the built systems default to available.
     */
    public boolean owningSystemAvailable(OwningSystem system, String siteCode) {
        return flag(availabilityKey(system), siteCode, system != OwningSystem.S172);
    }

    /** Public so a test can flip one owning system off directly, as {@code sfl.<module>.*} keys do. */
    public static String availabilityKey(OwningSystem system) {
        return KEY_OWNING_SYSTEM_PREFIX + system.name() + ".available";
    }

    private boolean flag(String key, String siteCode, boolean fallback) {
        return configuration.find(key, siteCode).map(value -> Boolean.parseBoolean(value.strip()))
                .orElse(fallback);
    }

    private static Set<String> categories(String raw) {
        return Arrays.stream(raw.split(",")).map(String::strip).filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
}
