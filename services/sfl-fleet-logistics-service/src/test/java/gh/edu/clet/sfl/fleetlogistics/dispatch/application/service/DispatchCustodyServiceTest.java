package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHandover;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHop;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
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

/** Traces: SRS-SFL-S171-02 chain-of-custody handovers and gap detection. */
class DispatchCustodyServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private DispatchTestDoubles.InMemoryDispatchRepository repository;
    private DispatchExceptionService exceptionService;
    private DispatchCustodyService service;
    private UUID dispatchId;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new DispatchTestDoubles.InMemoryDispatchRepository();
        exceptionService = new DispatchExceptionService(repository, new DispatchAccessPolicy(),
                new FleetTestDoubles.RecordingAuditPort(clock), new FleetTestDoubles.RecordingEventPublisher(),
                new FleetTestDoubles.RecordingNotificationPort(), new FleetTestDoubles.FixedRuntimeConfiguration(),
                new DispatchTestDoubles.RecordingSecurityVisibilityPort(), new DispatchTestDoubles.StubOutboxAdminPort(),
                clock);
        service = new DispatchCustodyService(repository, new DispatchAccessPolicy(),
                new DispatchTestDoubles.InMemoryEvidencePort(), exceptionService,
                new FleetTestDoubles.RecordingAuditPort(clock), new FleetTestDoubles.RecordingEventPublisher(), clock);
        dispatchId = seedDispatch();
    }

    @Test
    @DisplayName("an intact-seal handover is recorded and raises no custody-gap exception")
    void recordHandover_with_intact_seal_raises_no_gap() {
        CustodyHandover handover = service.recordHandover(handover(CustodyHop.WAREHOUSE_STAGING, SealState.INTACT));

        assertThat(handover.sequenceNo()).isEqualTo(1);
        assertThat(service.handovers(dispatchId, DispatchTestDoubles.dispatchController(SITE))).hasSize(1);
        assertThat(repository.hasOpenException(dispatchId)).isFalse();
    }

    @Test
    @DisplayName("recording a handover is denied to an actor without DISPATCH_CUSTODY_RECORD")
    void recordHandover_is_denied_without_custody_record_permission() {
        var mailroom = DispatchTestDoubles.mailroomOfficer(SITE);
        var command = handover(CustodyHop.WAREHOUSE_STAGING, SealState.INTACT, mailroom);

        assertThatThrownBy(() -> service.recordHandover(command)).isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("a broken-seal handover opens a security-relevant custody-gap exception")
    void recordHandover_with_broken_seal_opens_a_security_relevant_exception() {
        service.recordHandover(handover(CustodyHop.WAREHOUSE_STAGING, SealState.BROKEN));

        var gaps = service.gaps(dispatchId, DispatchTestDoubles.dispatchController(SITE));
        assertThat(gaps.gaps()).isNotEmpty();
        assertThat(gaps.closable()).isFalse();
    }

    private DispatchCustodyService.RecordHandover handover(CustodyHop hop, SealState sealState) {
        return handover(hop, sealState, DispatchTestDoubles.dispatchController(SITE));
    }

    private DispatchCustodyService.RecordHandover handover(CustodyHop hop, SealState sealState, ActorContext actor) {
        return new DispatchCustodyService.RecordHandover(dispatchId, hop, "Warehouse Clerk", "Driver", NOW,
                sealState, 1, null, null, actor, SourceChannel.WEB);
    }

    private UUID seedDispatch() {
        UUID id = UUID.randomUUID();
        var dispatch = new Dispatch(id, "DSP-1", SiteCode.of(SITE), "Route 1", "handler-1", "Centre 1", null, null,
                null, null, 1, List.of("SEAL-1"), Dispatch.Status.SEALED, null, null, null, null,
                RecordMetadata.createdBy("clerk", NOW, SourceChannel.WEB, "corr-test"));
        repository.saveDispatch(dispatch);
        return id;
    }
}
