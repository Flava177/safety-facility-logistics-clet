package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchManifestItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ReturnReconciliation;
import gh.edu.clet.sfl.fleetlogistics.dispatch.support.DispatchTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S171-06 return-leg reconciliation and outstanding-item escalation. */
class DispatchReturnServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private DispatchTestDoubles.InMemoryDispatchRepository repository;
    private DispatchReturnService service;
    private UUID dispatchId;
    private UUID manifestItemId;
    private UUID courierItemId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new DispatchTestDoubles.InMemoryDispatchRepository();
        var exceptionService = new DispatchExceptionService(repository, new DispatchAccessPolicy(),
                new FleetTestDoubles.RecordingAuditPort(clock), new FleetTestDoubles.RecordingEventPublisher(),
                new FleetTestDoubles.RecordingNotificationPort(), new FleetTestDoubles.FixedRuntimeConfiguration(),
                new DispatchTestDoubles.RecordingSecurityVisibilityPort(), new DispatchTestDoubles.StubOutboxAdminPort(),
                clock);
        service = new DispatchReturnService(repository, new DispatchAccessPolicy(),
                new DispatchTestDoubles.InMemoryEvidencePort(), exceptionService,
                new FleetTestDoubles.RecordingAuditPort(clock), new FleetTestDoubles.RecordingEventPublisher(), clock);
        seedDispatchWithOneManifestItem();
    }

    @Test
    @DisplayName("a matched return marks the manifest item returned and the dispatch reconciled")
    void reconcile_matched_return_marks_dispatch_reconciled() {
        ReturnReconciliation result = service.reconcile(reconcileCommand(1, 0));

        assertThat(result.matched()).isTrue();
        assertThat(repository.findDispatch(dispatchId).orElseThrow().status()).isEqualTo(Dispatch.Status.RECONCILED);
        assertThat(repository.findManifestItems(dispatchId).get(0).returnStatus())
                .isEqualTo(DispatchManifestItem.ReturnStatus.RETURNED);
    }

    @Test
    @DisplayName("reconciling a return is denied to an actor without DISPATCH_RETURN_RECONCILE")
    void reconcile_is_denied_without_return_reconcile_permission() {
        var command = new DispatchReturnService.ReconcileReturn(dispatchId, null, 1, 0, null, null,
                DispatchTestDoubles.centreManager(SITE), SourceChannel.WEB);

        assertThatThrownBy(() -> service.reconcile(command)).isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("a shortfall is a discrepancy that opens a return exception instead of reconciling the dispatch")
    void reconcile_shortfall_opens_a_discrepancy_exception() {
        ReturnReconciliation result = service.reconcile(reconcileCommand(0, 0));

        assertThat(result.matched()).isFalse();
        assertThat(result.shortfall()).isEqualTo(1);
        assertThat(repository.hasOpenException(dispatchId)).isTrue();
        assertThat(repository.findDispatch(dispatchId).orElseThrow().status()).isNotEqualTo(Dispatch.Status.RECONCILED);
    }

    @Test
    @DisplayName("escalating an outstanding item marks it outstanding and opens a return-discrepancy exception")
    void escalateOutstanding_marks_the_item_and_opens_an_exception() {
        service.escalateOutstanding(dispatchId, manifestItemId, courierItemId, DispatchTestDoubles.dispatchManager(SITE),
                SourceChannel.SYSTEM);

        assertThat(repository.findManifestItems(dispatchId).get(0).returnStatus())
                .isEqualTo(DispatchManifestItem.ReturnStatus.OUTSTANDING);
        assertThat(repository.hasOpenException(dispatchId)).isTrue();
    }

    private DispatchReturnService.ReconcileReturn reconcileCommand(int returnedCount, int brokenSeals) {
        return new DispatchReturnService.ReconcileReturn(dispatchId, null, returnedCount, brokenSeals, null, null,
                DispatchTestDoubles.dispatchManager(SITE), SourceChannel.WEB);
    }

    private void seedDispatchWithOneManifestItem() {
        dispatchId = UUID.randomUUID();
        var dispatch = new Dispatch(dispatchId, "DSP-1", SiteCode.of(SITE), "Route 1", "handler-1", "Centre 1", null,
                null, null, null, 1, List.of("SEAL-1"), Dispatch.Status.RECEIVED, NOW.minusSeconds(7200),
                NOW.minusSeconds(600), null, null, RecordMetadata.createdBy("clerk", NOW, SourceChannel.WEB,
                        "corr-test"));
        repository.saveDispatch(dispatch);

        courierItemId = UUID.randomUUID();
        repository.saveItem(new CourierItem(courierItemId, "ITM-1", SiteCode.of(SITE), CourierItem.Direction.OUTBOUND,
                CourierItem.Type.ORDINARY_MAIL, CourierItem.Sensitivity.ORDINARY, false, "Warehouse", "Centre 1",
                "Registry", "Centre Manager", null, CourierItem.Status.DISPATCHED, null, null, null, null, null,
                false, null, RecordMetadata.createdBy("clerk", NOW, SourceChannel.WEB, "corr-test")));

        manifestItemId = UUID.randomUUID();
        repository.saveManifestItem(new DispatchManifestItem(manifestItemId, dispatchId, courierItemId,
                SiteCode.of(SITE), 1, "SEAL-1", 1, DispatchManifestItem.ReturnStatus.PENDING, null, null, NOW));
    }
}
