package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceAllocation;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.spaceplanning.application.OccupancyStandardService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpaceChangeRequestService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningCommands;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpaceScenarioService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.UtilisationReconciliationService;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.BookingUtilisationPort;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.ConstructionHandoffPort;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.AllocationScenario;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.CommitOutcome;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ComplianceStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyOverride;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ScenarioUncommittedException;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.SpaceChangeRequest;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UnitHeadcount;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSignal;
import gh.edu.clet.sfl.facilities.spaceplanning.support.SpacePlanningTestFixture;
import gh.edu.clet.sfl.facilities.support.IfimpTestHarness;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The S158 acceptance criteria, end to end through the application services - one nested class per
 * SRS requirement, one test per criterion, validation rule and error state.
 *
 * <p>Built over {@link IfimpTestHarness}, whose real S152 estate ({@code MAIN}'s {@code HALL-A},
 * {@code MEET-1}, {@code OFF-101}) and S153 services are exactly what most S158 acceptance criteria are
 * statements about - "S152's current-state register is unaffected" is a claim about the real S152
 * service, not about a mock of it.
 */
class S158MandatoryScenariosTest {

    private IfimpTestHarness harness;
    private SpacePlanningTestFixture fixture;

    private ActorContext planner;
    private ActorContext director;
    private ActorContext manager;
    private ActorContext requester;
    private ActorContext otherRequester;
    private ActorContext kumasiManager;

    @BeforeEach
    void setUp() {
        harness = new IfimpTestHarness();
        fixture = new SpacePlanningTestFixture(harness);
        planner = harness.spacePlanner;
        director = harness.director;
        manager = harness.manager;
        requester = harness.requester;
        otherRequester = TestDoubles.actor("occupant.2", java.util.Set.of(SflRole.IFIMP_REQUESTER), "MAIN");
        kumasiManager = harness.kumasiManager;
    }

    // =============================================================================================
    // SRS-SFL-S158-01 - Space Plan and Allocation Scenario Modelling
    // =============================================================================================

    @Nested
    @DisplayName("SRS-SFL-S158-01 Space Plan and Allocation Scenario Modelling")
    class ScenarioModelling {

        @Test
        @DisplayName("AC: two draft scenarios exist, neither committed -> S152's register is unaffected")
        void two_uncommitted_drafts_leave_the_register_unaffected() {
            AllocationScenario draftA = createScenario("Draft A");
            AllocationScenario draftB = createScenario("Draft B");
            allocate(draftA, harness.office.id(), new UnitHeadcount("REGISTRY", 2));
            allocate(draftB, harness.office.id(), new UnitHeadcount("BURSARY", 3));

            assertThat(draftA.status()).isEqualTo(ScenarioStatus.DRAFT);
            assertThat(draftB.status()).isEqualTo(ScenarioStatus.DRAFT);
            assertThat(fixture.register.current("MAIN", planner, SourceChannel.WEB)).isEmpty();
        }

        @Test
        @DisplayName("multiple concurrent drafts can be compared against each other and the current register")
        void scenarios_can_be_compared_against_each_other_and_current() {
            AllocationScenario draftA = createScenario("Draft A");
            AllocationScenario draftB = createScenario("Draft B");
            allocate(draftA, harness.office.id(), new UnitHeadcount("REGISTRY", 2));
            allocate(draftB, harness.office.id(), new UnitHeadcount("BURSARY", 3));

            SpaceScenarioService.ScenarioComparison comparison = fixture.scenarios.compare(
                    List.of(draftA.id(), draftB.id()), planner, SourceChannel.WEB);

            SpaceScenarioService.RoomComparison office = comparison.rooms().stream()
                    .filter(r -> r.roomId().equals(harness.office.id())).findFirst().orElseThrow();
            assertThat(office.current()).isEmpty();
            assertThat(office.scenariosAgree()).isFalse();
            assertThat(office.scenarios()).hasSize(2);
        }

