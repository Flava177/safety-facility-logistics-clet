package gh.edu.clet.sfl.facilities.booking.domain.policy;

import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.ReadinessHoldReason;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;

/**
 * Whether the state of a space is a reason not to use it — SRS-SFL-S159-02.
 *
 * <h2>One function, two jobs</h2>
 *
 * The same test is asked at two moments and means something different each time, and recognising
 * that is what keeps the two from drifting:
 *
 * <ul>
 *   <li><strong>At request time</strong> a non-null answer is a <em>refusal</em>. The requester is
 *       told the hall is blocked and picks another one, or somebody with
 *       {@code FACILITIES_BOOKING_OVERRIDE} books it anyway with a recorded reason.</li>
 *   <li><strong>Afterwards</strong> a non-null answer is a <em>hold</em>. The booking already exists
 *       and is in somebody's diary, so it keeps its status and gains a visible flag — see
 *       {@code Booking} for why a hold is not a state.</li>
 * </ul>
 *
 * <p>Two functions would eventually disagree, and the disagreement would show up as a booking that
 * could be made but was permanently held, or one that was refused for a condition no sweep ever
 * flagged. So there is one, and the caller decides what a non-null answer means.
 *
 * <h2>Why {@code DEGRADED} does not block an ordinary booking</h2>
 *
 * S152 already made that call in {@code FacilityRoom.availableForBooking}: a hall with one failed
 * projector is usable, and refusing it would be worse than warning about it. An examination is held
 * to the stricter standard because "probably fine" is not something an examination centre can run on.
 * This class mirrors that split rather than inventing a third standard.
 */
public final class ReadinessHoldPolicy {

    private ReadinessHoldPolicy() {
    }

    /**
     * The reason this space should not host this booking, or {@code null} when it is fine.
     *
     * <p>Ordered most-severe first, and the order is the message: a space that is both withdrawn and
     * blocked is reported as withdrawn, because that is the fact the reader has to deal with.
     *
     * @param readinessLocked the NFR 23.3 examination lock. A locked hall is being held for an
     *        examination, so a meeting booked into it is the thing that has to move — which is why
     *        the test exempts examination bookings rather than treating the lock as a blanket refusal.
     */
    public static ReadinessHoldReason holdFor(BookingPurpose purpose, LocationReadinessStatus readiness,
            boolean bookable, boolean examinationCapable, boolean lifecycleActive, boolean readinessLocked) {
        if (!lifecycleActive || !bookable) {
            return ReadinessHoldReason.SPACE_WITHDRAWN;
        }
        if (readiness == LocationReadinessStatus.BLOCKED) {
            return ReadinessHoldReason.SPACE_BLOCKED;
        }
        if (purpose != null && purpose.requiresExaminationReadiness()
                && (!examinationCapable || readiness != LocationReadinessStatus.READY)) {
            return ReadinessHoldReason.NOT_EXAMINATION_READY;
        }
        if (readinessLocked && (purpose == null || !purpose.requiresExaminationReadiness())) {
            return ReadinessHoldReason.LOCKED_FOR_EXAMINATION;
        }
        return null;
    }

    /** The refusal a requester reads, naming the space and what is wrong with it. */
    public static String explain(ReadinessHoldReason reason, String roomCode) {
        return switch (reason) {
            case SPACE_WITHDRAWN -> roomCode + " is not currently offered for booking.";
            case SPACE_BLOCKED -> roomCode + " has an open critical readiness blocker and cannot be used.";
            case NOT_EXAMINATION_READY -> roomCode
                    + " is not certified ready for examination use. An examination needs a space assessed"
                    + " READY, not merely usable.";
            case LOCKED_FOR_EXAMINATION -> roomCode
                    + " is locked for examination use and cannot be booked for anything else.";
        };
    }
}
