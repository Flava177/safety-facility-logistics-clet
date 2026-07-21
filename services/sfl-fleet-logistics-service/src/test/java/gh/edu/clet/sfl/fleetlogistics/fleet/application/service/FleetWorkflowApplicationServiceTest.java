package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.ACCRA;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.AssignWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.CloseWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.CommentOnWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.EscalateWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.HoldWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.RaiseWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.ReopenWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands.StartWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.NotificationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow.SlaEvaluationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ClosureEvidenceMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.UnauthorizedApprovalException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.SlaPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetWorkflowTestDoubles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S166-02 fleet workflow queue, transitions, SLA and audit. */
class FleetWorkflowApplicationServiceTest {

    private static final UUID EVIDENCE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private FleetWorkflowTestDoubles.InMemoryFleetWorkflowRepository workflowItems;
    private FleetWorkflowTestDoubles.MutableSlaRuleRepository slaRules;
    private FleetTestDoubles.RecordingAuditPort audit;
    private FleetTestDoubles.RecordingEventPublisher events;
    private FleetTestDoubles.RecordingNotificationPort notifications;
    private MutableClock clock;
    private FleetWorkflowApplicationService service;

    @BeforeEach
    void setUp() {
        workflowItems = new FleetWorkflowTestDoubles.InMemoryFleetWorkflowRepository();
        slaRules = new FleetWorkflowTestDoubles.MutableSlaRuleRepository().withRules(defaultSlaRule());
        clock = new MutableClock(NOW);
        audit = new FleetTestDoubles.RecordingAuditPort(clock);
        events = new FleetTestDoubles.RecordingEventPublisher();
        notifications = new FleetTestDoubles.RecordingNotificationPort();
        service = new FleetWorkflowApplicationService(workflowItems, slaRules, new FleetAccessPolicy(), audit,
                events, notifications, clock);
    }

    @Test
    @DisplayName("raising with an assignee creates accountable work, audit and notification records")
    void raise_with_assignee_creates_audited_work() {
        FleetWorkflowItem item = service.raise(raiseCommand("mechanic@clet.edu.gh"));

        assertThat(item.status()).isEqualTo(FleetWorkflowStatus.ASSIGNED);
        assertThat(item.assignee()).isEqualTo("mechanic@clet.edu.gh");
        assertThat(item.firstResponseAt()).isEqualTo(NOW);
        assertThat(workflowItems.findTransitions(item.id())).hasSize(1);
        assertThat(audit.hasRecord(AuditAction.CREATE, "FleetWorkflowItem")).isTrue();
        assertThat(notifications.sent()).contains("USER|mechanic@clet.edu.gh|"
                + NotificationPort.NotificationKind.WORK_ASSIGNED + "|" + item.workflowNumber());
    }

    @Test
    @DisplayName("assignment, hold, resume and comment preserve immutable history")
    void mutable_state_has_immutable_transition_history() {
        FleetWorkflowItem item = service.raise(raiseCommand(null));
        FleetWorkflowItem assigned = service.assign(new AssignWorkflowItem(item.id(), "mechanic@clet.edu.gh",
                "Route to maintenance", null, FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB));
        FleetWorkflowItem started = service.start(new StartWorkflowItem(assigned.id(), null,
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB));
        FleetWorkflowItem held = service.holdOrResume(new HoldWorkflowItem(started.id(), false,
                "Waiting for parts", null, FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB));
        FleetWorkflowItem resumed = service.holdOrResume(new HoldWorkflowItem(held.id(), true, null, null,
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB));
        service.comment(new CommentOnWorkflowItem(resumed.id(), "Parts arrived.",
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB));

        assertThat(resumed.status()).isEqualTo(FleetWorkflowStatus.IN_PROGRESS);
        assertThat(workflowItems.findTransitions(item.id())).hasSize(6);
        assertThat(workflowItems.findComments(item.id())).singleElement()
                .satisfies(comment -> assertThat(comment.body()).isEqualTo("Parts arrived."));
        assertThat(audit.hasRecord(AuditAction.HOLD, "FleetWorkflowItem")).isTrue();
    }

