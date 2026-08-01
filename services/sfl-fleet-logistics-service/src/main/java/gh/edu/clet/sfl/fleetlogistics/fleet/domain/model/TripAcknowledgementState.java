package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Whether the assigned driver has answered for a trip, and how.
 *
 * <h2>Why this is not a {@link TripStatus}</h2>
 *
 * <p>The obvious implementation is two more statuses — {@code ACKNOWLEDGED} and {@code DEFERRED} —
 * and it is wrong. {@code TripStatus} answers "where is this trip in its lifecycle", and a trip whose
 * driver has confirmed is in exactly the same place as one whose driver has not yet looked: assigned,
 * holding a vehicle against a period, ready to start. Folding the driver's answer into that enum would
 * mean every transition rule, the GiST exclusion constraint on live statuses, the readiness
 * calculation and the dashboard's status counts all had to learn two states that tell them nothing.
 *
 * <p>They are two independent axes. A driver may defer a trip that is then started by a replacement
 * driver; a trip may be cancelled before anybody acknowledges it. Modelling them separately means
 * neither has to encode the other's combinations.
 *
 * <h2>Deferral is not refusal</h2>
 *
 * <p>{@link #DEFERRED} records that the driver cannot take the trip as scheduled and says why. It
 * does not release the assignment — the trip stays {@code ASSIGNED} to them and keeps its vehicle
 * booking — because unassigning on a driver's say-so would let a driver silently drop a trip nobody
 * is then watching. Reassigning is a dispatcher's decision, and the deferral is the signal that
 * prompts it.
 */
public enum TripAcknowledgementState {

    /** Assigned, and the driver has not answered yet. The state every assignment starts in. */
    PENDING,

    /** The driver has confirmed they will take the trip as scheduled. */
    CONFIRMED,

    /** The driver cannot take it as scheduled and has given a reason. The assignment still stands. */
    DEFERRED;

    /** True once the driver has answered either way. */
    public boolean isAnswered() {
        return this != PENDING;
    }

    /** Deferral must explain itself; confirmation has nothing to explain. */
    public boolean requiresReason() {
        return this == DEFERRED;
    }
}
