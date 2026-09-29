package gh.edu.clet.sfl.facilities.buildingsystems.domain;

/**
 * Why an authenticated reading was held for review rather than stored as fact - SRS-SFL-S156-01, -04.
 *
 * <p>The split that matters is {@link #releasable()}. A reading from a device nobody has registered yet,
 * or mapped to a room that has since gone, was a true measurement filed against the wrong key: once the
 * mapping is fixed it can be released as fact. An impossible value or an out-of-order timestamp is not a
 * filing problem - the reading itself is untrustworthy - so it can only be discarded with a reason.
 * Releasing it would turn a flagged value into exactly the "fact" S156-01 forbids.
 */
public enum QuarantineReason {
    /** S156-04 error state "Unregistered Device": quarantined and flagged for registration. */
    DEVICE_UNREGISTERED(true),
    /** S156-01 error state "Unresolvable Location": quarantined pending mapping. */
    LOCATION_UNRESOLVABLE(true),
    /** S156-01 validation: a physically impossible value is flagged, not stored as fact. */
    IMPLAUSIBLE_VALUE(false),
    /** S156-01 validation: older than the channel's last reading by more than the clock-skew tolerance. */
    OUT_OF_ORDER(false),
    /** Dated further in the future than the clock-skew tolerance - a gateway with a wrong clock. */
    CLOCK_SKEW(false);

    private final boolean releasable;

    QuarantineReason(boolean releasable) {
        this.releasable = releasable;
    }

    public boolean releasable() {
        return releasable;
    }
}
