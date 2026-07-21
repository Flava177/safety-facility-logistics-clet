package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.ACCRA;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.metadata;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.vehicle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ArchivedRecordImmutableException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.MissingSiteScopeException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OdometerRegressionException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S166-01 vehicle record invariants and lifecycle. */
class VehicleTest {

    @Test
    @DisplayName("a registered vehicle starts active, in service and available")
    void registration_starts_active_and_available() {
        Vehicle vehicle = vehicle();

        assertThat(vehicle.lifecycleStatus()).isEqualTo(VehicleLifecycleStatus.ACTIVE);
        assertThat(vehicle.serviceStatus()).isEqualTo(VehicleServiceStatus.IN_SERVICE);
        assertThat(vehicle.availabilityStatus()).isEqualTo(VehicleAvailabilityStatus.AVAILABLE);
        assertThat(vehicle.currentTripId()).isNull();
        assertThat(vehicle.metadata().version()).isZero();
    }

    @Test
    @DisplayName("registration requires a site scope and an operational owner")
    void registration_requires_site_and_owner() {
        assertThatThrownBy(() -> SiteCode.of(null)).isInstanceOf(MissingSiteScopeException.class);

        assertThatThrownBy(() -> Vehicle.register(UUID.randomUUID(), RegistrationNumber.of("GT-1-26"), null,
                new VehicleSpecification("Toyota", "Hilux", 2022, VehicleCategory.PICKUP, 5), ACCRA,
                "  ", "owner@clet.edu.gh", null,
                OdometerReading.of(0, OdometerSource.MANUAL_ENTRY, NOW), RestrictedUse.unrestricted(), metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("responsibleUnit");

        assertThatThrownBy(() -> Vehicle.register(UUID.randomUUID(), RegistrationNumber.of("GT-1-26"), null,
                new VehicleSpecification("Toyota", "Hilux", 2022, VehicleCategory.PICKUP, 5), ACCRA,
                "Logistics", "  ", null,
                OdometerReading.of(0, OdometerSource.MANUAL_ENTRY, NOW), RestrictedUse.unrestricted(), metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operationalOwner");
    }

    @Test
    @DisplayName("the registration number is normalised so duplicates cannot slip through on spacing or case")
    void registration_number_is_normalised() {
        assertThat(RegistrationNumber.of(" gt  1234-26 ").value()).isEqualTo("GT 1234-26");
        assertThat(RegistrationNumber.of("GT 1234-26")).isEqualTo(RegistrationNumber.of("gt 1234-26"));
    }

    @Test
    @DisplayName("an archived vehicle rejects every edit")
    void archived_vehicle_rejects_updates() {
        Vehicle archived = vehicle().changeLifecycle(VehicleLifecycleStatus.ARCHIVED, metadata());

        assertThatThrownBy(() -> archived.updateDetails(null, archived.specification(), "Unit", "owner", null,
                RestrictedUse.unrestricted(), metadata()))
                .isInstanceOf(ArchivedRecordImmutableException.class);
        assertThatThrownBy(() -> archived.recordOdometer(50_000, OdometerSource.MANUAL_ENTRY, NOW, metadata()))
                .isInstanceOf(ArchivedRecordImmutableException.class);
        assertThatThrownBy(() -> archived.assignToTrip(UUID.randomUUID(), metadata()))
                .isInstanceOf(ArchivedRecordImmutableException.class);
    }

    @Test
    @DisplayName("archiving makes the vehicle unavailable")
    void archiving_makes_the_vehicle_unavailable() {
        Vehicle archived = vehicle().changeLifecycle(VehicleLifecycleStatus.ARCHIVED, metadata());

        assertThat(archived.availabilityStatus()).isEqualTo(VehicleAvailabilityStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("a vehicle on an active trip cannot be suspended or archived")
    void vehicle_on_trip_cannot_be_withdrawn() {
        Vehicle assigned = vehicle().assignToTrip(UUID.randomUUID(), metadata());

        assertThatThrownBy(() -> assigned.changeLifecycle(VehicleLifecycleStatus.SUSPENDED, metadata()))
                .isInstanceOf(InvalidStateTransitionException.class)
                .extracting(exception -> ((InvalidStateTransitionException) exception).details())
                .satisfies(details -> assertThat(details).containsEntry("reason", "The vehicle is on an active trip"));

        assertThatThrownBy(() -> assigned.changeLifecycle(VehicleLifecycleStatus.ARCHIVED, metadata()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("assignment marks the vehicle assigned and releasing returns it to available")
    void assignment_and_release_move_availability() {
        UUID tripId = UUID.randomUUID();
        Vehicle assigned = vehicle().assignToTrip(tripId, metadata());

        assertThat(assigned.availabilityStatus()).isEqualTo(VehicleAvailabilityStatus.ASSIGNED);
        assertThat(assigned.currentTripId()).isEqualTo(tripId);
        assertThat(assigned.isOnTrip()).isTrue();

        Vehicle inUse = assigned.markInUse(metadata());
        assertThat(inUse.availabilityStatus()).isEqualTo(VehicleAvailabilityStatus.IN_USE);

        Vehicle released = inUse.releaseFromTrip(metadata());
        assertThat(released.availabilityStatus()).isEqualTo(VehicleAvailabilityStatus.AVAILABLE);
        assertThat(released.currentTripId()).isNull();
    }

    @Test
    @DisplayName("an inactive vehicle cannot be assigned")
    void inactive_vehicle_cannot_be_assigned() {
        Vehicle inactive = vehicle().changeLifecycle(VehicleLifecycleStatus.INACTIVE, metadata());

        assertThatThrownBy(() -> inactive.assignToTrip(UUID.randomUUID(), metadata()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("an overdue service makes the vehicle unavailable even while active")
    void overdue_service_makes_the_vehicle_unavailable() {
        Vehicle overdue = vehicle().withServiceStatus(VehicleServiceStatus.OVERDUE, metadata());

        assertThat(overdue.lifecycleStatus()).isEqualTo(VehicleLifecycleStatus.ACTIVE);
        assertThat(overdue.availabilityStatus()).isEqualTo(VehicleAvailabilityStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("returning to service restores availability")
    void returning_to_service_restores_availability() {
        Vehicle restored = vehicle()
                .withServiceStatus(VehicleServiceStatus.OUT_OF_SERVICE, metadata())
                .withServiceStatus(VehicleServiceStatus.IN_SERVICE, metadata());

        assertThat(restored.availabilityStatus()).isEqualTo(VehicleAvailabilityStatus.AVAILABLE);
    }

    @Test
    @DisplayName("the odometer only moves forward")
    void odometer_never_regresses() {
        Vehicle vehicle = vehicle();

        Vehicle advanced = vehicle.recordOdometer(45_000, OdometerSource.INSPECTION, NOW, metadata());
        assertThat(advanced.odometer().value()).isEqualTo(45_000);
        assertThat(advanced.odometer().source()).isEqualTo(OdometerSource.INSPECTION);

        assertThatThrownBy(() -> advanced.recordOdometer(44_000, OdometerSource.MANUAL_ENTRY, NOW, metadata()))
                .isInstanceOf(OdometerRegressionException.class)
                .extracting(exception -> ((OdometerRegressionException) exception).details())
                .satisfies(details -> {
                    assertThat(details).containsEntry("currentReading", 45_000L);
                    assertThat(details).containsEntry("submittedReading", 44_000L);
                });
    }

    @Test
    @DisplayName("an authorised correction is the only way a reading moves backwards")
    void authorised_correction_may_lower_the_reading() {
        Vehicle corrected = vehicle().correctOdometer(4_200, NOW, metadata());

        assertThat(corrected.odometer().value()).isEqualTo(4_200);
        assertThat(corrected.odometer().source()).isEqualTo(OdometerSource.AUTHORISED_CORRECTION);
    }

    @Test
    @DisplayName("the VIN is masked to its last four characters")
    void vin_masks_to_the_last_four_characters() {
        VehicleIdentificationNumber vin = VehicleIdentificationNumber.ofNullable("WVWZZZ1JZXW000001");

        assertThat(vin.masked()).endsWith("0001");
        assertThat(vin.masked()).doesNotContain("WVWZZZ");
        assertThat(vin.masked()).hasSameSizeAs(vin.value());
        assertThat(VehicleIdentificationNumber.ofNullable("  ")).isNull();
    }

    @Test
    @DisplayName("restricted use keeps an emergency-only vehicle out of routine work")
    void emergency_only_vehicle_is_restricted() {
        RestrictedUse restricted = RestrictedUse.forEmergencyUseOnly();

        assertThat(restricted.permits(OperatingMode.EMERGENCY)).isTrue();
        assertThat(restricted.permits(OperatingMode.ROUTINE)).isFalse();
        assertThat(restricted.violatesEmergencyOnlyRule(OperatingMode.ROUTINE)).isTrue();
        assertThat(restricted.violatesEmergencyOnlyRule(OperatingMode.EMERGENCY)).isFalse();
        assertThat(RestrictedUse.unrestricted().violatesEmergencyOnlyRule(OperatingMode.ROUTINE)).isFalse();
    }
}
