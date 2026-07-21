package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.ACCRA;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.metadata;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ClosureEvidenceMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OdometerRegressionException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S166-02 trip lifecycle, closure evidence and closure reason. */
class TripTest {

    private static final UUID VEHICLE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DRIVER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EVIDENCE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final DateTimeRange PERIOD = DateTimeRange.of(NOW.plus(Duration.ofHours(1)),
            NOW.plus(Duration.ofHours(5)));

    @Test
    @DisplayName("a planned trip has no vehicle or driver yet")
    void planned_trip_has_no_assignment() {
        Trip trip = plan();

        assertThat(trip.status()).isEqualTo(TripStatus.PLANNED);
        assertThat(trip.vehicleId()).isNull();
        assertThat(trip.driverId()).isNull();
        assertThat(trip.holdsAssignment()).isTrue();
    }

    @Test
    @DisplayName("assignment records the vehicle and driver")
    void assignment_records_vehicle_and_driver() {
        Trip assigned = plan().assign(VEHICLE_ID, DRIVER_ID, metadata());

        assertThat(assigned.status()).isEqualTo(TripStatus.ASSIGNED);
        assertThat(assigned.vehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(assigned.driverId()).isEqualTo(DRIVER_ID);
    }

    @Test
    @DisplayName("a trip cannot start without an assigned vehicle and driver")
    void start_requires_an_assignment() {
        assertThatThrownBy(() -> plan().start(NOW, 42_000L, metadata()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("starting records the actual start and the departure odometer")
    void start_records_departure_reading() {
        Trip started = assigned().start(NOW, 42_000L, metadata());

        assertThat(started.status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(started.actualStart()).isEqualTo(NOW);
        assertThat(started.startOdometer()).isEqualTo(42_000L);
    }

    @Test
    @DisplayName("a hold remembers what the trip was doing so resume restores it")
    void hold_and_resume_restore_the_previous_status() {
        Trip inProgress = assigned().start(NOW, 42_000L, metadata());

        Trip held = inProgress.hold("Waiting for a road closure to clear", metadata());
        assertThat(held.status()).isEqualTo(TripStatus.ON_HOLD);
        assertThat(held.holdReason()).isEqualTo("Waiting for a road closure to clear");

        Trip resumed = held.resume(metadata());
        assertThat(resumed.status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(resumed.holdReason()).isNull();
    }

    @Test
    @DisplayName("resuming an assigned-but-not-started trip returns it to assigned")
    void resume_from_assigned_returns_to_assigned() {
        Trip resumed = assigned().hold("Driver reassigned to an emergency", metadata()).resume(metadata());

        assertThat(resumed.status()).isEqualTo(TripStatus.ASSIGNED);
    }

    @Test
    @DisplayName("a hold needs a reason")
    void hold_requires_a_reason() {
        assertThatThrownBy(() -> assigned().hold("  ", metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hold reason");
    }

    @Test
    @DisplayName("cancellation needs a reason and is terminal")
    void cancellation_requires_a_reason_and_is_terminal() {
        assertThatThrownBy(() -> assigned().cancel(null, NOW, metadata()))
                .isInstanceOf(IllegalArgumentException.class);

        Trip cancelled = assigned().cancel("Purpose no longer required", NOW, metadata());
        assertThat(cancelled.status()).isEqualTo(TripStatus.CANCELLED);
        assertThat(cancelled.holdsAssignment()).isFalse();

        assertThatThrownBy(() -> cancelled.assign(VEHICLE_ID, DRIVER_ID, metadata()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("closure without a reason is blocked with the SRS wording")
    void closure_requires_a_reason() {
        Trip inProgress = assigned().start(NOW, 42_000L, metadata());

        assertThatThrownBy(() -> inProgress.close("  ", EVIDENCE_ID, 42_500L, NOW, metadata()))
                .isInstanceOf(ClosureEvidenceMissingException.class)
                .hasMessage(FleetErrorCode.FLEET_CLOSURE_EVIDENCE_MISSING.message());
    }

    @Test
    @DisplayName("closure without evidence is blocked with the SRS wording")
    void closure_requires_evidence() {
        Trip inProgress = assigned().start(NOW, 42_000L, metadata());

        assertThatThrownBy(() -> inProgress.close("Delivered", null, 42_500L, NOW, metadata()))
                .isInstanceOf(ClosureEvidenceMissingException.class)
                .hasMessage(FleetErrorCode.FLEET_CLOSURE_EVIDENCE_MISSING.message())
                .extracting(exception -> ((ClosureEvidenceMissingException) exception).details())
                .satisfies(details -> assertThat(details).containsEntry("missing", "closureEvidenceId"));
    }

    @Test
    @DisplayName("closure rejects an end odometer below the start reading")
    void closure_rejects_a_regressing_odometer() {
        Trip inProgress = assigned().start(NOW, 42_000L, metadata());

        assertThatThrownBy(() -> inProgress.close("Delivered", EVIDENCE_ID, 41_000L, NOW, metadata()))
                .isInstanceOf(OdometerRegressionException.class);
    }

    @Test
    @DisplayName("a valid closure completes the trip and records the distance covered")
    void valid_closure_completes_the_trip() {
        Trip closed = assigned().start(NOW, 42_000L, metadata())
                .close("Delivered examination materials", EVIDENCE_ID, 42_480L,
                        NOW.plus(Duration.ofHours(4)), metadata());

        assertThat(closed.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(closed.closureReason()).isEqualTo("Delivered examination materials");
        assertThat(closed.closureEvidenceId()).isEqualTo(EVIDENCE_ID);
        assertThat(closed.endOdometer()).isEqualTo(42_480L);
        assertThat(closed.distanceCovered()).isEqualTo(480L);
        assertThat(closed.holdsAssignment()).isFalse();
    }

    @Test
    @DisplayName("a completed trip accepts no further transition")
    void completed_trip_is_terminal() {
        Trip closed = assigned().start(NOW, 42_000L, metadata())
                .close("Delivered", EVIDENCE_ID, 42_480L, NOW, metadata());

        assertThatThrownBy(() -> closed.hold("Anything", metadata()))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> closed.cancel("Anything", NOW, metadata()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("the planned period must be a positive duration")
    void planned_period_must_be_positive() {
        assertThatThrownBy(() -> DateTimeRange.of(NOW.plus(Duration.ofHours(5)), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Trip plan() {
        return Trip.plan(UUID.fromString("33333333-3333-3333-3333-333333333333"), "TRP-00000001", ACCRA,
                "Deliver examination materials", "Accra HQ", "Kumasi Centre", OperatingMode.EXAMINATION,
                PERIOD, metadata());
    }

    private static Trip assigned() {
        return plan().assign(VEHICLE_ID, DRIVER_ID, metadata());
    }
}
