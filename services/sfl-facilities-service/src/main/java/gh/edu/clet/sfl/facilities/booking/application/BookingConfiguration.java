package gh.edu.clet.sfl.facilities.booking.application;

import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * S159's rules, read out of runtime configuration at the moment they are needed.
 *
 * <p>Same contract as {@code MaintenanceConfiguration} and for the same reason: NFR 23.8 requires
 * these to be configurable and versioned without a redeploy, so nothing here is cached across a call.
 * Every read carries a fallback, so the module works on a database nobody has seeded.
 *
 * <h2>The one value worth arguing about</h2>
 *
 * {@link #noShowGrace} defaults to twenty minutes, and the sweep releases the space at that point
 * rather than at the end of the booked window. A three-hour lecture nobody attended should not hold a
 * hall for three hours; twenty minutes is long enough to cover a late start and short enough that the
 * room is recoverable. The trade is that arriving at minute twenty-five finds the booking gone —
 * {@code NO_SHOW} is terminal — so a site that runs late by habit should raise this rather than work
 * around it.
 */
@Component
public class BookingConfiguration {

    /** Purposes that always need approving, whatever their length. */
    static final String KEY_APPROVAL_PURPOSES = "booking.approval.purposes";
    /** A booking longer than this needs approving whatever its purpose. */
    static final String KEY_APPROVAL_DURATION = "booking.approval.duration-threshold";
    /** Whether every booking needs approving while the site is in examination mode. */
    static final String KEY_APPROVAL_IN_EXAMINATION = "booking.approval.all-in-examination-mode";
    /** How long after the start a confirmed booking may go unused before it is a no-show. */
    static final String KEY_NO_SHOW_GRACE = "booking.no-show.grace";
    /** How far ahead a booking may be made. */
    static final String KEY_HORIZON_DAYS = "booking.horizon.days";
    /** Default room turnaround, in minutes, before and after an ordinary booking. */
    static final String KEY_SETUP_MINUTES = "booking.setup.default-minutes";
    static final String KEY_TEARDOWN_MINUTES = "booking.teardown.default-minutes";
    /** Turnaround for an examination, which needs the layout changing and changing back. */
    static final String KEY_EXAMINATION_SETUP_MINUTES = "booking.setup.examination-minutes";
    static final String KEY_EXAMINATION_TEARDOWN_MINUTES = "booking.teardown.examination-minutes";
    /** How many rows one sweep may process. */
    static final String KEY_SWEEP_BATCH = "booking.sweep.batch";

    private static final Set<BookingPurpose> DEFAULT_APPROVAL_PURPOSES =
            EnumSet.of(BookingPurpose.EXAMINATION, BookingPurpose.EVENT);

    private final RuntimeConfigurationPort configuration;

    public BookingConfiguration(RuntimeConfigurationPort configuration) {
        this.configuration = configuration;
    }

    /**
     * Whether this booking needs an approver before it is confirmed.
     *
     * <p>Three independent triggers, any one of which is enough: the purpose, the length, and the
     * site's operating mode. The mode trigger is the one that earns its keep — while a centre is in
     * examination mode, a meeting booked into the wrong hall is a problem nobody finds until the
     * morning, and routing everything past a human for those few weeks is cheap.
     */
    public boolean approvalRequired(String siteCode, BookingPurpose purpose, BookingWindow window,
            OperatingMode mode) {
        if (mode == OperatingMode.EXAMINATION && approvalInExaminationMode(siteCode)) {
            return true;
        }
        if (approvalPurposes(siteCode).contains(purpose)) {
            return true;
        }
        Duration threshold = configuration.duration(KEY_APPROVAL_DURATION, siteCode, Duration.ofHours(8));
        return window != null && window.duration().compareTo(threshold) > 0;
    }

    public Set<BookingPurpose> approvalPurposes(String siteCode) {
        return configuration.find(KEY_APPROVAL_PURPOSES, siteCode)
                .map(BookingConfiguration::parsePurposes)
                .orElse(DEFAULT_APPROVAL_PURPOSES);
    }

    public boolean approvalInExaminationMode(String siteCode) {
        return configuration.find(KEY_APPROVAL_IN_EXAMINATION, siteCode)
                .map(value -> Boolean.parseBoolean(value.strip()))
                .orElse(true);
    }

    /** How long a confirmed booking may sit unused before the sweep calls it a no-show. */
    public Duration noShowGrace(String siteCode) {
        Duration grace = configuration.duration(KEY_NO_SHOW_GRACE, siteCode, Duration.ofMinutes(20));
        return grace.isNegative() ? Duration.ZERO : grace;
    }

    /** How far ahead a booking may be made. Beyond this it is a data-entry error, not a plan. */
    public Duration horizon(String siteCode) {
        return Duration.ofDays(Math.max(1, configuration.integer(KEY_HORIZON_DAYS, siteCode, 365)));
    }

    /** The turnaround this kind of booking needs, when the caller did not say. */
    public int defaultSetupMinutes(String siteCode, BookingPurpose purpose) {
        return purpose != null && purpose.requiresExaminationReadiness()
                ? nonNegative(KEY_EXAMINATION_SETUP_MINUTES, siteCode, 30)
                : nonNegative(KEY_SETUP_MINUTES, siteCode, 0);
    }

    public int defaultTeardownMinutes(String siteCode, BookingPurpose purpose) {
        return purpose != null && purpose.requiresExaminationReadiness()
                ? nonNegative(KEY_EXAMINATION_TEARDOWN_MINUTES, siteCode, 30)
                : nonNegative(KEY_TEARDOWN_MINUTES, siteCode, 0);
    }

    public int sweepBatchSize(String siteCode) {
        return Math.max(1, configuration.integer(KEY_SWEEP_BATCH, siteCode, 200));
    }

    private int nonNegative(String key, String siteCode, int fallback) {
        return Math.max(0, configuration.integer(key, siteCode, fallback));
    }

    /**
     * Parses a comma-separated purpose list, ignoring anything it does not recognise.
     *
     * <p>Ignoring rather than failing is the safe direction here and the unsafe one elsewhere: a typo
     * in the list quietly stops requiring approval for that purpose, which is a weakening. It is still
     * better than the alternative — throwing would make one bad character in a configuration row
     * refuse every booking at the site.
     */
    private static Set<BookingPurpose> parsePurposes(String raw) {
        EnumSet<BookingPurpose> purposes = EnumSet.noneOf(BookingPurpose.class);
        Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .forEach(value -> {
                    try {
                        purposes.add(BookingPurpose.valueOf(value));
                    } catch (IllegalArgumentException unknown) {
                        // Deliberately swallowed. See the method comment.
                    }
                });
        return purposes;
    }
}
