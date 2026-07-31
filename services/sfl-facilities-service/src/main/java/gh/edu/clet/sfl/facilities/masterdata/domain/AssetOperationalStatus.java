package gh.edu.clet.sfl.facilities.masterdata.domain;

/**
 * Whether a facility asset is working.
 *
 * <p>Separate from {@link gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus},
 * which says whether the <em>record</em> is in use. An asset can be {@code ACTIVE} as a record and
 * {@code OUT_OF_SERVICE} as a machine, and conflating the two would make "we no longer track this
 * chiller" indistinguishable from "this chiller is broken".
 */
public enum AssetOperationalStatus {

    OPERATIONAL,
    /** Working, but below specification — a lift running on one of two motors. */
    DEGRADED,
    UNDER_MAINTENANCE,
    OUT_OF_SERVICE,
    /** Retired in place. Raises no blockers; kept for history. */
    DECOMMISSIONED;

    /** {@code true} when this status should contribute a readiness blocker for spaces the asset serves. */
    public boolean impairsReadiness() {
        return this == DEGRADED || this == OUT_OF_SERVICE || this == UNDER_MAINTENANCE;
    }

    /** {@code true} when the impairment is total rather than partial. */
    public boolean isTotalFailure() {
        return this == OUT_OF_SERVICE;
    }
}
