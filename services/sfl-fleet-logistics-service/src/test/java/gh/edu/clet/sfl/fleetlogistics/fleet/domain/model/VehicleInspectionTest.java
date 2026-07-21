package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.ACCRA;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.metadata;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ClosureEvidenceMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Traces: SRS-SFL-S166-01 (an inspection is an operational record carrying service status and
 * availability evidence) and SRS-SFL-S166-02 (the evidence-bearing workflow step that can block).
 * Gap report C-01 records why no "S166-06" identifier is used.
 */
class VehicleInspectionTest {

    private static final UUID VEHICLE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @DisplayName("an inspection with no findings passes")
    void clean_inspection_passes() {
        VehicleInspection inspection = record(List.of(), UUID.randomUUID());

        assertThat(inspection.result()).isEqualTo(InspectionResult.PASSED);
        assertThat(inspection.status()).isEqualTo(InspectionStatus.SUBMITTED);
        assertThat(inspection.permitsUse()).isTrue();
        assertThat(inspection.hasOpenCriticalDefect()).isFalse();
    }

    @Test
    @DisplayName("advisory findings alone still pass")
    void advisory_findings_still_pass() {
        VehicleInspection inspection = record(
                List.of(InspectionFinding.of("TYRES", "Tread nearing the limit", DefectSeverity.ADVISORY)),
                UUID.randomUUID());

        assertThat(inspection.result()).isEqualTo(InspectionResult.PASSED);
        assertThat(inspection.permitsUse()).isTrue();
    }

    @Test
    @DisplayName("a minor finding passes with defects recorded")
    void minor_finding_passes_with_defects() {
        VehicleInspection inspection = record(
                List.of(InspectionFinding.of("WIPERS", "Wiper blade smearing", DefectSeverity.MINOR)),
                UUID.randomUUID());

        assertThat(inspection.result()).isEqualTo(InspectionResult.PASSED_WITH_DEFECTS);
        assertThat(inspection.permitsUse()).isTrue();
    }

    @Test
    @DisplayName("a major or critical finding fails the inspection, whatever else was clean")
    void major_or_critical_finding_fails() {
        assertThat(record(List.of(InspectionFinding.of("STEERING", "Excessive play", DefectSeverity.MAJOR)),
                UUID.randomUUID()).result()).isEqualTo(InspectionResult.FAILED);
        assertThat(record(List.of(InspectionFinding.of("BRAKES", "Brake failure", DefectSeverity.CRITICAL)),
                UUID.randomUUID()).result()).isEqualTo(InspectionResult.FAILED);
    }

    @Test
    @DisplayName("a failing inspection cannot be recorded without evidence")
    void failing_inspection_requires_evidence() {
        assertThatThrownBy(() -> record(
                List.of(InspectionFinding.of("BRAKES", "Brake failure", DefectSeverity.CRITICAL)), null))
                .isInstanceOf(ClosureEvidenceMissingException.class)
                .hasMessage("Required evidence must be attached before closure.");
    }

    @Test
    @DisplayName("a passing inspection does not require evidence")
    void passing_inspection_does_not_require_evidence() {
        assertThat(record(List.of(), null).result()).isEqualTo(InspectionResult.PASSED);
    }

    @Test
    @DisplayName("an unresolved critical defect keeps the vehicle out of use even after the fact")
    void open_critical_defect_prevents_use() {
        VehicleInspection inspection = record(
                List.of(InspectionFinding.of("BRAKES", "Brake failure", DefectSeverity.CRITICAL)),
                UUID.randomUUID());

        assertThat(inspection.hasOpenCriticalDefect()).isTrue();
        assertThat(inspection.permitsUse()).isFalse();
        assertThat(inspection.openCriticalDefects()).hasSize(1);
    }

    @Test
    @DisplayName("resolving the defect clears it without rewriting the inspection result")
    void resolving_a_defect_keeps_the_original_result() {
        VehicleInspection resolved = record(
                List.of(InspectionFinding.of("BRAKES", "Brake failure", DefectSeverity.CRITICAL)),
                UUID.randomUUID())
                .resolveDefect("BRAKES", "WO-2026-0099", metadata());

        assertThat(resolved.hasOpenCriticalDefect()).isFalse();
        // The inspection still failed; what changed is that the defect was rectified afterwards.
        assertThat(resolved.result()).isEqualTo(InspectionResult.FAILED);
    }

    @Test
    @DisplayName("a submitted inspection can be accepted or rejected, but only once")
    void submitted_inspection_can_be_reviewed_once() {
        VehicleInspection submitted = record(List.of(), UUID.randomUUID());

        VehicleInspection accepted = submitted.accept(metadata());
        assertThat(accepted.status()).isEqualTo(InspectionStatus.ACCEPTED);

        assertThatThrownBy(() -> accepted.accept(metadata()))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> accepted.reject(metadata()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("a rejected inspection does not permit use")
    void rejected_inspection_does_not_permit_use() {
        VehicleInspection rejected = record(List.of(), UUID.randomUUID()).reject(metadata());

        assertThat(rejected.status()).isEqualTo(InspectionStatus.REJECTED);
        assertThat(rejected.permitsUse()).isFalse();
    }

    @Test
    @DisplayName("validity is judged against the configured window")
    void validity_window_is_respected() {
        VehicleInspection inspection = record(List.of(), UUID.randomUUID());

        assertThat(inspection.isValidAt(NOW.plus(Duration.ofHours(12)), Duration.ofDays(1))).isTrue();
        assertThat(inspection.isValidAt(NOW.plus(Duration.ofDays(1)), Duration.ofDays(1))).isTrue();
        assertThat(inspection.isValidAt(NOW.plus(Duration.ofDays(2)), Duration.ofDays(1))).isFalse();
    }

    @Test
    @DisplayName("required fields are enforced")
    void required_fields_are_enforced() {
        assertThatThrownBy(() -> VehicleInspection.record(UUID.randomUUID(), VEHICLE_ID, null, ACCRA,
                InspectionType.PRE_TRIP, "  ", NOW, 42_000L, null, List.of(), null, metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("performedBy");

        assertThatThrownBy(() -> VehicleInspection.record(UUID.randomUUID(), VEHICLE_ID, null, ACCRA,
                InspectionType.PRE_TRIP, "driver@clet.edu.gh", NOW, -1L, null, List.of(), null, metadata()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("odometerReading");
    }

    private static VehicleInspection record(List<InspectionFinding> findings, UUID evidenceId) {
        return VehicleInspection.record(UUID.randomUUID(), VEHICLE_ID, null, ACCRA, InspectionType.PRE_TRIP,
                "driver@clet.edu.gh", NOW, 42_000L, evidenceId, findings, "Pre-trip check", metadata());
    }
}
