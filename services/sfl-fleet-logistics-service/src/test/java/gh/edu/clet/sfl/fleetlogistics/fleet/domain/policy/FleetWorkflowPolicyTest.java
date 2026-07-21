package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SlaTarget;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Traces: SRS-SFL-S166-02 workflow state machine and configurable SLA rules. */
class FleetWorkflowPolicyTest {

    @Nested
    @DisplayName("TripTransitionPolicy")
    class TripTransitions {

        @ParameterizedTest(name = "{0} -> {1} is allowed")
        @CsvSource({
                "PLANNED, ASSIGNED",
                "PLANNED, CANCELLED",
                "ASSIGNED, IN_PROGRESS",
                "ASSIGNED, ON_HOLD",
                "ASSIGNED, ASSIGNED",
                "ASSIGNED, CANCELLED",
                "IN_PROGRESS, ON_HOLD",
                "IN_PROGRESS, COMPLETED",
                "IN_PROGRESS, CANCELLED",
                "ON_HOLD, ASSIGNED",
                "ON_HOLD, IN_PROGRESS",
                "ON_HOLD, CANCELLED"
        })
        void allowed(TripStatus from, TripStatus to) {
            assertThat(TripTransitionPolicy.canTransition(from, to)).isTrue();
        }

        @ParameterizedTest(name = "{0} -> {1} is prohibited")
        @CsvSource({
                "PLANNED, IN_PROGRESS",
                "PLANNED, COMPLETED",
                "PLANNED, ON_HOLD",
                "ASSIGNED, COMPLETED",
                "ON_HOLD, COMPLETED",
                "COMPLETED, IN_PROGRESS",
                "COMPLETED, CANCELLED",
                "CANCELLED, ASSIGNED",
                "CANCELLED, COMPLETED"
        })
        void prohibited(TripStatus from, TripStatus to) {
            assertThat(TripTransitionPolicy.canTransition(from, to)).isFalse();
            assertThatThrownBy(() -> TripTransitionPolicy.requireTransition(from, to))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("a trip must pass through ASSIGNED before it can start")
        void a_trip_must_be_assigned_before_starting() {
            assertThat(TripTransitionPolicy.canTransition(TripStatus.PLANNED, TripStatus.IN_PROGRESS)).isFalse();
            assertThat(TripTransitionPolicy.canTransition(TripStatus.ASSIGNED, TripStatus.IN_PROGRESS)).isTrue();
        }
    }

    @Nested
    @DisplayName("FleetWorkflowTransitionPolicy")
    class WorkflowTransitions {

        @ParameterizedTest(name = "{0} -> {1} is allowed")
        @CsvSource({
                "OPEN, ASSIGNED",
                "OPEN, ESCALATED",
                "OPEN, CANCELLED",
                "ASSIGNED, ASSIGNED",
                "ASSIGNED, IN_PROGRESS",
                "ASSIGNED, CLOSED",
                "IN_PROGRESS, ESCALATED",
                "IN_PROGRESS, CLOSED",
                "ON_HOLD, IN_PROGRESS",
                "ESCALATED, IN_PROGRESS",
                "ESCALATED, ESCALATED",
                "ESCALATED, CLOSED",
                "CLOSED, REOPENED",
                "REOPENED, IN_PROGRESS",
                "REOPENED, CLOSED"
        })
        void allowed(FleetWorkflowStatus from, FleetWorkflowStatus to) {
            assertThat(FleetWorkflowTransitionPolicy.canTransition(from, to)).isTrue();
        }

        @ParameterizedTest(name = "{0} -> {1} is prohibited")
        @CsvSource({
                "OPEN, CLOSED",
                "OPEN, IN_PROGRESS",
                "OPEN, REOPENED",
                "CLOSED, IN_PROGRESS",
                "CLOSED, CANCELLED",
                "CANCELLED, OPEN",
                "CANCELLED, REOPENED",
                "ON_HOLD, CLOSED"
        })
        void prohibited(FleetWorkflowStatus from, FleetWorkflowStatus to) {
            assertThat(FleetWorkflowTransitionPolicy.canTransition(from, to)).isFalse();
        }

        @Test
        @DisplayName("an unassigned item cannot be closed; somebody must own it first")
        void an_open_item_cannot_be_closed_directly() {
            assertThatThrownBy(() -> FleetWorkflowTransitionPolicy.requireTransition(FleetWorkflowStatus.OPEN,
                    FleetWorkflowStatus.CLOSED)).isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("cancellation is terminal but closure permits reopening")
        void cancellation_is_terminal_and_closure_is_not() {
            assertThat(FleetWorkflowTransitionPolicy.canTransition(FleetWorkflowStatus.CANCELLED,
                    FleetWorkflowStatus.REOPENED)).isFalse();
            assertThat(FleetWorkflowTransitionPolicy.canTransition(FleetWorkflowStatus.CLOSED,
                    FleetWorkflowStatus.REOPENED)).isTrue();
        }
    }