        @Test
        @DisplayName("committing LIKE_FOR_LIKE is an explicit, named act that applies to the S152 register")
        void committing_like_for_like_applies_to_the_register() {
            AllocationScenario scenario = createScenario("Registry move");
            allocate(scenario, harness.office.id(), new UnitHeadcount("REGISTRY", 2));

            SpaceScenarioService.CommitResult result = fixture.scenarios.commit(
                    new SpacePlanningCommands.CommitScenario(scenario.id(), CommitOutcome.LIKE_FOR_LIKE,
                            "Approved at estates meeting", null, null, null, planner, SourceChannel.WEB));

            assertThat(result.scenario().status()).isEqualTo(ScenarioStatus.COMMITTED);
            assertThat(result.scenario().committedBy()).isEqualTo("space.planner");
            assertThat(result.scenario().committedAt()).isEqualTo(harness.clock.instant());
            List<SpaceAllocation> current = fixture.register.current("MAIN", planner, SourceChannel.WEB);
            assertThat(current).extracting(SpaceAllocation::allocatedUnit).containsExactly("REGISTRY");
            assertThat(harness.audit.recorded(AuditAction.SPACE_SCENARIO_COMMITTED)).isTrue();
            assertThat(harness.audit.recorded(AuditAction.SPACE_ALLOCATION_APPLIED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.space-scenario-committed.v1")).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.space-allocation-applied.v1")).isTrue();
        }

        @Test
        @DisplayName("committing PHYSICAL_WORKS proposes an S176 project and stays COMMITTED, awaiting handover")
        void committing_physical_works_proposes_a_construction_project_and_waits() {
            AllocationScenario scenario = createScenario("New computer lab");
            allocate(scenario, harness.meetingRoom.id(), new UnitHeadcount("IT_UNIT", 8));

            SpaceScenarioService.CommitResult result = fixture.scenarios.commit(
                    new SpacePlanningCommands.CommitScenario(scenario.id(), CommitOutcome.PHYSICAL_WORKS,
                            "Needs a raised floor", "Lab conversion", "Convert MEET-1 to a computer lab", null,
                            planner, SourceChannel.WEB));

            assertThat(result.scenario().status()).isEqualTo(ScenarioStatus.COMMITTED);
            assertThat(result.scenario().linkedProjectId()).isNotNull();
            assertThat(result.scenario().appliedToRegisterAt()).isNull();
            assertThat(fixture.construction.proposals).hasSize(1);
            assertThat(fixture.register.current("MAIN", planner, SourceChannel.WEB))
                    .noneMatch(a -> a.roomId().equals(harness.meetingRoom.id()));
        }

        @Test
        @DisplayName("S176's handover confirmation applies the allocations and moves the scenario to HANDED_OVER")
        void handover_confirmation_applies_allocations_and_completes_the_scenario() {
            AllocationScenario scenario = createScenario("New computer lab");
            allocate(scenario, harness.meetingRoom.id(), new UnitHeadcount("IT_UNIT", 8));
            SpaceScenarioService.CommitResult committed = fixture.scenarios.commit(
                    new SpacePlanningCommands.CommitScenario(scenario.id(), CommitOutcome.PHYSICAL_WORKS, null, null,
                            null, null, planner, SourceChannel.WEB));
            UUID projectId = committed.scenario().linkedProjectId();

            AllocationScenario handedOver = fixture.scenarios.confirmHandover(scenario.id(), projectId,
                    committed.scenario().linkedProjectReference(), harness.system);

            assertThat(handedOver.status()).isEqualTo(ScenarioStatus.HANDED_OVER);
            assertThat(fixture.register.current("MAIN", planner, SourceChannel.WEB))
                    .anyMatch(a -> a.roomId().equals(harness.meetingRoom.id()) && a.allocatedUnit().equals("IT_UNIT"));
            assertThat(harness.audit.recorded(AuditAction.SPACE_SCENARIO_HANDOVER_CONFIRMED)).isTrue();
        }

