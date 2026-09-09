package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.dispatch.support.DispatchTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
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

/** Traces: S171 accountable exception-case workflow - open, transition, SLA and escalation. */
class DispatchExceptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    private static final String SITE = "ACCRA";

    private DispatchTestDoubles.InMemoryDispatchRepository repository;
    private DispatchTestDoubles.RecordingSecurityVisibilityPort security;
    private DispatchExceptionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        repository = new DispatchTestDoubles.InMemoryDispatchRepository();
        security = new DispatchTestDoubles.RecordingSecurityVisibilityPort();
        service = new DispatchExceptionService(repository, new DispatchAccessPolicy(),
                new FleetTestDoubles.RecordingAuditPort(clock), new FleetTestDoubles.RecordingEventPublisher(),
                new FleetTestDoubles.RecordingNotificationPort(), new FleetTestDoubles.FixedRuntimeConfiguration(),
                security, new DispatchTestDoubles.StubOutboxAdminPort(), clock);
    }

    @Test
    @DisplayName("a case is opened, assigned, reviewed and approved through its normal workflow")
    void open_assign_review_and_approve_happy_path() {
        var actor = DispatchTestDoubles.dispatchController(SITE);
        DispatchExceptionCase opened = service.openCase(openCommand("SCAN_MISMATCH:1", false, actor));
        assertThat(opened.status()).isEqualTo(DispatchExceptionCase.Status.DETECTED);

        service.transition(opened.id(), "assign", "controller-1", null, actor, SourceChannel.WEB);
        service.transition(opened.id(), "review", null, null, actor, SourceChannel.WEB);
        var approved = service.transition(opened.id(), "approve", "looks fine", null,
                DispatchTestDoubles.dispatchManager(SITE), SourceChannel.WEB);

        assertThat(approved.status()).isEqualTo(DispatchExceptionCase.Status.APPROVED);
    }

    @Test
    @DisplayName("approving a case is denied to an actor without DISPATCH_EXCEPTION_APPROVE")
    void transition_approve_is_denied_without_approve_permission() {
        var controller = DispatchTestDoubles.dispatchController(SITE);
        DispatchExceptionCase opened = service.openCase(openCommand("SCAN_MISMATCH:2", false, controller));
        service.transition(opened.id(), "assign", "controller-1", null, controller, SourceChannel.WEB);
        service.transition(opened.id(), "review", null, null, controller, SourceChannel.WEB);

        assertThatThrownBy(() -> service.transition(opened.id(), "approve", "ok", null, controller, SourceChannel.WEB))
                .isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("assigning an already-assigned case is refused - assign only applies from DETECTED or REOPENED")
    void transition_assign_from_the_wrong_state_is_refused() {
        var actor = DispatchTestDoubles.dispatchController(SITE);
        DispatchExceptionCase opened = service.openCase(openCommand("SCAN_MISMATCH:3", false, actor));
        service.transition(opened.id(), "assign", "controller-1", null, actor, SourceChannel.WEB);

        assertThatThrownBy(() -> service.transition(opened.id(), "assign", "controller-2", null, actor,
                SourceChannel.WEB)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("an unknown transition action is refused")
    void transition_with_an_unknown_action_is_refused() {
        var actor = DispatchTestDoubles.dispatchController(SITE);
        DispatchExceptionCase opened = service.openCase(openCommand("SCAN_MISMATCH:4", false, actor));

        assertThatThrownBy(() -> service.transition(opened.id(), "teleport", null, null, actor, SourceChannel.WEB))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("opening a case is idempotent by (site, occurrenceKey): a repeated detection returns the open case")
    void openCase_is_idempotent_by_occurrence_key() {
        var actor = DispatchTestDoubles.dispatchController(SITE);
        DispatchExceptionCase first = service.openCase(openCommand("SCAN_MISMATCH:5", false, actor));
        DispatchExceptionCase second = service.openCase(openCommand("SCAN_MISMATCH:5", false, actor));

        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    @DisplayName("a security-relevant case surfaces to SSEMP when opened")
    void openCase_security_relevant_surfaces_to_ssemp() {
        var actor = DispatchTestDoubles.dispatchController(SITE);
        DispatchExceptionCase opened = service.openCase(openCommand("CUSTODY_GAP:1", true, actor));

        assertThat(security.surfaced()).containsExactly(opened.id());
    }

    private DispatchExceptionService.OpenCase openCommand(String occurrenceKey, boolean securityRelevant,
            gh.edu.clet.sfl.common.security.ActorContext actor) {
        return new DispatchExceptionService.OpenCase(SITE, DispatchExceptionCase.Type.SCAN_MISMATCH,
                DispatchExceptionCase.Severity.MEDIUM, securityRelevant, occurrenceKey, null, UUID.randomUUID(), null,
                null, null, List.of("RULE_1"), actor, SourceChannel.WEB);
    }
}
