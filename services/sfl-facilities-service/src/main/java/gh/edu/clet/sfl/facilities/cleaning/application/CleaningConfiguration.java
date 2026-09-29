package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * S169's business thresholds, read from runtime configuration at the moment they are needed.
 *
 * <p>Same contract as {@code BookingConfiguration}: NFR 23.8 (Configuration Without Code) requires these
 * to be changeable and versioned without a deploy, so nothing is cached across a call, and every read
 * carries the default V19 seeds so a database nobody has configured still works. Site-scoped values win
 * over platform defaults.
 */
@Component
public class CleaningConfiguration {

    static final String KEY_HORIZON_DAYS = "cleaning.schedule.horizon-days";
    static final String KEY_TIME_ZONE = "cleaning.schedule.time-zone";
    static final String KEY_BOOKING_TASK_MINUTES = "cleaning.booking.task-minutes";
    static final String KEY_REACTIVE_TARGET = "cleaning.reactive.target";
    static final String KEY_CREWS = "cleaning.capacity.crews";
    static final String KEY_LOW_RATING_MAX = "cleaning.feedback.low-rating-max";
    static final String KEY_LOW_RATING_REPEAT = "cleaning.feedback.low-rating-repeat-count";
    static final String KEY_LOW_RATING_WINDOW = "cleaning.feedback.low-rating-window";
    static final String KEY_DISCREPANCY_TOLERANCE = "cleaning.sla.discrepancy-tolerance";
    static final String KEY_SWEEP_BATCH = "cleaning.sweep.batch";

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Africa/Accra");

    private final RuntimeConfigurationPort configuration;

    public CleaningConfiguration(RuntimeConfigurationPort configuration) {
        this.configuration = configuration;
    }

    /** How far ahead routine tasks are materialised. At least a day, at most a quarter. */
    public Duration horizon(String siteCode) {
        return Duration.ofDays(Math.max(1, Math.min(92, configuration.integer(KEY_HORIZON_DAYS, siteCode, 7))));
    }

    /**
     * The zone schedule times are read in. An unparseable value falls back rather than failing, for the
     * reason {@code BookingConfiguration.parsePurposes} gives: one bad character in a configuration row
     * must not stop every site's sweep.
     */
    public ZoneId timeZone(String siteCode) {
        return configuration.find(KEY_TIME_ZONE, siteCode).map(value -> {
            try {
                return ZoneId.of(value.strip());
            } catch (DateTimeException unknown) {
                return DEFAULT_ZONE;
            }
        }).orElse(DEFAULT_ZONE);
    }

    /** Length of a booking setup or teardown clean when the booking's own buffer is shorter. */
    public Duration bookingTaskLength(String siteCode) {
        return Duration.ofMinutes(Math.max(5, configuration.integer(KEY_BOOKING_TASK_MINUTES, siteCode, 30)));
    }

    /** How long after it is raised a reactive request is due. */
    public Duration reactiveTarget(String siteCode) {
        Duration target = configuration.duration(KEY_REACTIVE_TARGET, siteCode, Duration.ofHours(4));
        return target.isNegative() || target.isZero() ? Duration.ofHours(4) : target;
    }

    /** Crews available at once at the site - the S169-04 capacity S173 draws on. */
    public int crews(String siteCode) {
        return Math.max(1, configuration.integer(KEY_CREWS, siteCode, 2));
    }

    public int lowRatingMax(String siteCode) {
        return Math.max(1, Math.min(4, configuration.integer(KEY_LOW_RATING_MAX, siteCode, 2)));
    }

    public int lowRatingRepeatCount(String siteCode) {
        return Math.max(1, configuration.integer(KEY_LOW_RATING_REPEAT, siteCode, 3));
    }

    public Duration lowRatingWindow(String siteCode) {
        Duration window = configuration.duration(KEY_LOW_RATING_WINDOW, siteCode, Duration.ofDays(30));
        return window.isNegative() || window.isZero() ? Duration.ofDays(30) : window;
    }

    /** How far a vendor-reported completion may sit from the recorded one before it is a discrepancy. */
    public Duration discrepancyTolerance(String siteCode) {
        Duration tolerance = configuration.duration(KEY_DISCREPANCY_TOLERANCE, siteCode, Duration.ofMinutes(5));
        return tolerance.isNegative() ? Duration.ZERO : tolerance;
    }

    public int sweepBatch(String siteCode) {
        return Math.max(1, configuration.integer(KEY_SWEEP_BATCH, siteCode, 500));
    }
}