        @Test
        @DisplayName("a repeated handover confirmation for the same project is idempotent")
        void handover_confirmation_is_idempotent() {
            AllocationScenario scenario = createScenario("New computer lab");
            allocate(scenario, harness.meetingRoom.id(), new UnitHeadcount("IT_UNIT", 8));
            SpaceScenarioService.CommitResult committed = fixture.scenarios.commit(
                    new SpacePlanningCommands.CommitScenario(scenario.id(), CommitOutcome.PHYSICAL_WORKS, null, null,
                            null, null, planner, SourceChannel.WEB));
            UUID projectId = committed.scenario().linkedProjectId();
            fixture.scenarios.confirmHandover(scenario.id(), projectId, null, harness.system);
            int allocationsAfterFirst = fixture.register.current("MAIN", planner, SourceChannel.WEB).size();

            AllocationScenario second = fixture.scenarios.confirmHandover(scenario.id(), projectId, null,
                    harness.system);

            assertThat(second.status()).isEqualTo(ScenarioStatus.HANDED_OVER);
            assertThat(fixture.register.current("MAIN", planner, SourceChannel.WEB)).hasSize(allocationsAfterFirst);
        }

        @Test
        @DisplayName("error state: applying a draft scenario to the S152 register is refused, uncommitted")
        void applying_a_draft_to_the_register_is_refused() {
            AllocationScenario draft = createScenario("Draft only");
            allocate(draft, harness.office.id(), new UnitHeadcount("REGISTRY", 2));

            assertThatThrownBy(() -> fixture.scenarios.applyToRegister(draft.id(), planner, SourceChannel.WEB))
                    .isInstanceOf(ScenarioUncommittedException.class)
                    .extracting(t -> ((FacilitiesException) t).code())
                    .isEqualTo(FacilitiesErrorCode.SPACE_SCENARIO_UNCOMMITTED);
            assertThat(harness.audit.recorded(AuditAction.SPACE_SCENARIO_USE_AS_CURRENT_REFUSED)).isTrue();
        }

        @Test
        @DisplayName("error state: a current-allocation query given a draft scenario id is refused, uncommitted")
        void querying_current_allocation_with_a_draft_id_is_refused() {
            AllocationScenario draft = createScenario("Draft only");

            assertThatThrownBy(() -> fixture.scenarios.currentAllocations("MAIN", draft.id(), planner,
                    SourceChannel.WEB))
                    .isInstanceOf(ScenarioUncommittedException.class);
        }