    @Nested
    @DisplayName("SlaPolicy")
    class Sla {

        private final SlaPolicy.SlaRule defaultRule = new SlaPolicy.SlaRule("default", null, null, null, null,
                null, Duration.ofHours(4), Duration.ofHours(24), SflRole.FLEET_MANAGER);
        private final SlaPolicy.SlaRule urgentRule = new SlaPolicy.SlaRule("urgent", null,
                WorkflowPriority.URGENT, null, null, null, Duration.ofHours(1), Duration.ofHours(4),
                SflRole.FLEET_MANAGER);
        private final SlaPolicy.SlaRule criticalDefectRule = new SlaPolicy.SlaRule("critical-defect",
                FleetWorkflowType.VEHICLE_DEFECT, null, WorkflowSeverity.CRITICAL, null, null,
                Duration.ofMinutes(30), Duration.ofHours(4), SflRole.FLEET_MANAGER);
        private final SlaPolicy.SlaRule siteRule = new SlaPolicy.SlaRule("accra-defect",
                FleetWorkflowType.VEHICLE_DEFECT, null, WorkflowSeverity.CRITICAL, "ACCRA",
                OperatingMode.EMERGENCY, Duration.ofMinutes(10), Duration.ofHours(1), SflRole.SFL_ADMIN);

        @Test
        @DisplayName("the most specific matching rule wins")
        void most_specific_rule_wins() {
            SlaTarget target = SlaPolicy.resolve(List.of(defaultRule, urgentRule, criticalDefectRule, siteRule),
                    FleetWorkflowType.VEHICLE_DEFECT, WorkflowPriority.URGENT, WorkflowSeverity.CRITICAL,
                    "ACCRA", OperatingMode.EMERGENCY);

            assertThat(target.ruleReference()).isEqualTo("accra-defect");
            assertThat(target.responseTarget()).isEqualTo(Duration.ofMinutes(10));
            assertThat(target.escalationRole()).isEqualTo(SflRole.SFL_ADMIN);
        }

        @Test
        @DisplayName("a rule whose site does not match is not applied")
        void non_matching_site_is_skipped() {
            SlaTarget target = SlaPolicy.resolve(List.of(defaultRule, criticalDefectRule, siteRule),
                    FleetWorkflowType.VEHICLE_DEFECT, WorkflowPriority.HIGH, WorkflowSeverity.CRITICAL,
                    "KUMASI", OperatingMode.EMERGENCY);

            assertThat(target.ruleReference()).isEqualTo("critical-defect");
        }

        @Test
        @DisplayName("with no configured rule the compiled-in default applies and says so")
        void unconfigured_falls_back_visibly() {
            SlaTarget target = SlaPolicy.resolve(List.of(), FleetWorkflowType.TRIP_EXCEPTION,
                    WorkflowPriority.LOW, WorkflowSeverity.MINOR, "ACCRA", OperatingMode.ROUTINE);

            assertThat(target.ruleReference()).isEqualTo(SlaPolicy.DEFAULT_RULE_REFERENCE);
            assertThat(target.resolutionTarget()).isEqualTo(Duration.ofHours(24));
        }

        @Test
        @DisplayName("the due dates are computed from when the item was raised")
        void due_dates_are_computed_from_the_raise_time() {
            Instant raisedAt = Instant.parse("2026-07-21T08:00:00Z");
            SlaTarget target = SlaPolicy.resolve(List.of(urgentRule), FleetWorkflowType.TRIP_EXCEPTION,
                    WorkflowPriority.URGENT, WorkflowSeverity.MAJOR, "ACCRA", OperatingMode.ROUTINE);

            assertThat(target.responseDueAt(raisedAt)).isEqualTo(Instant.parse("2026-07-21T09:00:00Z"));
            assertThat(target.dueAt(raisedAt)).isEqualTo(Instant.parse("2026-07-21T12:00:00Z"));
        }

        @Test
        @DisplayName("a resolution target shorter than the response target is rejected")
        void inconsistent_targets_are_rejected() {
            assertThatThrownBy(() -> new SlaTarget(Duration.ofHours(4), Duration.ofHours(1),
                    SflRole.FLEET_MANAGER, "bad-rule"))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatCode(() -> new SlaTarget(Duration.ofHours(1), Duration.ofHours(1), SflRole.FLEET_MANAGER,
                    "equal-targets")).doesNotThrowAnyException();
        }
    }
}