    @Test
    @DisplayName("closure requires evidence and reason, then permits privileged reopening")
    void closure_requires_evidence_and_reopen_is_privileged() {
        FleetWorkflowItem assigned = service.raise(raiseCommand("mechanic@clet.edu.gh"));

        assertThatThrownBy(() -> service.close(new CloseWorkflowItem(assigned.id(), "Resolved", null,
                null, FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB)))
                .isInstanceOf(ClosureEvidenceMissingException.class);

        FleetWorkflowItem closed = service.close(new CloseWorkflowItem(assigned.id(), "Resolved", EVIDENCE_ID,
                null, FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB));

        assertThatThrownBy(() -> service.reopen(new ReopenWorkflowItem(closed.id(), "Issue recurred", null,
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB)))
                .isInstanceOf(UnauthorizedApprovalException.class);

        FleetWorkflowItem reopened = service.reopen(new ReopenWorkflowItem(closed.id(), "Issue recurred", null,
                FleetTestDoubles.fleetManager("ACCRA"), SourceChannel.WEB));

        assertThat(reopened.status()).isEqualTo(FleetWorkflowStatus.REOPENED);
        assertThat(reopened.closureEvidenceId()).isNull();
        assertThat(audit.hasRecord(AuditAction.CLOSE, "FleetWorkflowItem")).isTrue();
        assertThat(audit.hasRecord(AuditAction.REOPEN, "FleetWorkflowItem")).isTrue();
    }

    @Test
    @DisplayName("manual escalation is privileged and publishes the workflow escalation event")
    void escalation_is_privileged_and_notifies() {
        FleetWorkflowItem item = service.raise(raiseCommand("mechanic@clet.edu.gh"));

        assertThatThrownBy(() -> service.escalate(new EscalateWorkflowItem(item.id(), "Needs manager review",
                null, FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB)))
                .isInstanceOf(UnauthorizedApprovalException.class);

        FleetWorkflowItem escalated = service.escalate(new EscalateWorkflowItem(item.id(),
                "Needs manager review", null, FleetTestDoubles.fleetManager("ACCRA"), SourceChannel.WEB));

        assertThat(escalated.status()).isEqualTo(FleetWorkflowStatus.ESCALATED);
        assertThat(escalated.escalationLevel()).isEqualTo(1);
        assertThat(events.types()).contains(FleetEventType.FLEET_WORKFLOW_ESCALATED);
        assertThat(notifications.sent()).anySatisfy(entry ->
                assertThat(entry).contains("ROLE|" + SflRole.FLEET_MANAGER + "|WORK_ESCALATED"));
    }

    @Test
    @DisplayName("the SLA sweep escalates breached live items using the runtime rule active at evaluation")
    void sla_sweep_escalates_breached_items() {
        slaRules.withRules(new SlaPolicy.SlaRule("fast-response", FleetWorkflowType.VEHICLE_DEFECT,
                WorkflowPriority.URGENT, WorkflowSeverity.CRITICAL, "ACCRA", OperatingMode.MAINTENANCE,
                Duration.ofSeconds(1), Duration.ofSeconds(1), SflRole.SFL_ADMIN));
        FleetWorkflowItem item = service.raise(raiseCommand("mechanic@clet.edu.gh"));

        clock.advance(Duration.ofSeconds(2));
        List<FleetWorkflowItem> escalated = new SlaEvaluationService(workflowItems, service, clock).evaluateOnce();

        assertThat(escalated).hasSize(1);
        FleetWorkflowItem persisted = workflowItems.findById(item.id()).orElseThrow();
        assertThat(persisted.status()).isEqualTo(FleetWorkflowStatus.ESCALATED);
        assertThat(persisted.escalationLevel()).isEqualTo(1);
        assertThat(events.types()).contains(FleetEventType.FLEET_WORKFLOW_ESCALATED);
        assertThat(notifications.sent()).anySatisfy(entry ->
                assertThat(entry).contains("ROLE|" + SflRole.SFL_ADMIN + "|WORK_ESCALATED"));
    }

    private RaiseWorkflowItem raiseCommand(String assignee) {
        return new RaiseWorkflowItem(FleetWorkflowType.VEHICLE_DEFECT, "VehicleInspection",
                "inspection-001", ACCRA.value(), "Brake defect", "Brake inspection failed.",
                WorkflowPriority.URGENT, WorkflowSeverity.CRITICAL, OperatingMode.MAINTENANCE, assignee,
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB, "idem-workflow-1");
    }

    private static SlaPolicy.SlaRule defaultSlaRule() {
        return new SlaPolicy.SlaRule("default", null, null, null, null, null,
                Duration.ofHours(1), Duration.ofHours(8), SflRole.FLEET_MANAGER);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
