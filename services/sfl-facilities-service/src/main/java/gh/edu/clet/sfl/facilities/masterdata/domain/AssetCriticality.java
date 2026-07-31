package gh.edu.clet.sfl.facilities.masterdata.domain;

/**
 * How much a failure of this asset matters.
 *
 * <p>The readiness engine reads it directly: an asset that is out of service raises a blocker whose
 * severity comes from this field, so a failed examination-hall generator blocks the hall while a
 * failed cafeteria fridge does not. Without it, readiness would have to treat every fault the same
 * and would be useless as a signal.
 *
 * <p>Ordered most severe first so {@code compareTo} and natural ordering sort the way an operator
 * reads a queue.
 */
public enum AssetCriticality {

    /** Failure stops operations. Blocks readiness on any space it serves. */
    CRITICAL,
    /** Failure degrades operations materially. */
    HIGH,
    /** Failure is disruptive but worked around. */
    MEDIUM,
    /** Failure is cosmetic or deferrable. */
    LOW
}
