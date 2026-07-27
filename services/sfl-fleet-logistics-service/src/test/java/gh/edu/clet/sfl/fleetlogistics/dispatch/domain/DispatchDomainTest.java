package gh.edu.clet.sfl.fleetlogistics.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHandover;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHop;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt.ReceiptOutcome;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt.VarianceType;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.CustodyChainPolicy;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.DispatchClosurePolicy;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.ReceiptVariancePolicy;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.ReturnReconciliationPolicy;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ReturnReconciliation.ReturnOutcome;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Framework-free unit tests for the S171 domain aggregates and policies. */
class DispatchDomainTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private final RecordMetadata meta = RecordMetadata.createdBy("actor", NOW, SourceChannel.WEB, "corr");

    private CourierItem item(CourierItem.Direction direction, CourierItem.Type type, CourierItem.Sensitivity s) {
        return new CourierItem(UUID.randomUUID(), "OUT-1", SiteCode.of("HQ"), direction, type, s,
                CourierItem.custodyRequired(type, s), "HQ", "Centre", "sender", "recipient", "handler",
                CourierItem.Status.RECEIVED, null, null, null, null, null, false, null, meta);
    }

    // ---- CourierItem lifecycle -------------------------------------------------------------------

    @Test
    void outbound_item_advances_through_the_full_lifecycle_and_closes_on_a_clean_outcome() {
        var i = item(CourierItem.Direction.OUTBOUND, CourierItem.Type.EXAMINATION_PAPER,
                CourierItem.Sensitivity.SECRET);
        assertThat(i.chainOfCustodyRequired()).isTrue();
        i = i.stage(meta); assertThat(i.status()).isEqualTo(CourierItem.Status.STAGED);
        i = i.dispatched(meta); assertThat(i.status()).isEqualTo(CourierItem.Status.DISPATCHED);
        i = i.inTransit(meta); assertThat(i.status()).isEqualTo(CourierItem.Status.IN_TRANSIT);
        i = i.delivered(meta); assertThat(i.status()).isEqualTo(CourierItem.Status.DELIVERED);
        i = i.returnedToOrigin(meta); assertThat(i.status()).isEqualTo(CourierItem.Status.RETURNED);
        i = i.close(meta); assertThat(i.status()).isEqualTo(CourierItem.Status.CLOSED);
    }

    @Test
    void ordinary_mail_does_not_require_chain_of_custody() {
        var i = item(CourierItem.Direction.OUTBOUND, CourierItem.Type.ORDINARY_MAIL, CourierItem.Sensitivity.ORDINARY);
        assertThat(i.chainOfCustodyRequired()).isFalse();
    }

    @Test
    void inbound_distribution_requires_a_recorded_acknowledgement_and_closes_the_item() {
        var i = item(CourierItem.Direction.INBOUND, CourierItem.Type.ORDINARY_MAIL, CourierItem.Sensitivity.ORDINARY);
        assertThatThrownBy(() -> i.distribute("clerk", NOW, null, null, meta))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("acknowledgement");
        var distributed = i.distribute("clerk", NOW, UUID.randomUUID(), "SIG-1", meta);
        assertThat(distributed.status()).isEqualTo(CourierItem.Status.DELIVERED);
    }

    @Test
    void a_closed_item_cannot_enter_exception() {
        var i = item(CourierItem.Direction.OUTBOUND, CourierItem.Type.CERTIFICATE, CourierItem.Sensitivity.CONFIDENTIAL)
                .stage(meta).dispatched(meta).delivered(meta).close(meta);
        assertThatThrownBy(() -> i.markException("late", meta)).isInstanceOf(IllegalStateException.class);
    }

    // ---- Dispatch lifecycle ----------------------------------------------------------------------

    private Dispatch draft() {
        return new Dispatch(UUID.randomUUID(), "DSP-1", SiteCode.of("HQ"), "HQ→Centre", "handler", "Centre", "exam",
                null, null, null, 0, List.of(), Dispatch.Status.DRAFT, null, null, null, null, meta);
    }

    @Test
    void a_dispatch_cannot_be_sealed_empty_or_without_seal_ids() {
        assertThatThrownBy(() -> draft().seal(meta)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> draft().updateManifest(2, List.of(), meta).seal(meta))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void a_sealed_dispatch_advances_to_reconciled_and_closes() {
        var d = draft().updateManifest(2, List.of("SEAL-1"), meta).seal(meta);
        assertThat(d.status()).isEqualTo(Dispatch.Status.SEALED);
        d = d.dispatch(NOW, meta).inTransit(meta).received(NOW, meta).reconciled(NOW, meta).close("done", meta);
        assertThat(d.status()).isEqualTo(Dispatch.Status.CLOSED);
    }

    // ---- Custody chain policy --------------------------------------------------------------------

    private CustodyHandover hop(CustodyHop hop, int seq, SealState seal, Integer count) {
        return new CustodyHandover(UUID.randomUUID(), UUID.randomUUID(), SiteCode.of("HQ"), hop, seq, "from", "to", NOW,
                seal, count, null, null, "actor", NOW, SourceChannel.WEB, "corr");
    }

    @Test
    void custody_policy_detects_broken_seal_count_mismatch_and_out_of_order_hops() {
        var broken = CustodyChainPolicy.detectGaps(List.of(hop(CustodyHop.DISPATCH, 1, SealState.BROKEN, 2)), 2);
        assertThat(broken).anyMatch(g -> g.startsWith("BROKEN_SEAL"));
        var mismatch = CustodyChainPolicy.detectGaps(List.of(hop(CustodyHop.DISPATCH, 1, SealState.INTACT, 1)), 2);
        assertThat(mismatch).anyMatch(g -> g.startsWith("COUNT_MISMATCH"));
        var outOfOrder = CustodyChainPolicy.detectGaps(
                List.of(hop(CustodyHop.CENTRE_RECEIPT, 1, SealState.INTACT, 2), hop(CustodyHop.DISPATCH, 2, SealState.INTACT, 2)), 2);
        assertThat(outOfOrder).anyMatch(g -> g.startsWith("OUT_OF_ORDER"));
    }

    @Test
    void a_clean_chain_with_all_closure_hops_is_closable() {
        var clean = List.of(hop(CustodyHop.WAREHOUSE_STAGING, 1, SealState.INTACT, 2),
                hop(CustodyHop.DISPATCH, 2, SealState.INTACT, 2), hop(CustodyHop.CENTRE_RECEIPT, 3, SealState.INTACT, 2));
        assertThat(CustodyChainPolicy.missingClosureHops(clean)).isEmpty();
        assertThat(CustodyChainPolicy.closable(clean, 2)).isTrue();
        assertThat(CustodyChainPolicy.closable(List.of(hop(CustodyHop.DISPATCH, 1, SealState.INTACT, 2)), 2)).isFalse();
    }

    // ---- Receipt variance policy -----------------------------------------------------------------

    @Test
    void receipt_variance_policy_classifies_each_deviation() {
        assertThat(ReceiptVariancePolicy.evaluate(SealState.INTACT, 2, 2, "Ada", "Ada", true).outcome())
                .isEqualTo(ReceiptOutcome.CLEAN);
        assertThat(ReceiptVariancePolicy.evaluate(SealState.BROKEN, 2, 2, "Ada", "Ada", true).type())
                .isEqualTo(VarianceType.BROKEN_SEAL);
        assertThat(ReceiptVariancePolicy.evaluate(SealState.INTACT, 3, 2, "Ada", null, true).type())
                .isEqualTo(VarianceType.SHORT_COUNT);
        assertThat(ReceiptVariancePolicy.evaluate(SealState.INTACT, 2, 3, "Ada", null, true).type())
                .isEqualTo(VarianceType.OVER_COUNT);
        assertThat(ReceiptVariancePolicy.evaluate(SealState.INTACT, 2, 2, "Bob", "Ada", true).type())
                .isEqualTo(VarianceType.WRONG_RECIPIENT);
        assertThat(ReceiptVariancePolicy.evaluate(SealState.INTACT, 2, 2, "Ada", null, false).type())
                .isEqualTo(VarianceType.MISSING_SIGNATURE);
    }

    // ---- Return reconciliation policy ------------------------------------------------------------

    @Test
    void return_policy_reports_matched_shortfall_and_extras() {
        assertThat(ReturnReconciliationPolicy.evaluate(3, 3, 0).outcome()).isEqualTo(ReturnOutcome.MATCHED);
        assertThat(ReturnReconciliationPolicy.evaluate(3, 2, 0).shortfall()).isEqualTo(1);
        assertThat(ReturnReconciliationPolicy.evaluate(3, 4, 0).extras()).isEqualTo(1);
        assertThat(ReturnReconciliationPolicy.evaluate(3, 3, 1).outcome()).isEqualTo(ReturnOutcome.DISCREPANCY);
    }

    // ---- Exception case workflow -----------------------------------------------------------------

    private DispatchExceptionCase newCase() {
        return new DispatchExceptionCase(UUID.randomUUID(), "DXC-1", SiteCode.of("HQ"), "CUSTODY_GAP:1", null,
                UUID.randomUUID(), null, null, null, DispatchExceptionCase.Type.CUSTODY_GAP,
                DispatchExceptionCase.Severity.HIGH, true, DispatchExceptionCase.Status.DETECTED, null,
                NOW.plusSeconds(3600), null, null, null, null, 0, List.of("BROKEN_SEAL"), meta);
    }

    @Test
    void exception_case_runs_the_full_evidence_gated_lifecycle() {
        var c = newCase().assign("officer", meta).review(meta).requestExplanation(meta)
                .explain("seal replaced in transit", UUID.randomUUID(), meta).review(meta)
                .decide(DispatchExceptionCase.Decision.APPROVED, "accepted", meta);
        var closed = c.close("resolved", UUID.randomUUID(), meta);
        assertThat(closed.status()).isEqualTo(DispatchExceptionCase.Status.CLOSED);
    }

    @Test
    void an_exception_cannot_close_without_explanation_decision_and_evidence() {
        var approved = newCase().assign("officer", meta).review(meta)
                .decide(DispatchExceptionCase.Decision.APPROVED, "ok", meta);
        assertThatThrownBy(() -> approved.close("done", null, meta)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evidence");
    }

    @Test
    void closure_policy_blocks_while_an_exception_is_open_or_a_custody_gap_is_unresolved() {
        assertThatThrownBy(() -> DispatchClosurePolicy.requireClosable(true, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exception");
        assertThatThrownBy(() -> DispatchClosurePolicy.requireClosable(false, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("custody gap");
    }
}
