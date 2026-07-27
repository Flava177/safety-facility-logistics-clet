package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

/**
 * The ordered chain-of-custody hops for a sensitive dispatch, matching the CT-05 secure-dispatch
 * narrative (warehouse staging through return reconciliation). Not every dispatch traverses every hop;
 * {@link gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.CustodyChainPolicy} validates ordering
 * and the mandatory hops rather than a fixed count.
 */
public enum CustodyHop {
    WAREHOUSE_STAGING,
    DISPATCH,
    TRANSIT,
    CENTRE_RECEIPT,
    HALL_DEPLOYMENT,
    COLLECTION,
    RETURN;

    /** Zero-based canonical position used to detect out-of-order handovers. */
    public int order() {
        return ordinal();
    }
}
