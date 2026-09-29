package gh.edu.clet.sfl.facilities.cleaning.domain.policy;

/**
 * When low ratings become a pattern - SRS-SFL-S169-02 "repeated low ratings for the same space or vendor
 * are surfaced for supervisor review".
 *
 * <p>"Repeated" is a count within a window, both configured (Configuration Without Code): three ratings
 * of two or less in thirty days by default. One bad rating is an occupant's bad day; three in a month
 * for the same room is the room, or the vendor.
 */
public final class LowRatingPolicy {

    private LowRatingPolicy() {
    }

    /** {@code true} when {@code rating} is at or below the configured low mark. */
    public static boolean isLow(int rating, int lowRatingMax) {
        return rating <= lowRatingMax;
    }

    /** {@code true} when {@code lowRatingsInWindow} has reached the configured repeat count. */
    public static boolean isRepeated(long lowRatingsInWindow, int repeatCount) {
        return lowRatingsInWindow >= Math.max(1, repeatCount);
    }
}