        @Test
        @DisplayName("refusal: a scenario cannot allocate a room from a different site")
        void a_scenario_cannot_allocate_a_room_from_another_site() {
            AllocationScenario scenario = createScenario("Cross-site attempt");

            assertThatThrownBy(() -> fixture.scenarios.setRoomAllocation(new SpacePlanningCommands.SetRoomAllocation(
                    scenario.id(), harness.kumasiHall.id(), List.of(new UnitHeadcount("REGISTRY", 5)), null, planner,
                    SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class);
        }

        @Test
        @DisplayName("refusal: an actor outside the scenario's site cannot read it")
        void an_actor_outside_the_site_cannot_read_the_scenario() {
            AllocationScenario scenario = createScenario("MAIN only");

            assertThatThrownBy(() -> fixture.scenarios.find(scenario.id(), kumasiManager, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        private AllocationScenario createScenario(String name) {
            return fixture.scenarios.create(new SpacePlanningCommands.CreateScenario("MAIN", name, null, null,
                    planner, SourceChannel.WEB));
        }

        private void allocate(AllocationScenario scenario, UUID roomId, UnitHeadcount... units) {
            fixture.scenarios.setRoomAllocation(new SpacePlanningCommands.SetRoomAllocation(scenario.id(), roomId,
                    List.of(units), null, planner, SourceChannel.WEB));
        }
    }

    // =============================================================================================
    // SRS-SFL-S158-02 - Occupancy Standards and Compliance Tracking
    // =============================================================================================

    @Nested
    @DisplayName("SRS-SFL-S158-02 Occupancy Standards and Compliance Tracking")
    class OccupancyStandardsAndCompliance {

        @Test
        @DisplayName("error state: a space type with no standard is NOT_EVALUATED, never compliant by default")
        void no_standard_is_not_evaluated() {
            AllocationScenario scenario = fixture.scenarios.create(
                    new SpacePlanningCommands.CreateScenario("MAIN", "No standard yet", null, null, planner,
                            SourceChannel.WEB));

            SpaceScenarioService.RoomPlan plan = fixture.scenarios.setRoomAllocation(
                    new SpacePlanningCommands.SetRoomAllocation(scenario.id(), harness.office.id(),
                            List.of(new UnitHeadcount("REGISTRY", 3)), null, planner, SourceChannel.WEB));

            assertThat(plan.compliance().status()).isEqualTo(ComplianceStatus.NOT_EVALUATED);
            assertThat(plan.compliance().flagged()).isFalse();
        }

        @Test
        @DisplayName("AC: an allocation beyond the standard is flagged non-compliant unless overridden")
        void beyond_standard_is_flagged_non_compliant() {
            defineStandard(SpaceType.OFFICE, 100, null);
            AllocationScenario scenario = fixture.scenarios.create(
                    new SpacePlanningCommands.CreateScenario("MAIN", "Overcrowded office", null, null, planner,
                            SourceChannel.WEB));

            SpaceScenarioService.RoomPlan plan = fixture.scenarios.setRoomAllocation(
                    new SpacePlanningCommands.SetRoomAllocation(scenario.id(), harness.office.id(),
                            List.of(new UnitHeadcount("REGISTRY", 6)), null, planner, SourceChannel.WEB));

            assertThat(plan.compliance().status()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
            assertThat(plan.compliance().flagged()).isTrue();
        }

        @Test
        @DisplayName("non-compliant allocations are recorded, not blocked - a commit still succeeds")
        void non_compliant_allocations_are_not_blocked() {
            defineStandard(SpaceType.OFFICE, 100, null);
            AllocationScenario scenario = fixture.scenarios.create(
                    new SpacePlanningCommands.CreateScenario("MAIN", "Overcrowded office", null, null, planner,
                            SourceChannel.WEB));
            fixture.scenarios.setRoomAllocation(new SpacePlanningCommands.SetRoomAllocation(scenario.id(),
                    harness.office.id(), List.of(new UnitHeadcount("REGISTRY", 6)), null, planner, SourceChannel.WEB));

            SpaceScenarioService.CommitResult result = fixture.scenarios.commit(
                    new SpacePlanningCommands.CommitScenario(scenario.id(), CommitOutcome.LIKE_FOR_LIKE, null, null,
                            null, null, planner, SourceChannel.WEB));

            assertThat(result.scenario().status()).isEqualTo(ScenarioStatus.COMMITTED);
            assertThat(result.compliance()).anyMatch(gh.edu.clet.sfl.facilities.spaceplanning.domain.RoomCompliance::flagged);
        }

        @Test
        @DisplayName("validation: an override must carry a reason and an accountable approver, not the requester")
        void override_requires_reason_and_an_accountable_approver() {
            defineStandard(SpaceType.OFFICE, 100, null);
            AllocationScenario scenario = fixture.scenarios.create(
                    new SpacePlanningCommands.CreateScenario("MAIN", "Overcrowded office", null, null, planner,
                            SourceChannel.WEB));
            fixture.scenarios.setRoomAllocation(new SpacePlanningCommands.SetRoomAllocation(scenario.id(),
                    harness.office.id(), List.of(new UnitHeadcount("REGISTRY", 6)), null, planner, SourceChannel.WEB));

            OccupancyOverride requested = fixture.standards.requestOverride(new SpacePlanningCommands.RequestOverride(
                    scenario.id(), harness.office.id(), "Temporary until the annexe is ready", planner,
                    SourceChannel.WEB));

            assertThatThrownBy(() -> fixture.standards.approveOverride(
                    new SpacePlanningCommands.ApproveOverride(requested.id(), planner, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.class)
                    .extracting(t -> ((FacilitiesException) t).code())
                    .isEqualTo(FacilitiesErrorCode.SPACE_OVERRIDE_INCOMPLETE);

            OccupancyOverride approved = fixture.standards.approveOverride(
                    new SpacePlanningCommands.ApproveOverride(requested.id(), director, SourceChannel.WEB));

            assertThat(approved.status()).isEqualTo(OccupancyOverride.OverrideStatus.APPROVED);
            assertThat(approved.approvedBy()).isEqualTo("director");
            assertThat(harness.audit.recorded(AuditAction.OCCUPANCY_OVERRIDE_RECORDED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.occupancy-override-recorded.v1")).isTrue();
        }

        @Test
        @DisplayName("an approved override is withdrawn when the room's allocation changes under it")
        void override_is_withdrawn_when_the_allocation_changes() {
            defineStandard(SpaceType.OFFICE, 100, null);
            AllocationScenario scenario = fixture.scenarios.create(
                    new SpacePlanningCommands.CreateScenario("MAIN", "Overcrowded office", null, null, planner,
                            SourceChannel.WEB));
            fixture.scenarios.setRoomAllocation(new SpacePlanningCommands.SetRoomAllocation(scenario.id(),
                    harness.office.id(), List.of(new UnitHeadcount("REGISTRY", 6)), null, planner, SourceChannel.WEB));
            OccupancyOverride requested = fixture.standards.requestOverride(new SpacePlanningCommands.RequestOverride(
                    scenario.id(), harness.office.id(), "Temporary", planner, SourceChannel.WEB));
            fixture.standards.approveOverride(new SpacePlanningCommands.ApproveOverride(requested.id(), director,
                    SourceChannel.WEB));

            fixture.scenarios.setRoomAllocation(new SpacePlanningCommands.SetRoomAllocation(scenario.id(),
                    harness.office.id(), List.of(new UnitHeadcount("REGISTRY", 7)), 1L, planner, SourceChannel.WEB));

            assertThat(fixture.repository.findOverride(requested.id()).orElseThrow().status())
                    .isEqualTo(OccupancyOverride.OverrideStatus.WITHDRAWN);
        }

        private void defineStandard(SpaceType type, Integer maxCapacityPercent, BigDecimal minAreaPerPerson) {
            fixture.standards.define(new SpacePlanningCommands.DefineStandard("MAIN", type, maxCapacityPercent,
                    minAreaPerPerson, null, planner, SourceChannel.WEB));
        }
    }

    // =============================================================================================
    // SRS-SFL-S158-03 - Utilisation Reconciliation Against Booking Data
    // =============================================================================================

    @Nested
    @DisplayName("SRS-SFL-S158-03 Utilisation Reconciliation Against Booking Data")
    class UtilisationReconciliation {

        @Test
        @DisplayName("AC: a space booked under 20% of capacity over a full period appears on the signal list")
        void under_twenty_percent_over_a_full_period_raises_a_signal() {
            UtilisationReconciliationService.RunSummary run = fixture.utilisation.reconcile(
                    new SpacePlanningCommands.RunReconciliation("MAIN", null, harness.system, SourceChannel.SCHEDULER));

            assertThat(run.roomsSnapshotted()).isGreaterThanOrEqualTo(3);
            List<UtilisationSignal> signals = fixture.utilisation.signals("MAIN", true, planner, SourceChannel.WEB);
            assertThat(signals).anyMatch(s -> s.roomId().equals(harness.meetingRoom.id())
                    && s.kind() == UtilisationSignal.Kind.UNDER_UTILISED);
            assertThat(harness.audit.recorded(AuditAction.UTILISATION_SIGNAL_RAISED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.utilisation-signal-raised.v1")).isTrue();
        }

        @Test
        @DisplayName("a room with no bookings is included from the S152 room list as zero use, not skipped")
        void a_room_with_no_bookings_is_zero_use_not_absent() {
            fixture.utilisation.reconcile(new SpacePlanningCommands.RunReconciliation("MAIN", null, harness.system,
                    SourceChannel.SCHEDULER));

            var snapshot = fixture.repository.findSnapshot(harness.meetingRoom.id(),
                    fixture.repository.findLatestSnapshots("MAIN").get(0).periodStart(),
                    fixture.repository.findLatestSnapshots("MAIN").get(0).periodEnd());
            assertThat(snapshot).isPresent();
            assertThat(snapshot.orElseThrow().bookingCount()).isZero();
            assertThat(snapshot.orElseThrow().utilisationRate()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("a persistent gap between planned allocation and observed utilisation raises a signal, "
                + "and the signal clears once the gap closes")
        void persistent_gap_raises_and_clears() {
            AllocationScenario scenario = fixture.scenarios.create(
                    new SpacePlanningCommands.CreateScenario("MAIN", "Planned meeting room use", null, null, planner,
                            SourceChannel.WEB));
            fixture.scenarios.setRoomAllocation(new SpacePlanningCommands.SetRoomAllocation(scenario.id(),
                    harness.meetingRoom.id(), List.of(new UnitHeadcount("LAW_FACULTY", 8)), null, planner,
                    SourceChannel.WEB));
            fixture.scenarios.commit(new SpacePlanningCommands.CommitScenario(scenario.id(), CommitOutcome.LIKE_FOR_LIKE,
                    null, null, null, null, planner, SourceChannel.WEB));

            // Period 1: no bookings. One gap period is not yet "persistent".
            fixture.utilisation.reconcile(new SpacePlanningCommands.RunReconciliation("MAIN", null, harness.system,
                    SourceChannel.SCHEDULER));
            assertThat(fixture.utilisation.signals("MAIN", true, planner, SourceChannel.WEB))
                    .noneMatch(s -> s.roomId().equals(harness.meetingRoom.id())
                            && s.kind() == UtilisationSignal.Kind.PLANNED_ACTUAL_GAP);

            // Period 2: still no bookings. Two consecutive gap periods -> persistent.
            harness.clock.advance(Duration.ofDays(7));
            fixture.utilisation.reconcile(new SpacePlanningCommands.RunReconciliation("MAIN", null, harness.system,
                    SourceChannel.SCHEDULER));
            assertThat(fixture.utilisation.signals("MAIN", true, planner, SourceChannel.WEB))
                    .anyMatch(s -> s.roomId().equals(harness.meetingRoom.id())
                            && s.kind() == UtilisationSignal.Kind.PLANNED_ACTUAL_GAP);

            // Period 3: heavy use matching the plan. The gap closes.
            harness.clock.advance(Duration.ofDays(7));
            fixture.bookings.set(new BookingUtilisationPort.ObservedUtilisation(harness.meetingRoom.id(), "MEET-1",
                    5, 5, 0, 3000, 3000, 40));
            fixture.utilisation.reconcile(new SpacePlanningCommands.RunReconciliation("MAIN", null, harness.system,
                    SourceChannel.SCHEDULER));

            assertThat(fixture.utilisation.signals("MAIN", true, planner, SourceChannel.WEB))
                    .noneMatch(s -> s.roomId().equals(harness.meetingRoom.id())
                            && s.kind() == UtilisationSignal.Kind.PLANNED_ACTUAL_GAP);
            assertThat(harness.audit.recorded(AuditAction.UTILISATION_SIGNAL_CLEARED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.utilisation-signal-cleared.v1")).isTrue();
        }

        @Test
        @DisplayName("validation: S159 never writes back - reconciliation only reads through the utilisation port")
        void reconciliation_only_reads_s159_it_never_writes() {
            fixture.utilisation.reconcile(new SpacePlanningCommands.RunReconciliation("MAIN", null, harness.system,
                    SourceChannel.SCHEDULER));

            assertThat(harness.bookingStore.findBookings(new gh.edu.clet.sfl.facilities.booking.application.ports
                            .BookingRepository.BookingQuery("MAIN", null, null, null, null, null, null, null, null,
                            0, 10)).items())
                    .isEmpty();
        }

        @Test
        @DisplayName("error state: an unreadable S159 fails the run rather than reporting zero use")
        void an_unavailable_source_fails_the_run() {
            fixture.bookings.makeUnavailable();

            assertThatThrownBy(() -> fixture.utilisation.reconcile(new SpacePlanningCommands.RunReconciliation("MAIN",
                    null, harness.system, SourceChannel.SCHEDULER)))
                    .isInstanceOf(FacilitiesException.class)
                    .extracting(t -> ((FacilitiesException) t).code())
                    .isEqualTo(FacilitiesErrorCode.SPACE_UTILISATION_SOURCE_UNAVAILABLE);
        }
    }

    // =============================================================================================
    // SRS-SFL-S158-04 - Space-Change Request and Handover to Construction PM
    // =============================================================================================

    @Nested
    @DisplayName("SRS-SFL-S158-04 Space-Change Request and Handover to Construction PM")
    class SpaceChangeRequestAndHandover {

        @Test
        @DisplayName("a requester submits a request and sees only their own afterwards")
        void requester_sees_only_their_own_requests() {
            SpaceChangeRequest mine = submit(requester, "REGISTRY");
            submit(otherRequester, "BURSARY");

            List<SpaceChangeRequest> visible = fixture.requests.search("MAIN", null, requester, SourceChannel.WEB);

            assertThat(visible).extracting(SpaceChangeRequest::id).containsExactly(mine.id());
            assertThatThrownBy(() -> fixture.requests.find(
                    fixture.requests.search("MAIN", null, otherRequester, SourceChannel.WEB).get(0).id(), requester,
                    SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("validation: a request cannot be decided by the person who submitted it")
        void the_submitter_cannot_decide_their_own_request() {
            // The facilities manager holds both FACILITIES_SPACE_CHANGE_REQUEST and _DECIDE, so this
            // isolates the self-decide rule from the simpler "lacks the permission at all" refusal.
            SpaceChangeRequest submitted = submit(manager, "REGISTRY");

            assertThatThrownBy(() -> fixture.requests.decide(new SpacePlanningCommands.DecideRequest(submitted.id(),
                    true, null, null, manager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedApprovalException.class);
        }

        @Test
        @DisplayName("an approved request becomes a committed like-for-like scenario")
        void an_approved_request_becomes_a_committed_scenario() {
            SpaceChangeRequest submitted = submit(requester, "REGISTRY");
            SpaceChangeRequest approved = fixture.requests.decide(new SpacePlanningCommands.DecideRequest(
                    submitted.id(), true, null, null, manager, SourceChannel.WEB));
            AllocationScenario scenario = fixture.scenarios.create(new SpacePlanningCommands.CreateScenario("MAIN",
                    "Registry reassignment", null, approved.id(), planner, SourceChannel.WEB));
            fixture.scenarios.setRoomAllocation(new SpacePlanningCommands.SetRoomAllocation(scenario.id(),
                    harness.office.id(), List.of(new UnitHeadcount("REGISTRY", 2)), null, planner, SourceChannel.WEB));
            fixture.scenarios.commit(new SpacePlanningCommands.CommitScenario(scenario.id(), CommitOutcome.LIKE_FOR_LIKE,
                    null, null, null, null, planner, SourceChannel.WEB));

            SpaceChangeRequest linked = fixture.requests.linkScenario(new SpacePlanningCommands.LinkScenario(
                    approved.id(), scenario.id(), manager, SourceChannel.WEB));
            SpaceChangeRequest resolved = fixture.requests.resolve(new SpacePlanningCommands.ResolveRequest(
                    linked.id(), "Registry moved", null, manager, SourceChannel.WEB));

            assertThat(resolved.status()).isEqualTo(SpaceChangeRequest.Status.RESOLVED);
            assertThat(resolved.outcomeType()).isEqualTo(SpaceChangeRequest.OutcomeType.SCENARIO);
        }

        @Test
        @DisplayName("AC: approved and requiring physical works -> an S176 project reference is created and linked back")
        void approved_and_requiring_physical_works_creates_a_linked_project() {
            SpaceChangeRequest submitted = submit(requester, "IT_UNIT");
            SpaceChangeRequest approved = fixture.requests.decide(new SpacePlanningCommands.DecideRequest(
                    submitted.id(), true, null, null, manager, SourceChannel.WEB));

            SpaceChangeRequest linked = fixture.requests.handToConstruction(
                    new SpacePlanningCommands.HandToConstruction(approved.id(), "New IT hub", "Rewire and refit",
                            List.of(harness.office.id()), manager, SourceChannel.WEB));

            assertThat(linked.linkedProjectId()).isNotNull();
            assertThat(linked.outcomeType()).isEqualTo(SpaceChangeRequest.OutcomeType.CONSTRUCTION_PROJECT);
            assertThat(fixture.construction.proposals).hasSize(1);
            assertThat(fixture.construction.proposals.get(0).spaceChangeRequestId()).isEqualTo(approved.id());
            assertThat(harness.audit.recorded(AuditAction.SPACE_CHANGE_REQUEST_LINKED)).isTrue();

            SpaceChangeRequest resolved = fixture.requests.resolve(new SpacePlanningCommands.ResolveRequest(
                    linked.id(), "Handed to S176", null, manager, SourceChannel.WEB));
            assertThat(resolved.status()).isEqualTo(SpaceChangeRequest.Status.RESOLVED);
        }

        @Test
        @DisplayName("error state: Unlinked Resolution - closing a request with no linked outcome is refused")
        void resolving_without_a_linked_outcome_is_refused() {
            SpaceChangeRequest submitted = submit(requester, "REGISTRY");
            SpaceChangeRequest approved = fixture.requests.decide(new SpacePlanningCommands.DecideRequest(
                    submitted.id(), true, null, null, manager, SourceChannel.WEB));

            assertThatThrownBy(() -> fixture.requests.resolve(new SpacePlanningCommands.ResolveRequest(approved.id(),
                    null, null, manager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.class)
                    .extracting(t -> ((FacilitiesException) t).code())
                    .isEqualTo(FacilitiesErrorCode.SPACE_CHANGE_UNLINKED_RESOLUTION);
        }

        @Test
        @DisplayName("resolving against a scenario that is still a draft is refused as uncommitted")
        void resolving_against_a_draft_scenario_is_refused() {
            SpaceChangeRequest submitted = submit(requester, "REGISTRY");
            SpaceChangeRequest approved = fixture.requests.decide(new SpacePlanningCommands.DecideRequest(
                    submitted.id(), true, null, null, manager, SourceChannel.WEB));
            AllocationScenario draft = fixture.scenarios.create(new SpacePlanningCommands.CreateScenario("MAIN",
                    "Still modelling", null, approved.id(), planner, SourceChannel.WEB));
            SpaceChangeRequest linked = fixture.requests.linkScenario(new SpacePlanningCommands.LinkScenario(
                    approved.id(), draft.id(), manager, SourceChannel.WEB));

            assertThatThrownBy(() -> fixture.requests.resolve(new SpacePlanningCommands.ResolveRequest(linked.id(),
                    null, null, manager, SourceChannel.WEB)))
                    .isInstanceOf(ScenarioUncommittedException.class);
        }

        private SpaceChangeRequest submit(ActorContext actor, String unit) {
            return fixture.requests.submit(new SpacePlanningCommands.SubmitRequest("MAIN", unit,
                    "Growth in " + unit, harness.office.id(), null, 3, SpaceChangeRequest.Urgency.NORMAL, actor,
                    SourceChannel.WEB));
        }
    }
}
