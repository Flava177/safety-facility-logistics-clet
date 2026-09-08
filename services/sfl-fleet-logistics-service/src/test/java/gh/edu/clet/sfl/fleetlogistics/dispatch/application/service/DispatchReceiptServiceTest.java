package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
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

/** Traces: SRS-SFL-S171-03 destination receipt confirmation and variance handling. */
class DispatchReceiptServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private DispatchTestDoubles.InMemoryDispatchRepository repository;
    private DispatchReceiptService service;
    private UUID dispatchId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new DispatchTestDoubles.InMemoryDispatchRepository();
        var exceptionService = new DispatchExceptionService(repository, new DispatchAccessPolicy(),
                new FleetTestDoubles.RecordingAuditPort(clock), new FleetTestDoubles.RecordingEventPublisher(),
                new FleetTestDoubles.RecordingNotificationPort(), new FleetTestDoubles.FixedRuntimeConfiguration(),
                new DispatchTestDoubles.RecordingSecurityVisibilityPort(), new DispatchTestDoubles.StubOutboxAdminPort(),
                clock);
        service = new DispatchReceiptService(repository, new DispatchAccessPolicy(),
                new DispatchTestDoubles.InMemoryEvidencePort(), exceptionService,
                new FleetTestDoubles.RecordingAuditPort(clock), new FleetTestDoubles.RecordingEventPublisher(), clock);
        dispatchId = seedDispatch();
    }

    @Test
    @DisplayName("a matching count, intact seal and signed receipt is clean and marks the dispatch received")
    void confirmReceipt_clean_receipt_marks_dispatch_received() {
        DispatchReceipt receipt = service.confirmReceipt(confirmCommand(SealState.INTACT, 1, "Jane Doe", "RCP-1"));

        assertThat(receipt.clean()).isTrue();
        assertThat(repository.findDispatch(dispatchId).orElseThrow().status()).isEqualTo(Dispatch.Status.RECEIVED);
    }

    @Test
    @DisplayName("confirming a receipt is denied to an actor without DISPATCH_RECEIPT_CONFIRM")
    void confirmReceipt_is_denied_without_receipt_confirm_permission() {
        var command = new DispatchReceiptService.ConfirmReceipt(dispatchId, SealState.INTACT, true, 1, 1, "Jane Doe",
                null, signature(), "RCP-2", false, NOW, DispatchTestDoubles.dispatchController(SITE),
                SourceChannel.WEB);

        assertThatThrownBy(() -> service.confirmReceipt(command)).isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("a broken seal is a security-relevant variance that opens an exception and blocks a clean outcome")
    void confirmReceipt_broken_seal_is_a_security_relevant_variance() {
        DispatchReceipt receipt = service.confirmReceipt(confirmCommand(SealState.BROKEN, 1, "Jane Doe", "RCP-3"));

        assertThat(receipt.clean()).isFalse();
        assertThat(receipt.securityRelevant()).isTrue();
        assertThat(repository.hasOpenException(dispatchId)).isTrue();
    }

    @Test
    @DisplayName("replaying the same edge-captured correlation id returns the original receipt rather than a second one")
    void confirmReceipt_replay_of_the_same_capture_correlation_id_is_idempotent() {
        DispatchReceipt first = service.confirmReceipt(confirmCommand(SealState.INTACT, 1, "Jane Doe", "RCP-4"));
        DispatchReceipt replay = service.confirmReceipt(confirmCommand(SealState.INTACT, 1, "Jane Doe", "RCP-4"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(repository.findReceipts(dispatchId)).hasSize(1);
    }

    private DispatchReceiptService.ConfirmReceipt confirmCommand(SealState sealState, int verifiedCount,
            String recipientName, String captureCorrelationId) {
        return new DispatchReceiptService.ConfirmReceipt(dispatchId, sealState, true, 1, verifiedCount, recipientName,
                null, signature(), captureCorrelationId, false, NOW, DispatchTestDoubles.centreManager(SITE),
                SourceChannel.WEB);
    }

    private static EvidenceMeta signature() {
        return new EvidenceMeta("signature.png", "image/png", "storage://signature", null, null, null);
    }

    private UUID seedDispatch() {
        UUID id = UUID.randomUUID();
        var dispatch = new Dispatch(id, "DSP-1", SiteCode.of(SITE), "Route 1", "handler-1", "Centre 1", null, null,
                null, null, 1, List.of("SEAL-1"), Dispatch.Status.DISPATCHED, NOW.minusSeconds(3600), null, null,
                null, RecordMetadata.createdBy("clerk", NOW, SourceChannel.WEB, "corr-test"));
        repository.saveDispatch(dispatch);
        return id;
    }
}
