package gh.edu.clet.sfl.facilities.booking.domain;

/**
 * What cleaning a booking asks for around its window - the S159 half of SRS-SFL-S169-01.
 *
 * <p>Stated by the requester on the booking itself, because the requester is the one person who
 * knows that the moot court will be full of paper at five o'clock. S169 reads it when the booking is
 * confirmed and raises the task against the booking; nobody re-enters it in a second system, which is
 * the whole of what the requirement asks for.
 *
 * <p>{@link #NONE} is the default and the common case. A booking that says nothing about cleaning is
 * covered by the space's routine schedule like any other hour of the day.
 */
public enum CleaningRequirement {

    NONE(false, false),
    /** Cleaned before the booking starts - in time for the setup buffer, not the booked start. */
    SETUP(true, false),
    /** Cleaned after the booking ends - within the teardown buffer. */
    TEARDOWN(false, true),
    SETUP_AND_TEARDOWN(true, true);

    private final boolean beforeUse;
    private final boolean afterUse;

    CleaningRequirement(boolean beforeUse, boolean afterUse) {
        this.beforeUse = beforeUse;
        this.afterUse = afterUse;
    }

    public boolean beforeUse() {
        return beforeUse;
    }

    public boolean afterUse() {
        return afterUse;
    }

    public boolean requiresCleaning() {
        return beforeUse || afterUse;
    }

    /** {@code null} reads as {@link #NONE}: a request that did not mention cleaning asked for none. */
    public static CleaningRequirement orNone(CleaningRequirement requirement) {
        return requirement == null ? NONE : requirement;
    }
}
