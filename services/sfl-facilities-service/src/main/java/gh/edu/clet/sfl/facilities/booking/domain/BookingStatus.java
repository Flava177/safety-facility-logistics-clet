package gh.edu.clet.sfl.facilities.booking.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The life of a booking.
 *
 * <h2>Why a request already holds the space</h2>
 *
 * {@link #REQUESTED} is a <em>holding</em> state: it occupies the room for its window exactly as
 * {@link #CONFIRMED} does. That is a deliberate choice against the obvious alternative, which is to
 * let anybody request anything and resolve clashes at approval.
 *
 * <p>The alternative fails in a way that is hard to undo. Three people request the same hall on
 * Tuesday; all three are told "requested"; two of them plan around a room they will not get, and the
 * approver is handed a conflict to arbitrate rather than a decision to make. Refusing the second
 * request at the moment it is made is unkinder for one second and kinder for the following week.
 *
 * <h2>Why there is no separate APPROVED state</h2>
 *
 * Approval is an <em>event</em> — recorded as a {@code BookingApproval}, with who and why — not a
 * state a booking sits in. A booking that has been approved is confirmed; there is nothing further
 * to do to it. Adding {@code APPROVED} between the two would create a state whose only difference
 * from {@code CONFIRMED} is that somebody has not yet pressed a second button, and that button does
 * not exist.
 *
 * <p>Bookings that need no approval go {@code REQUESTED → CONFIRMED} directly, and the absence of a
 * {@code BookingApproval} record is what says so.
 *
 * <h2>NO_SHOW is terminal and automatic</h2>
 *
 * Reached only by the scheduled sweep, for a confirmed booking still unstarted a configured grace
 * period after it should have begun. It is deliberately not something a person sets: "they did not
 * turn up" is an observation, and letting it be asserted by hand would make it an accusation.
 *
 * <p>It fires part-way through the window rather than at the end of it, which is what releases the
 * space to somebody else while the slot is still usable. See {@code Booking.isNoShowAt}.
 */
public enum BookingStatus {

    /** Asked for, and already holding the space. Awaiting approval where approval is required. */
    REQUESTED,
    /** Approved, or needed no approval. The space is held. */
    CONFIRMED,
    /** Somebody has arrived and taken the room. */
    IN_USE,
    /** Ran and finished. Terminal. */
    COMPLETED,
    /** Refused by an approver, with a reason. Terminal. */
    REJECTED,
    /** Withdrawn before use, by the requester or an officer. Terminal. */
    CANCELLED,
    /** Confirmed, its window passed, nobody came. Set by the sweep, never by hand. Terminal. */
    NO_SHOW;

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED = Map.of(
            REQUESTED, EnumSet.of(CONFIRMED, REJECTED, CANCELLED),
            CONFIRMED, EnumSet.of(IN_USE, CANCELLED, NO_SHOW),
            IN_USE, EnumSet.of(COMPLETED, CANCELLED),
            COMPLETED, EnumSet.noneOf(BookingStatus.class),
            REJECTED, EnumSet.noneOf(BookingStatus.class),
            CANCELLED, EnumSet.noneOf(BookingStatus.class),
            NO_SHOW, EnumSet.noneOf(BookingStatus.class));

    /**
     * Whether a booking in this state occupies its space.
     *
     * <p>The definition the conflict check, the availability query and the database exclusion
     * constraint all depend on. If it changes here it must change in {@code V10}'s constraint too —
     * they are two expressions of one rule and there is no compiler to catch them drifting apart.
     */
    public boolean holdsTheSpace() {
        return this == REQUESTED || this == CONFIRMED || this == IN_USE;
    }

    /** {@code true} while the booking is still something that might happen or is happening. */
    public boolean isLive() {
        return holdsTheSpace();
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    public boolean canTransitionTo(BookingStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public BookingStatus transitionTo(BookingStatus target) {
        if (!canTransitionTo(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "A booking cannot move from " + name() + " to " + target + ".");
        }
        return target;
    }
}
