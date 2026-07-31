package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.maintenance.application.FacilityFaultService;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceCommands;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceConfiguration;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceEscalationService;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceEvidenceService;
import gh.edu.clet.sfl.facilities.maintenance.application.EvidenceDisposalService;
import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceVendorService;
import gh.edu.clet.sfl.facilities.maintenance.application.PreventiveMaintenanceService;
import gh.edu.clet.sfl.facilities.maintenance.application.WorkOrderApplicationService;
import gh.edu.clet.sfl.facilities.maintenance.domain.EvidenceType;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceEvidence;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceVendor;
import gh.edu.clet.sfl.facilities.maintenance.domain.PreventiveMaintenanceSchedule;
import gh.edu.clet.sfl.facilities.maintenance.domain.RetentionClass;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderType;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesMasterDataService;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilityAssetService;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.application.ReadinessApplicationService;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.support.InMemoryFacilitiesRepository;
import gh.edu.clet.sfl.facilities.support.InMemoryMaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.NotificationPort;
import gh.edu.clet.sfl.facilities.support.InMemoryReadinessRepository;
import gh.edu.clet.sfl.facilities.support.RecordingNotifications;
import gh.edu.clet.sfl.facilities.support.RecordingAuditPort;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The S153 acceptance criteria, end to end through the application services.
 *
 * <p>One nested class per requirement, and inside it one test per criterion the SRS states. They run
 * against in-memory adapters so a failure points at a rule rather than at a mapping; the
 * Testcontainers test covers persistence and the V9 migration.
 *
 * <p>The clock is mutable — {@link #clock} is reassigned rather than fixed — because half of S153 is
 * about time passing. A fixed clock cannot express "four hours later the sweep runs", which is the
 * single most important behaviour in this module.
 */
class S153MandatoryScenariosTest {

    private static final Instant NOW = Instant.parse("2026-07-31T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);

    private MutableClock clock;
    private RecordingNotifications notifications;
    private InMemoryFacilitiesRepository facilities;
    private InMemoryMaintenanceRepository maintenance;
    private InMemoryReadinessRepository readinessStore;
    private RecordingAuditPort audit;
    private TestDoubles.RecordingOutbox outbox;
    private TestDoubles.InMemoryConfiguration configuration;

    private FacilitiesMasterDataService estate;
    private FacilityAssetService assets;
    private ReadinessApplicationService readiness;
    private FacilityFaultService faults;
    private WorkOrderApplicationService workOrders;
    private MaintenanceEvidenceService evidence;
    private MaintenanceVendorService vendors;
    private PreventiveMaintenanceService preventive;
    private MaintenanceEscalationService escalation;
    private EvidenceDisposalService disposal;

    private ActorContext manager;
    private ActorContext supervisor;
    private ActorContext technician;
    private ActorContext vendorTechnician;
    private ActorContext otherVendorTechnician;
    private ActorContext requester;
    private ActorContext auditor;
    private ActorContext system;

    private FacilityRoom hall;
    private FacilityAsset generator;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        facilities = new InMemoryFacilitiesRepository();
        maintenance = new InMemoryMaintenanceRepository();
        readinessStore = new InMemoryReadinessRepository();
        audit = new RecordingAuditPort(NOW);
        outbox = new TestDoubles.RecordingOutbox();
        configuration = new TestDoubles.InMemoryConfiguration();
        TestDoubles.InMemoryIdempotency idempotency = new TestDoubles.InMemoryIdempotency();
        FacilitiesAuthorization authorization = new FacilitiesAuthorization(audit);
        MaintenanceConfiguration maintenanceConfiguration = new MaintenanceConfiguration(configuration);

        estate = new FacilitiesMasterDataService(facilities, outbox, audit, idempotency, authorization, clock);
        readiness = new ReadinessApplicationService(readinessStore, facilities, outbox, audit, idempotency,
                authorization, clock);
        assets = new FacilityAssetService(facilities, outbox, audit, idempotency, authorization, readiness,
                clock);
        faults = new FacilityFaultService(maintenance, facilities, readiness, maintenanceConfiguration,
                authorization, audit, idempotency, outbox, clock);
        workOrders = new WorkOrderApplicationService(maintenance, facilities, faults,
                maintenanceConfiguration, authorization, audit, idempotency, outbox, clock);
        evidence = new MaintenanceEvidenceService(maintenance, workOrders, authorization, audit, idempotency,
                outbox, clock);
        vendors = new MaintenanceVendorService(maintenance, authorization, audit, idempotency, outbox, clock);
        preventive = new PreventiveMaintenanceService(maintenance, facilities, maintenanceConfiguration,
                authorization, audit, idempotency, outbox, clock);
        notifications = new RecordingNotifications();
        escalation = new MaintenanceEscalationService(maintenance, maintenanceConfiguration, faults,
                workOrders, notifications, clock);
        disposal = new EvidenceDisposalService(maintenance, audit, outbox, clock);

        manager = TestDoubles.actor("manager", Set.of(SflRole.FACILITIES_MANAGER), "MAIN");
        supervisor = TestDoubles.actor("supervisor", Set.of(SflRole.IFIMP_MAINTENANCE_SUPERVISOR), "MAIN");
        technician = TestDoubles.actor("technician", Set.of(SflRole.IFIMP_TECHNICIAN), "MAIN");
        vendorTechnician = TestDoubles.actor("acme.tech", Set.of(SflRole.VENDOR_TECHNICIAN), "MAIN");
        otherVendorTechnician = TestDoubles.actor("other.tech", Set.of(SflRole.VENDOR_TECHNICIAN), "MAIN");
        requester = TestDoubles.actor("lecturer", Set.of(SflRole.IFIMP_REQUESTER), "MAIN");
        auditor = TestDoubles.actor("auditor", Set.of(SflRole.AUDITOR), "MAIN");
        system = TestDoubles.actor("system", Set.of(SflRole.SFL_ADMIN), "*", "MAIN");

        Site site = estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "CLET Headquarters", null,
                manager, SourceChannel.WEB, null));
        Building building = estate.createBuilding(new FacilitiesCommands.CreateBuilding(site.id(), "LAW",
                "Law Block", null, manager, SourceChannel.WEB, null));
        FacilityFloor floor = estate.createFloor(new FacilitiesCommands.CreateFloor(building.id(), "GF",
                "Ground floor", 0, manager, SourceChannel.WEB, null));
        hall = estate.createRoom(new FacilitiesCommands.CreateRoom(floor.id(), "HALL-A", "Examination Hall A",
                SpaceType.EXAMINATION_HALL, 200, null, null, true, true, manager, SourceChannel.WEB, null));
        generator = assets.register(new FacilitiesCommands.RegisterAsset("MAIN", "GEN-01",
                "Standby generator", AssetCategory.GENERATOR, AssetCriticality.CRITICAL, hall.id(), null,
                null, null, null, null, null, 90, null, null, null, manager, SourceChannel.WEB, null));
    }

    // =========================================================================================

    @Nested
    @DisplayName("S153-01. Maintain CMMS operational records")
    class OperationalRecords {

        @Test
        void a_reported_fault_carries_a_unique_number_provenance_and_an_audit_trail() {
            FacilityFault fault = report(FaultPriority.MEDIUM);

            assertThat(fault.faultNumber()).isEqualTo("FLT-MAIN-000001");
            assertThat(fault.status()).isEqualTo(FacilityFaultStatus.REPORTED);
            assertThat(fault.metadata().createdBy()).isEqualTo("manager");
            assertThat(fault.metadata().version()).isZero();
            assertThat(fault.metadata().sourceChannel()).isEqualTo(SourceChannel.WEB);
            assertThat(audit.actions()).contains(AuditAction.FAULT_REPORTED);
            assertThat(outbox.published("sfl.ifimp.facility-fault-reported.v1")).isTrue();
        }

        @Test
        void fault_numbers_do_not_repeat() {
            assertThat(List.of(report(FaultPriority.LOW).faultNumber(),
                    report(FaultPriority.LOW).faultNumber(),
                    report(FaultPriority.LOW).faultNumber()))
                    .containsExactly("FLT-MAIN-000001", "FLT-MAIN-000002", "FLT-MAIN-000003");
        }

        @Test
        void a_fault_must_be_somewhere() {
            assertThatThrownBy(() -> faults.report(new MaintenanceCommands.ReportFault("MAIN", null, null,
                    null, "Something is wrong", "Somewhere", null, FaultPriority.LOW, manager,
                    SourceChannel.WEB, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("either a room or a location code");
        }

        /**
         * The defect this round exists to fix. The pre-S152 service returned every fault at every site
         * to any caller, through a {@code findAll()} with no permission check and no scope filter.
         */
        @Test
        void an_actor_sees_only_the_sites_they_hold() {
            report(FaultPriority.LOW);
            ActorContext kumasi = TestDoubles.actor("kumasi.manager", Set.of(SflRole.FACILITIES_MANAGER),
                    "KUMASI");

            assertThat(faults.search(null, null, null, null, 50, kumasi, SourceChannel.WEB)).isEmpty();
            assertThat(faults.search(null, null, null, null, 50, manager, SourceChannel.WEB)).hasSize(1);
        }

        @Test
        void a_requester_reads_only_the_faults_they_reported() {
            FacilityFault mine = faults.report(new MaintenanceCommands.ReportFault("MAIN", hall.id(), null,
                    null, "Projector will not start", "No power light", null, FaultPriority.LOW, requester,
                    SourceChannel.WEB, null, null));
            report(FaultPriority.LOW);

            assertThat(faults.search(null, null, null, null, 50, requester, SourceChannel.WEB))
                    .extracting(FacilityFault::id)
                    .containsExactly(mine.id());
        }

        @Test
        void a_requester_asking_for_somebody_elses_fault_is_refused_and_the_denial_is_audited() {
            FacilityFault other = report(FaultPriority.LOW);

            assertThatThrownBy(() -> faults.findById(other.id(), requester, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
            assertThat(audit.actions()).contains(AuditAction.AUTHORIZATION_DENIED);
        }

        @Test
        void reporting_twice_with_one_idempotency_key_produces_one_fault() {
            MaintenanceCommands.ReportFault command = new MaintenanceCommands.ReportFault("MAIN", hall.id(),
                    null, null, "Water ingress", "Ceiling void", null, FaultPriority.HIGH, manager,
                    SourceChannel.WEB, "key-1", "payload");

            FacilityFault first = faults.report(command);
            FacilityFault second = faults.report(command);

            assertThat(second.id()).isEqualTo(first.id());
        }
    }

    // =========================================================================================

    @Nested
    @DisplayName("S153-02. Execute CMMS workflow")
    class Workflow {

        @Test
        void triage_computes_the_sla_from_the_configured_rule() {
            configuration.set("maintenance.sla.resolution.high", "PT6H");
            FacilityFault fault = triage(report(FaultPriority.HIGH), FaultPriority.HIGH);

            assertThat(fault.slaDueAt()).isEqualTo(NOW.plus(Duration.ofHours(6)));
            assertThat(fault.status()).isEqualTo(FacilityFaultStatus.TRIAGED);
        }

        /**
         * SRS-SFL-S153-02 lists operating mode among the SLA inputs, and this is why it is there: an
         * examination is running, so the same fault has to be dealt with in half the time.
         */
        @Test
        void examination_mode_compresses_the_sla() {
            configuration.set("maintenance.sla.resolution.high", "PT8H");
            configuration.set("maintenance.sla.examination-factor", "0.25");
            estate.changeOperatingMode(new FacilitiesCommands.ChangeOperatingMode(
                    facilities.findSiteByCode("MAIN").orElseThrow().id(), OperatingMode.EXAMINATION,
                    "Semester examinations", TestDoubles.actor("centre", Set.of(SflRole.CENTRE_MANAGER),
                            "MAIN"), SourceChannel.WEB));

            FacilityFault fault = triage(report(FaultPriority.HIGH), FaultPriority.HIGH);

            assertThat(fault.slaDueAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        }

        @Test
        void the_workflow_runs_from_report_to_closure() {
            FacilityFault fault = triage(report(FaultPriority.MEDIUM), FaultPriority.MEDIUM);
            WorkOrder order = createWorkOrder(fault);
            assertThat(order.status()).isEqualTo(WorkOrderStatus.OPEN);
            assertThat(order.workOrderNumber()).isEqualTo("WO-MAIN-000001");

            order = assign(order, "technician");
            assertThat(order.status()).isEqualTo(WorkOrderStatus.ASSIGNED);

            order = transition(order, MaintenanceCommands.TransitionWorkOrder.Transition.START, null);
            assertThat(order.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);

            order = transition(order, MaintenanceCommands.TransitionWorkOrder.Transition.COMPLETE, "Done");
            assertThat(order.status()).isEqualTo(WorkOrderStatus.COMPLETED);

            order = close(order, "Verified on site");
            assertThat(order.status()).isEqualTo(WorkOrderStatus.CLOSED);
            assertThat(maintenance.findFault(fault.id()).orElseThrow().status())
                    .isEqualTo(FacilityFaultStatus.RESOLVED);
        }

        @Test
        void a_hold_records_its_reason_and_accumulates_held_time() {
            WorkOrder order = assign(createWorkOrder(triage(report(FaultPriority.MEDIUM),
                    FaultPriority.MEDIUM)), "technician");
            order = transition(order, MaintenanceCommands.TransitionWorkOrder.Transition.HOLD,
                    "Waiting on a replacement ballast");
            assertThat(order.status()).isEqualTo(WorkOrderStatus.ON_HOLD);
            assertThat(order.holdReason()).isEqualTo("Waiting on a replacement ballast");

            clock.advance(Duration.ofHours(3));
            order = assign(order, "technician");

            assertThat(order.status()).isEqualTo(WorkOrderStatus.ASSIGNED);
            assertThat(order.holdReason()).isNull();
            assertThat(order.totalHeldSeconds()).isEqualTo(Duration.ofHours(3).getSeconds());
        }

        @Test
        void a_hold_does_not_stop_the_sla_clock() {
            configuration.set("maintenance.sla.resolution.medium", "PT2H");
            WorkOrder order = assign(createWorkOrder(triage(report(FaultPriority.MEDIUM),
                    FaultPriority.MEDIUM)), "technician");
            Instant due = order.slaDueAt();
            order = transition(order, MaintenanceCommands.TransitionWorkOrder.Transition.HOLD, "No part");

            clock.advance(Duration.ofHours(5));

            assertThat(maintenance.findWorkOrder(order.id()).orElseThrow().slaDueAt()).isEqualTo(due);
            assertThat(maintenance.findWorkOrder(order.id()).orElseThrow().isOverdue(clock.instant())).isTrue();
        }

        @Test
        void the_state_machine_refuses_a_move_it_does_not_have() {
            WorkOrder order = createWorkOrder(triage(report(FaultPriority.LOW), FaultPriority.LOW));

            assertThatThrownBy(() -> transition(order,
                    MaintenanceCommands.TransitionWorkOrder.Transition.COMPLETE, "Skipping ahead"))
                    .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class)
                    .hasMessageContaining("cannot move from OPEN to COMPLETED");
        }

        @Test
        void a_stale_write_is_refused_rather_than_silently_applied() {
            FacilityFault fault = report(FaultPriority.LOW);
            triage(fault, FaultPriority.LOW);

            assertThatThrownBy(() -> faults.triage(new MaintenanceCommands.TriageFault(fault.id(),
                    FaultPriority.HIGH, "Second officer", 0L, supervisor, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.VersionConflictException.class);
        }

        @Test
        void cancelling_needs_a_reason_and_is_terminal() {
            WorkOrder order = createWorkOrder(triage(report(FaultPriority.LOW), FaultPriority.LOW));
            WorkOrder cancelled = workOrders.cancel(new MaintenanceCommands.CancelWorkOrder(order.id(),
                    "Duplicate of WO-MAIN-000004", null, manager, SourceChannel.WEB));

            assertThat(cancelled.status()).isEqualTo(WorkOrderStatus.CANCELLED);
            assertThat(cancelled.cancellationReason()).isEqualTo("Duplicate of WO-MAIN-000004");
            assertThatThrownBy(() -> assign(cancelled, "technician"))
                    .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class);
        }

        /** SRS-SFL-S153-02: "Only authorised roles may approve, override, cancel or reopen." */
        @Test
        void a_technician_cannot_reopen_work_they_completed() {
            WorkOrder order = transition(assign(createWorkOrder(triage(report(FaultPriority.LOW),
                    FaultPriority.LOW)), "technician"),
                    MaintenanceCommands.TransitionWorkOrder.Transition.COMPLETE, "Finished");

            assertThatThrownBy(() -> workOrders.transition(new MaintenanceCommands.TransitionWorkOrder(
                    order.id(), MaintenanceCommands.TransitionWorkOrder.Transition.REOPEN, "Changed my mind",
                    null, technician, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedApprovalException.class);
        }
    }

    // =========================================================================================

    @Nested
    @DisplayName("S153-02. SLA escalation")
    class Escalation {

        @Test
        void nothing_escalates_while_inside_the_sla() {
            configuration.set("maintenance.sla.resolution.high", "PT4H");
            createWorkOrder(triage(report(FaultPriority.HIGH), FaultPriority.HIGH));

            clock.advance(Duration.ofHours(1));

            assertThat(escalation.sweep(system).total()).isZero();
        }

        @Test
        void an_item_escalates_one_level_the_moment_it_is_late() {
            configuration.set("maintenance.sla.resolution.high", "PT4H");
            WorkOrder order = createWorkOrder(triage(report(FaultPriority.HIGH), FaultPriority.HIGH));

            clock.advance(Duration.ofHours(5));
            MaintenanceEscalationService.EscalationSweep sweep = escalation.sweep(system);

            assertThat(sweep.workOrdersEscalated()).isEqualTo(1);
            assertThat(maintenance.findWorkOrder(order.id()).orElseThrow().escalationLevel()).isEqualTo(1);
            assertThat(outbox.published("sfl.ifimp.work-order-escalated.v1")).isTrue();
            assertThat(audit.actions()).contains(AuditAction.WORK_ORDER_ESCALATED);

            // The half of SRS-SFL-S153-02 that was missing. "Escalates the item AND notifies the
            // configured role" — the level moving is the first half, and for three passes the
            // assertions stopped there while the event went to an outbox nothing drained.
            WorkOrder escalated = maintenance.findWorkOrder(order.id()).orElseThrow();
            assertThat(notifications.about(escalated.workOrderNumber(),
                    NotificationPort.NotificationKind.WORK_ESCALATED))
                    .singleElement()
                    .satisfies(sent -> assertThat(sent.context()).containsEntry("escalationLevel", "1"));

            // Work nobody ever started, now past both deadlines, breaches both — and they are two
            // different facts about it, so they are two notifications rather than one merged one.
            assertThat(notifications.about(escalated.workOrderNumber(),
                    NotificationPort.NotificationKind.RESPONSE_OVERDUE)).hasSize(1);
        }

        @Test
        void nobody_picking_the_work_up_is_its_own_breach_with_its_own_recipient() {
            // maintenance.sla.response.* has been read, stored and exposed since S153 shipped and
            // nothing used it: only the resolution deadline escalated, so "nobody has started this"
            // and "nobody has finished this" were the same event. They are not the same event.
            configuration.set("maintenance.sla.response.high", "PT30M");
            configuration.set("maintenance.sla.resolution.high", "PT8H");
            WorkOrder order = createWorkOrder(triage(report(FaultPriority.HIGH), FaultPriority.HIGH));

            clock.advance(Duration.ofHours(1));
            MaintenanceEscalationService.EscalationSweep sweep = escalation.sweep(system);

            // Past the response deadline, nowhere near the resolution one.
            assertThat(sweep.responseBreaches()).isEqualTo(1);
            assertThat(sweep.workOrdersEscalated()).isZero();
            assertThat(notifications.about(order.workOrderNumber()))
                    .singleElement()
                    .satisfies(sent -> {
                        assertThat(sent.kind()).isEqualTo(NotificationPort.NotificationKind.RESPONSE_OVERDUE);
                        assertThat(sent.recipient()).isEqualTo(SflRole.IFIMP_MAINTENANCE_SUPERVISOR.name());
                    });

            // Idempotent: the sweep is at-least-once, and re-raising every fifteen minutes for as long
            // as a job sits untouched is exactly how an escalation gets muted.
            notifications.clear();
            clock.advance(Duration.ofHours(1));
            assertThat(escalation.sweep(system).responseBreaches()).isZero();
            assertThat(notifications.sent()).isEmpty();
        }

        @Test
        void starting_the_work_answers_the_response_deadline() {
            configuration.set("maintenance.sla.response.high", "PT30M");
            configuration.set("maintenance.sla.resolution.high", "PT8H");
            WorkOrder order = createWorkOrder(triage(report(FaultPriority.HIGH), FaultPriority.HIGH));
            // A work order has to be assigned before it can start; that is the state machine, and the
            // point of the response deadline is that assigning alone does not answer it.
            workOrders.assign(new MaintenanceCommands.AssignWorkOrder(order.id(), "technician", null, null,
                    supervisor, SourceChannel.WEB));
            workOrders.transition(new MaintenanceCommands.TransitionWorkOrder(order.id(),
                    MaintenanceCommands.TransitionWorkOrder.Transition.START, null, null, technician,
                    SourceChannel.WEB));

            clock.advance(Duration.ofHours(1));

            // Someone picked it up, so there is nothing to chase — even though the deadline has passed.
            assertThat(escalation.sweep(system).responseBreaches()).isZero();
            assertThat(notifications.sent()).isEmpty();
        }

        @Test
        void an_escalation_on_unassigned_work_goes_to_the_supervisor_rather_than_nowhere() {
            // The case most likely to be overdue is the one nobody has picked up, so it is the one that
            // must not silently have no recipient.
            configuration.set("maintenance.sla.resolution.high", "PT4H");
            WorkOrder order = createWorkOrder(triage(report(FaultPriority.HIGH), FaultPriority.HIGH));
            assertThat(order.assignedTo()).isNull();

            clock.advance(Duration.ofHours(5));
            escalation.sweep(system);

            WorkOrder escalated = maintenance.findWorkOrder(order.id()).orElseThrow();
            assertThat(notifications.about(escalated.workOrderNumber(),
                    NotificationPort.NotificationKind.WORK_ESCALATED))
                    .singleElement()
                    .satisfies(sent -> {
                        assertThat(sent.recipientType()).isEqualTo("ROLE");
                        assertThat(sent.recipient()).isEqualTo(SflRole.IFIMP_MAINTENANCE_SUPERVISOR.name());
                    });
        }

        @Test
        void it_climbs_one_level_per_interval_and_stops_at_the_ceiling() {
            configuration.set("maintenance.sla.resolution.high", "PT1H");
            configuration.set("maintenance.escalation.interval", "PT1H");
            configuration.set("maintenance.escalation.max-level", "2");
            WorkOrder order = createWorkOrder(triage(report(FaultPriority.HIGH), FaultPriority.HIGH));

            clock.advance(Duration.ofHours(2));
            escalation.sweep(system);
            assertThat(maintenance.findWorkOrder(order.id()).orElseThrow().escalationLevel()).isEqualTo(2);

            clock.advance(Duration.ofHours(10));
            escalation.sweep(system);
            assertThat(maintenance.findWorkOrder(order.id()).orElseThrow().escalationLevel()).isEqualTo(2);
        }

        /**
         * The scheduler is at-least-once. A sweep that escalated twice would notify the same manager
         * twice, which is how people learn to ignore escalations.
         */
        @Test
        void running_the_sweep_twice_escalates_nothing_twice() {
            configuration.set("maintenance.sla.resolution.high", "PT1H");
            createWorkOrder(triage(report(FaultPriority.HIGH), FaultPriority.HIGH));
            clock.advance(Duration.ofHours(2));

            assertThat(escalation.sweep(system).total()).isEqualTo(1);
            assertThat(escalation.sweep(system).total()).isZero();
        }

        /**
         * SRS-SFL-S153-02: "Escalation rules must be evaluated using the runtime configuration active
         * at the time of evaluation." A rule tightened between two sweeps applies at the second.
         */
        @Test
        void a_configuration_change_applies_at_the_next_sweep() {
            configuration.set("maintenance.sla.resolution.high", "PT1H");
            configuration.set("maintenance.escalation.interval", "PT8H");
            WorkOrder order = createWorkOrder(triage(report(FaultPriority.HIGH), FaultPriority.HIGH));

            clock.advance(Duration.ofHours(3));
            escalation.sweep(system);
            assertThat(maintenance.findWorkOrder(order.id()).orElseThrow().escalationLevel()).isEqualTo(1);

            // The centre tightens the ladder. The two hours already overdue now buy two more levels.
            configuration.set("maintenance.escalation.interval", "PT1H");
            escalation.sweep(system);

            assertThat(maintenance.findWorkOrder(order.id()).orElseThrow().escalationLevel()).isEqualTo(3);
        }

        @Test
        void a_completed_order_waiting_on_a_verifier_is_not_escalated() {
            configuration.set("maintenance.sla.resolution.high", "PT1H");
            WorkOrder order = transition(assign(createWorkOrder(triage(report(FaultPriority.HIGH),
                    FaultPriority.HIGH)), "technician"),
                    MaintenanceCommands.TransitionWorkOrder.Transition.COMPLETE, "Done");
            assertThat(order.status()).isEqualTo(WorkOrderStatus.COMPLETED);

            clock.advance(Duration.ofHours(6));

            assertThat(escalation.sweep(system).workOrdersEscalated()).isZero();
        }
    }

    // =========================================================================================

    @Nested
    @DisplayName("S153. A fault blocks the space it is in")
    class FaultReadiness {

        @Test
        void a_critical_fault_blocks_its_hall() {
            FacilityFault fault = report(FaultPriority.CRITICAL);

            List<ReadinessBlocker> open = readinessStore.findOpenBlockers(hall.id());
            assertThat(open).hasSize(1);
            assertThat(open.get(0).severity()).isEqualTo(BlockerSeverity.CRITICAL);
            assertThat(open.get(0).source()).isEqualTo(BlockerSource.WORK_ORDER);
            assertThat(open.get(0).description()).contains(fault.faultNumber());
            assertThat(facilities.findRoom(hall.id()).orElseThrow().readinessStatus())
                    .isEqualTo(LocationReadinessStatus.BLOCKED);
        }

        @Test
        void a_low_priority_fault_does_not() {
            report(FaultPriority.LOW);

            assertThat(readinessStore.findOpenBlockers(hall.id())).isEmpty();
        }

        @Test
        void the_threshold_is_configurable() {
            configuration.set("maintenance.readiness.blocker-threshold", "LOW");
            report(FaultPriority.LOW);

            assertThat(readinessStore.findOpenBlockers(hall.id())).hasSize(1);
            assertThat(readinessStore.findOpenBlockers(hall.id()).get(0).severity())
                    .isEqualTo(BlockerSeverity.ADVISORY);
        }

        @Test
        void triage_raising_the_priority_raises_the_blocker() {
            FacilityFault fault = report(FaultPriority.LOW);
            assertThat(readinessStore.findOpenBlockers(hall.id())).isEmpty();

            triage(fault, FaultPriority.CRITICAL);

            assertThat(readinessStore.findOpenBlockers(hall.id())).hasSize(1);
        }

        @Test
        void closing_the_work_order_resolves_the_blocker_and_returns_the_hall() {
            FacilityFault fault = triage(report(FaultPriority.CRITICAL), FaultPriority.CRITICAL);
            assertThat(facilities.findRoom(hall.id()).orElseThrow().readinessStatus())
                    .isEqualTo(LocationReadinessStatus.BLOCKED);

            WorkOrder order = assign(createWorkOrder(fault), "technician");
            order = transition(order, MaintenanceCommands.TransitionWorkOrder.Transition.START, null);
            attachEvidence(order);
            close(order, "Fire door rehung and tested");

            assertThat(readinessStore.findOpenBlockers(hall.id())).isEmpty();
            assertThat(facilities.findRoom(hall.id()).orElseThrow().readinessStatus())
                    .isNotEqualTo(LocationReadinessStatus.BLOCKED);
        }

        @Test
        void rejecting_a_fault_also_clears_its_blocker() {
            FacilityFault fault = report(FaultPriority.CRITICAL);
            assertThat(readinessStore.findOpenBlockers(hall.id())).hasSize(1);

            faults.dismiss(new MaintenanceCommands.DismissFault(fault.id(), FacilityFaultStatus.REJECTED,
                    "Reported in error", null, null, manager, SourceChannel.WEB));

            assertThat(readinessStore.findOpenBlockers(hall.id())).isEmpty();
        }

        @Test
        void a_fault_with_no_room_blocks_nothing() {
            faults.report(new MaintenanceCommands.ReportFault("MAIN", null, "CAR-PARK-B", null,
                    "Barrier stuck", "Will not lift", null, FaultPriority.CRITICAL, manager,
                    SourceChannel.WEB, null, null));

            assertThat(readinessStore.findOpenBlockers(hall.id())).isEmpty();
        }
    }

    // =========================================================================================

    @Nested
    @DisplayName("S153-03. Closure evidence and audit")
    class Evidence {

        @Test
        void closure_is_refused_without_the_required_evidence() {
            WorkOrder order = readyToClose(FaultPriority.HIGH);

            assertThatThrownBy(() -> close(order, "Fixed"))
                    .isInstanceOf(FacilitiesException.ClosureEvidenceMissingException.class)
                    .hasMessageContaining("1 item(s) required, 0 attached");
        }

        @Test
        void retention_elapsing_disposes_of_the_reference_and_keeps_the_record() {
            WorkOrder order = readyToClose(FaultPriority.HIGH);
            MaintenanceEvidence attached = attachEvidence(order);
            close(order, "Fixed");

            // OPERATIONAL retention is one year. A day short of it, nothing happens.
            clock.advance(Duration.ofDays(364));
            assertThat(disposal.sweep(system)).isZero();
            assertThat(maintenance.findEvidence(attached.id()).orElseThrow().fileReference()).isNotNull();

            clock.advance(Duration.ofDays(2));
            assertThat(disposal.sweep(system)).isEqualTo(1);

            MaintenanceEvidence disposed = maintenance.findEvidence(attached.id()).orElseThrow();
            // The pointer is gone; everything needed to prove what was destroyed, and why, remains.
            assertThat(disposed.fileReference()).isNull();
            assertThat(disposed.disposedAt()).isNotNull();
            assertThat(disposed.disposalReason()).contains("OPERATIONAL");
            assertThat(disposed.contentHash()).isEqualTo(attached.contentHash());
            assertThat(disposed.uploadedBy()).isEqualTo(attached.uploadedBy());
            assertThat(audit.actions()).contains(AuditAction.EVIDENCE_DISPOSED);
            assertThat(outbox.published("sfl.ifimp.maintenance-evidence-disposed.v1")).isTrue();

            // Idempotent: a second sweep finds nothing left to do.
            assertThat(disposal.sweep(system)).isZero();
        }

        @Test
        void a_legal_hold_outlives_the_retention_period() {
            WorkOrder order = readyToClose(FaultPriority.HIGH);
            MaintenanceEvidence attached = attachEvidence(order);
            close(order, "Fixed");
            evidence.setLegalHold(new MaintenanceCommands.SetLegalHold(attached.id(), true,
                    "Held pending an investigation", auditor, SourceChannel.WEB));

            clock.advance(Duration.ofDays(400));

            // Well past a one-year retention, and untouched — which is the whole point of a hold.
            assertThat(disposal.sweep(system)).isZero();
            assertThat(maintenance.findEvidence(attached.id()).orElseThrow().fileReference()).isNotNull();
        }

        @Test
        void closure_is_allowed_once_the_evidence_is_attached() {
            WorkOrder order = readyToClose(FaultPriority.HIGH);
            attachEvidence(order);

            assertThat(close(order, "Fixed").status()).isEqualTo(WorkOrderStatus.CLOSED);
        }

        @Test
        void a_low_priority_order_needs_none() {
            WorkOrder order = readyToClose(FaultPriority.LOW);

            assertThat(order.evidenceRequired()).isZero();
            assertThat(close(order, "Fixed").status()).isEqualTo(WorkOrderStatus.CLOSED);
        }

        /**
         * The rule that applied when the work was raised is the rule the assignee is held to. A
         * requirement tightened mid-job must not strand somebody who has already finished.
         */
        @Test
        void tightening_the_rule_does_not_apply_to_work_already_raised() {
            WorkOrder order = readyToClose(FaultPriority.LOW);
            configuration.set("maintenance.closure.evidence-threshold", "LOW");
            configuration.set("maintenance.closure.evidence-count", "3");

            assertThat(close(order, "Fixed").status()).isEqualTo(WorkOrderStatus.CLOSED);
        }

        @Test
        void closure_is_refused_without_a_reason() {
            WorkOrder order = readyToClose(FaultPriority.LOW);

            assertThatThrownBy(() -> close(order, "  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void evidence_without_a_retention_class_is_refused() {
            WorkOrder order = readyToClose(FaultPriority.HIGH);

            assertThatThrownBy(() -> evidence.attach(new MaintenanceCommands.AttachEvidence(order.id(),
                    EvidenceType.AFTER_PHOTO, "s3://evidence/after.jpg", "after.jpg", "image/jpeg", 1024L,
                    "a".repeat(64), null, null, technician, SourceChannel.MOBILE, null, null)))
                    .isInstanceOf(FacilitiesException.RetentionClassMissingException.class);
        }

        @Test
        void an_invoice_does_not_satisfy_the_closure_requirement() {
            WorkOrder order = readyToClose(FaultPriority.HIGH);
            evidence.attach(new MaintenanceCommands.AttachEvidence(order.id(), EvidenceType.INVOICE,
                    "s3://evidence/invoice.pdf", "invoice.pdf", "application/pdf", 2048L, "b".repeat(64),
                    RetentionClass.OPERATIONAL, null, technician, SourceChannel.MOBILE, null, null));

            assertThatThrownBy(() -> close(order, "Fixed"))
                    .isInstanceOf(FacilitiesException.ClosureEvidenceMissingException.class);
        }

        @Test
        void export_without_a_reason_is_refused_and_the_attempt_is_audited() {
            MaintenanceEvidence item = attachEvidence(readyToClose(FaultPriority.HIGH));

            assertThatThrownBy(() -> evidence.export(new MaintenanceCommands.ExportEvidence(item.id(), "  ",
                    "compliance@clet.gov.gh", auditor, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.ExportNotApprovedException.class);
            assertThat(audit.actions()).contains(AuditAction.AUTHORIZATION_DENIED);
        }

        @Test
        void an_approved_export_is_audited_with_its_reason_and_recipient() {
            MaintenanceEvidence item = attachEvidence(readyToClose(FaultPriority.HIGH));

            MaintenanceEvidenceService.ExportGrant grant = evidence.export(
                    new MaintenanceCommands.ExportEvidence(item.id(), "Audit query AQ-2026-11",
                            "compliance@clet.gov.gh", auditor, SourceChannel.WEB));

            assertThat(grant.reason()).isEqualTo("Audit query AQ-2026-11");
            assertThat(grant.recipient()).isEqualTo("compliance@clet.gov.gh");
            assertThat(audit.actions()).contains(AuditAction.EVIDENCE_EXPORTED);
        }

        @Test
        void an_operational_role_cannot_export() {
            MaintenanceEvidence item = attachEvidence(readyToClose(FaultPriority.HIGH));

            assertThatThrownBy(() -> evidence.export(new MaintenanceCommands.ExportEvidence(item.id(),
                    "Sending to the vendor", "vendor@example.com", manager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        void a_legal_hold_suspends_disposal_without_losing_the_classification() {
            MaintenanceEvidence item = attachEvidence(readyToClose(FaultPriority.HIGH));
            assertThat(item.disposalEligibleFrom()).isNotNull();

            MaintenanceEvidence held = evidence.setLegalHold(new MaintenanceCommands.SetLegalHold(item.id(),
                    true, "Litigation hold LH-4", auditor, SourceChannel.WEB));

            assertThat(held.legalHold()).isTrue();
            assertThat(held.retentionClass()).isEqualTo(item.retentionClass());
            assertThat(held.disposalEligibleFrom()).isNull();
        }
    }

    // =========================================================================================

    @Nested
    @DisplayName("S153. Vendor scope")
    class VendorScope {

        @Test
        void a_vendor_technician_sees_only_the_work_assigned_to_them() {
            WorkOrder mine = assign(createWorkOrder(triage(report(FaultPriority.LOW), FaultPriority.LOW)),
                    "acme.tech");
            assign(createWorkOrder(triage(report(FaultPriority.LOW), FaultPriority.LOW)), "other.tech");

            assertThat(workOrders.search(null, null, null, null, null, null, null, 50, vendorTechnician,
                    SourceChannel.MOBILE))
                    .extracting(WorkOrder::id)
                    .containsExactly(mine.id());
        }

        @Test
        void a_vendor_technician_cannot_open_somebody_elses_work_order_by_id() {
            WorkOrder theirs = assign(createWorkOrder(triage(report(FaultPriority.LOW), FaultPriority.LOW)),
                    "other.tech");

            assertThatThrownBy(() -> workOrders.findById(theirs.id(), vendorTechnician, SourceChannel.MOBILE))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class)
                    .hasMessageContaining("assigned to you");
        }

        @Test
        void a_vendor_technician_cannot_transition_somebody_elses_work_order() {
            WorkOrder theirs = assign(createWorkOrder(triage(report(FaultPriority.LOW), FaultPriority.LOW)),
                    "other.tech");

            assertThatThrownBy(() -> workOrders.transition(new MaintenanceCommands.TransitionWorkOrder(
                    theirs.id(), MaintenanceCommands.TransitionWorkOrder.Transition.START, null, null,
                    vendorTechnician, SourceChannel.MOBILE)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        void an_in_house_technician_is_not_narrowed_the_same_way() {
            assign(createWorkOrder(triage(report(FaultPriority.LOW), FaultPriority.LOW)), "acme.tech");
            assign(createWorkOrder(triage(report(FaultPriority.LOW), FaultPriority.LOW)), "other.tech");

            assertThat(workOrders.search(null, null, null, null, null, null, null, 50, technician,
                    SourceChannel.WEB)).hasSize(2);
        }

        @Test
        void work_cannot_be_assigned_to_a_vendor_whose_contract_has_expired() {
            MaintenanceVendor expired = vendors.register(new MaintenanceCommands.RegisterVendor("MAIN",
                    "ACME", "Acme Facilities", null, null, null, null, 4, "CT-1",
                    TODAY.minusDays(1), null, manager, SourceChannel.WEB, null, null));
            WorkOrder order = createWorkOrder(triage(report(FaultPriority.LOW), FaultPriority.LOW));

            assertThatThrownBy(() -> workOrders.assign(new MaintenanceCommands.AssignWorkOrder(order.id(),
                    "acme.tech", expired.id(), null, manager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        void a_tighter_contracted_response_time_wins_over_the_priority_rule() {
            configuration.set("maintenance.sla.resolution.low", "P14D");
            MaintenanceVendor vendor = vendors.register(new MaintenanceCommands.RegisterVendor("MAIN",
                    "ACME", "Acme Facilities", null, null, null, null, 6, null, null, null, manager,
                    SourceChannel.WEB, null, null));
            FacilityFault fault = triage(report(FaultPriority.LOW), FaultPriority.LOW);

            WorkOrder order = workOrders.createFromFault(
                    new MaintenanceCommands.CreateWorkOrderFromFault(fault.id(), vendor.id(), "acme.tech",
                            manager, SourceChannel.WEB, null, null));

            assertThat(order.slaDueAt()).isEqualTo(NOW.plus(Duration.ofHours(6)));
        }
    }

    // =========================================================================================

    @Nested
    @DisplayName("S153. Preventive maintenance")
    class Preventive {

        @Test
        void a_schedule_generates_a_work_order_inside_its_lead_time() {
            createSchedule(TODAY.plusDays(5), 90, 7);

            List<WorkOrder> raised = preventive.generateDueWorkOrders(system, TODAY);

            assertThat(raised).hasSize(1);
            assertThat(raised.get(0).workOrderType()).isEqualTo(WorkOrderType.PREVENTIVE);
            assertThat(raised.get(0).assetId()).isEqualTo(generator.id());
            assertThat(raised.get(0).facilityFaultId()).isNull();
            assertThat(audit.actions()).contains(AuditAction.PREVENTIVE_WORK_ORDER_GENERATED);
        }

        @Test
        void it_generates_nothing_outside_the_lead_time() {
            createSchedule(TODAY.plusDays(30), 90, 7);

            assertThat(preventive.generateDueWorkOrders(system, TODAY)).isEmpty();
        }

        /** The scheduler is at-least-once, and a technician sent twice costs a vendor call-out. */
        @Test
        void running_generation_twice_raises_one_work_order() {
            createSchedule(TODAY.plusDays(5), 90, 7);

            assertThat(preventive.generateDueWorkOrders(system, TODAY)).hasSize(1);
            assertThat(preventive.generateDueWorkOrders(system, TODAY)).isEmpty();
            assertThat(preventive.generateDueWorkOrders(system, TODAY.plusDays(1))).isEmpty();
        }

        /**
         * A generator that ran late must not push every later service later, or a quarterly inspection
         * drifts out of its quarter inside a year.
         */
        @Test
        void the_next_due_date_advances_from_the_cycle_not_from_today() {
            PreventiveMaintenanceSchedule schedule = createSchedule(TODAY.plusDays(1), 90, 7);
            preventive.generateDueWorkOrders(system, TODAY.plusDays(4));

            assertThat(maintenance.findSchedule(schedule.id()).orElseThrow().nextDueOn())
                    .isEqualTo(TODAY.plusDays(91));
        }

        @Test
        void closing_a_preventive_order_records_the_service_against_the_asset() {
            createSchedule(TODAY.plusDays(1), 90, 7);
            WorkOrder order = preventive.generateDueWorkOrders(system, TODAY).get(0);
            order = assign(order, "technician");
            order = transition(order, MaintenanceCommands.TransitionWorkOrder.Transition.START, null);
            close(order, "Serviced and load-tested");

            assertThat(facilities.findAsset(generator.id()).orElseThrow().lastServicedOn())
                    .isEqualTo(TODAY);
        }

        @Test
        void a_schedule_against_a_decommissioned_asset_stops_generating() {
            createSchedule(TODAY.plusDays(1), 90, 7);
            assets.changeStatus(new FacilitiesCommands.ChangeAssetStatus(generator.id(),
                    AssetOperationalStatus.DECOMMISSIONED, "Replaced by GEN-02", null, manager,
                    SourceChannel.WEB));

            assertThat(preventive.generateDueWorkOrders(system, TODAY)).isEmpty();
        }

        @Test
        void a_lead_time_at_or_beyond_the_interval_is_refused() {
            assertThatThrownBy(() -> createSchedule(TODAY.plusDays(1), 30, 30))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("shorter than intervalDays");
        }
    }

    // =========================================================================================
    // Fixtures
    // =========================================================================================

    private FacilityFault report(FaultPriority priority) {
        return faults.report(new MaintenanceCommands.ReportFault("MAIN", hall.id(), null, null,
                priority + " fault in Hall A", "Reported from the floor", null, priority, manager,
                SourceChannel.WEB, null, null));
    }

    private FacilityFault triage(FacilityFault fault, FaultPriority priority) {
        return faults.triage(new MaintenanceCommands.TriageFault(fault.id(), priority, "Assessed on site",
                null, supervisor, SourceChannel.WEB));
    }

    private WorkOrder createWorkOrder(FacilityFault fault) {
        return workOrders.createFromFault(new MaintenanceCommands.CreateWorkOrderFromFault(fault.id(), null,
                null, manager, SourceChannel.WEB, null, null));
    }

    private WorkOrder assign(WorkOrder order, String assignee) {
        return workOrders.assign(new MaintenanceCommands.AssignWorkOrder(order.id(), assignee, null, null,
                manager, SourceChannel.WEB));
    }

    private WorkOrder transition(WorkOrder order,
            MaintenanceCommands.TransitionWorkOrder.Transition transition, String notes) {
        return workOrders.transition(new MaintenanceCommands.TransitionWorkOrder(order.id(), transition,
                notes, null, supervisor, SourceChannel.WEB));
    }

    private WorkOrder close(WorkOrder order, String notes) {
        return workOrders.close(new MaintenanceCommands.CloseWorkOrder(order.id(), notes, null, supervisor,
                SourceChannel.WEB));
    }

    /** An assigned, started work order at the given priority — the state closure is attempted from. */
    private WorkOrder readyToClose(FaultPriority priority) {
        WorkOrder order = assign(createWorkOrder(triage(report(priority), priority)), "technician");
        return transition(order, MaintenanceCommands.TransitionWorkOrder.Transition.START, null);
    }

    private MaintenanceEvidence attachEvidence(WorkOrder order) {
        return evidence.attach(new MaintenanceCommands.AttachEvidence(order.id(), EvidenceType.AFTER_PHOTO,
                "s3://evidence/" + order.id() + "/after.jpg", "after.jpg", "image/jpeg", 1024L,
                "c".repeat(64), RetentionClass.OPERATIONAL, null, supervisor, SourceChannel.MOBILE, null,
                null));
    }

    private PreventiveMaintenanceSchedule createSchedule(LocalDate firstDue, int interval, int leadTime) {
        return preventive.create(new MaintenanceCommands.CreateSchedule("MAIN", "GEN-SERVICE",
                "Generator quarterly service", "Load test and oil change", generator.id(), interval,
                leadTime, FaultPriority.MEDIUM, WorkOrderType.PREVENTIVE, firstDue, manager,
                SourceChannel.WEB, null, null));
    }

    /**
     * A clock that can be pushed forward.
     *
     * <p>Half of S153 is about time passing, and {@code Clock.fixed} cannot express "four hours later
     * the sweep runs" — the single most important behaviour in this module. Mutable rather than a
     * series of fixed clocks so a test reads as a sequence of events at one service.
     */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
