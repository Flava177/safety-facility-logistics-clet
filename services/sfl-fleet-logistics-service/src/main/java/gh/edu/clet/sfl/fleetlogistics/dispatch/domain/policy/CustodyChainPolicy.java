package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHandover;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHop;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Pure chain-of-custody integrity rules. A gap or mismatch (missing handover, broken seal, count
 * mismatch, out-of-order hop) is a custody gap that must open an exception and block closure.
 */
public final class CustodyChainPolicy {

    /** Hops that must be present before a dispatch's custody can be closed. */
    public static final EnumSet<CustodyHop> REQUIRED_FOR_CLOSURE =
            EnumSet.of(CustodyHop.WAREHOUSE_STAGING, CustodyHop.DISPATCH, CustodyHop.CENTRE_RECEIPT);

    private CustodyChainPolicy() {}

    /**
     * Detect custody gaps across the recorded handovers for a dispatch.
     *
     * @param handovers    all recorded handovers for the dispatch (any order)
     * @param expectedCount the manifest item count each handover should verify (0 = do not check counts)
     * @return a list of human-readable gap descriptions; empty when the recorded chain is clean so far
     */
    public static List<String> detectGaps(List<CustodyHandover> handovers, int expectedCount) {
        List<String> gaps = new ArrayList<>();
        List<CustodyHandover> ordered = handovers.stream()
                .sorted(Comparator.comparingInt(CustodyHandover::sequenceNo)).toList();
        int previousHopOrder = -1;
        for (CustodyHandover h : ordered) {
            if (h.sealState().isCompromised()) {
                gaps.add("BROKEN_SEAL@" + h.hop() + "(" + h.sealState() + ")");
            }
            if (expectedCount > 0 && h.verifiedCount() != null && h.verifiedCount() != expectedCount) {
                gaps.add("COUNT_MISMATCH@" + h.hop() + "(expected=" + expectedCount + ",verified=" + h.verifiedCount() + ")");
            }
            if (h.hop().order() < previousHopOrder) {
                gaps.add("OUT_OF_ORDER@" + h.hop());
            }
            previousHopOrder = Math.max(previousHopOrder, h.hop().order());
        }
        return gaps;
    }

    /** Which closure-required hops are absent from the recorded chain. */
    public static List<CustodyHop> missingClosureHops(List<CustodyHandover> handovers) {
        EnumSet<CustodyHop> present = EnumSet.noneOf(CustodyHop.class);
        for (CustodyHandover h : handovers) present.add(h.hop());
        List<CustodyHop> missing = new ArrayList<>();
        for (CustodyHop required : REQUIRED_FOR_CLOSURE) if (!present.contains(required)) missing.add(required);
        return missing;
    }

    /** True when the recorded chain has no gaps and all closure-required hops are present. */
    public static boolean closable(List<CustodyHandover> handovers, int expectedCount) {
        return detectGaps(handovers, expectedCount).isEmpty() && missingClosureHops(handovers).isEmpty();
    }
}
