package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHandover;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHop;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    /** Why a chain is broken. Three causes, each with its own remedy. */
    public enum GapReason { BROKEN_SEAL, COUNT_MISMATCH, OUT_OF_ORDER }

    /**
     * One break in the custody chain, as structured data.
     *
     * <p>These used to be formatted strings with the structure baked into them —
     * {@code BROKEN_SEAL@TRANSIT(BROKEN)}, {@code COUNT_MISMATCH@TRANSIT(expected=12,verified=11)} —
     * which meant every consumer had to parse a wire format with a regular expression to colour a
     * row or count by reason. The client that did is deleted with this change.
     *
     * <p>{@code detail} carries the cause's own particulars: the seal state for a broken seal, the
     * expected and verified counts for a mismatch. {@code handoverId} is what lets a screen link
     * straight to the handover that caused it.
     */
    public record Gap(GapReason reason, CustodyHop hop, UUID handoverId, Map<String, Object> detail) {
        public Gap {
            detail = detail == null ? Map.of() : Map.copyOf(detail);
        }
    }

    public static List<Gap> detectGaps(List<CustodyHandover> handovers, int expectedCount) {
        List<Gap> gaps = new ArrayList<>();
        List<CustodyHandover> ordered = handovers.stream()
                .sorted(Comparator.comparingInt(CustodyHandover::sequenceNo)).toList();
        int previousHopOrder = -1;
        for (CustodyHandover h : ordered) {
            if (h.sealState().isCompromised()) {
                gaps.add(new Gap(GapReason.BROKEN_SEAL, h.hop(), h.id(), Map.of("sealState", h.sealState().name())));
            }
            if (expectedCount > 0 && h.verifiedCount() != null && h.verifiedCount() != expectedCount) {
                gaps.add(new Gap(GapReason.COUNT_MISMATCH, h.hop(), h.id(),
                        Map.of("expected", expectedCount, "verified", h.verifiedCount())));
            }
            if (h.hop().order() < previousHopOrder) {
                gaps.add(new Gap(GapReason.OUT_OF_ORDER, h.hop(), h.id(),
                        Map.of("recordedAfterHopOrder", previousHopOrder)));
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
