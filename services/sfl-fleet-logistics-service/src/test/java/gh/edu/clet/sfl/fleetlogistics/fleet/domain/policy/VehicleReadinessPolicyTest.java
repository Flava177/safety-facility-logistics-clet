package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.ACCRA;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.KUMASI;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.TODAY;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.metadata;
import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DateTimeRange;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DefectSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionFinding;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceDetails;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlockerCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Readiness is derived from explicit blockers, never stored as a flag.
 *
 * <p>Traces: SRS-SFL-S166-01, SRS-SFL-S166-02 (assignment gating), SRS-SFL-S166-05 (readiness
 * blockers, ready / conditionally ready / unavailable).
 */
class VehicleReadinessPolicyTest {

    private static final Duration WARNING_WINDOW = Duration.ofDays(30);
    private static final Duration INSPECTION_VALIDITY = Duration.ofDays(1);
    private static final Duration ODOMETER_STALENESS = Duration.ofDays(30);
    private static final DateTimeRange TOMORROW_MORNING = DateTimeRange.of(
            NOW.plus(Duration.ofHours(24)), NOW.plus(Duration.ofHours(28)));

    @Test
    @DisplayName("a compliant, serviced, uncommitted vehicle is ready")
    void fully_compliant_vehicle_is_ready() {
        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(context(FleetFixtures.vehicle(),
                allMandatoryDocuments(), null, null, null, null));

        assertThat(assessment.status()).isEqualTo(ReadinessStatus.READY);
        assertThat(assessment.blockers()).isEmpty();
        assertThat(assessment.permitsAssignment()).isTrue();
    }

    @Test
    @DisplayName("a missing mandatory document blocks readiness and names the document type")
    void missing_mandatory_document_blocks() {
        List<ComplianceDocument> partial = List.of(
                document(ComplianceDocumentType.ROADWORTHINESS_CERTIFICATE, TODAY.plusMonths(6)));

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(context(FleetFixtures.vehicle(), partial,
                null, null, null, null));

        assertThat(assessment.status()).isEqualTo(ReadinessStatus.NOT_READY);
        assertThat(assessment.blockingCodes()).contains(ReadinessBlockerCode.COMPLIANCE_DOCUMENT_MISSING);
        assertThat(assessment.blockers()).anySatisfy(blocker ->
                assertThat(blocker.message()).contains("INSURANCE_CERTIFICATE"));
    }

    @Test
    @DisplayName("an expired document blocks readiness")
    void expired_document_blocks() {
        List<ComplianceDocument> documents = List.of(
                document(ComplianceDocumentType.ROADWORTHINESS_CERTIFICATE, TODAY.plusMonths(6)),
                document(ComplianceDocumentType.INSURANCE_CERTIFICATE, TODAY.minusDays(1)),
                document(ComplianceDocumentType.VEHICLE_REGISTRATION, TODAY.plusYears(1)));

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(context(FleetFixtures.vehicle(), documents,
                null, null, null, null));

        assertThat(assessment.status()).isEqualTo(ReadinessStatus.NOT_READY);
        assertThat(assessment.blockingCodes()).contains(ReadinessBlockerCode.COMPLIANCE_DOCUMENT_EXPIRED);
    }

    @Test
    @DisplayName("a document that lapses mid-trip blocks the assignment")
    void document_expiring_mid_trip_blocks() {
        List<ComplianceDocument> documents = List.of(
                document(ComplianceDocumentType.ROADWORTHINESS_CERTIFICATE, TODAY.plusMonths(6)),
                document(ComplianceDocumentType.INSURANCE_CERTIFICATE, TODAY),
                document(ComplianceDocumentType.VEHICLE_REGISTRATION, TODAY.plusYears(1)));

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(new ReadinessContext(
                FleetFixtures.vehicle(), null, documents, null, null, List.of(), List.of(),
                DateTimeRange.of(NOW.plus(Duration.ofDays(2)), NOW.plus(Duration.ofDays(3))),
                OperatingMode.ROUTINE, null, NOW, WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS,
                true, false));

        assertThat(assessment.blockingCodes()).contains(ReadinessBlockerCode.COMPLIANCE_DOCUMENT_EXPIRED);
        assertThat(assessment.blockers()).anySatisfy(blocker ->
                assertThat(blocker.message()).contains("before the end of the requested period"));
    }

    @Test
    @DisplayName("a document expiring inside the warning window makes the vehicle conditionally ready")
    void document_expiring_soon_is_conditionally_ready() {
        List<ComplianceDocument> documents = List.of(
                document(ComplianceDocumentType.ROADWORTHINESS_CERTIFICATE, TODAY.plusDays(10)),
                document(ComplianceDocumentType.INSURANCE_CERTIFICATE, TODAY.plusYears(1)),
                document(ComplianceDocumentType.VEHICLE_REGISTRATION, TODAY.plusYears(1)));

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(context(FleetFixtures.vehicle(), documents,
                null, null, null, null));

        assertThat(assessment.status()).isEqualTo(ReadinessStatus.CONDITIONALLY_READY);
        assertThat(assessment.codes()).containsExactly(ReadinessBlockerCode.COMPLIANCE_DOCUMENT_EXPIRING);
        assertThat(assessment.permitsAssignment()).isTrue();
    }

    @Test
    @DisplayName("an inactive, suspended or archived vehicle is never ready")
    void non_active_lifecycle_blocks() {
        assertThat(VehicleReadinessPolicy.assess(context(
                FleetFixtures.vehicle().changeLifecycle(VehicleLifecycleStatus.INACTIVE, metadata()),
                allMandatoryDocuments(), null, null, null, null)).blockingCodes())
                .contains(ReadinessBlockerCode.VEHICLE_NOT_ACTIVE);

        assertThat(VehicleReadinessPolicy.assess(context(
                FleetFixtures.vehicle().changeLifecycle(VehicleLifecycleStatus.SUSPENDED, metadata()),
                allMandatoryDocuments(), null, null, null, null)).blockingCodes())
                .contains(ReadinessBlockerCode.VEHICLE_SUSPENDED);

        assertThat(VehicleReadinessPolicy.assess(context(
                FleetFixtures.vehicle().changeLifecycle(VehicleLifecycleStatus.ARCHIVED, metadata()),
                allMandatoryDocuments(), null, null, null, null)).blockingCodes())
                .contains(ReadinessBlockerCode.VEHICLE_ARCHIVED);
    }

    @Test
    @DisplayName("overdue service blocks; service due soon only warns")
    void service_status_drives_readiness() {
        Vehicle overdue = FleetFixtures.vehicle().withServiceStatus(VehicleServiceStatus.OVERDUE, metadata());
        Vehicle due = FleetFixtures.vehicle().withServiceStatus(VehicleServiceStatus.DUE, metadata());

        assertThat(VehicleReadinessPolicy.assess(context(overdue, allMandatoryDocuments(), null, null, null, null))
                .status()).isEqualTo(ReadinessStatus.NOT_READY);
        assertThat(VehicleReadinessPolicy.assess(context(due, allMandatoryDocuments(), null, null, null, null))
                .status()).isEqualTo(ReadinessStatus.CONDITIONALLY_READY);
    }

    @Test
    @DisplayName("a failed inspection blocks readiness")
    void failed_inspection_blocks() {
        VehicleInspection failed = inspection(DefectSeverity.MAJOR);

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(context(FleetFixtures.vehicle(),
                allMandatoryDocuments(), failed, null, null, null));

        assertThat(assessment.blockingCodes()).contains(ReadinessBlockerCode.INSPECTION_FAILED);
        assertThat(assessment.status()).isEqualTo(ReadinessStatus.NOT_READY);
    }

    @Test
    @DisplayName("an unresolved critical defect blocks readiness and lists the defect")
    void open_critical_defect_blocks() {
        VehicleInspection critical = inspection(DefectSeverity.CRITICAL);

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(context(FleetFixtures.vehicle(),
                allMandatoryDocuments(), critical, null, null, null));

        assertThat(assessment.blockingCodes()).contains(ReadinessBlockerCode.OPEN_CRITICAL_DEFECT);
        assertThat(assessment.blockers()).anySatisfy(blocker ->
                assertThat(blocker.context()).containsKey("defects"));
    }

    @Test
    @DisplayName("resolving the defect clears the critical-defect blocker")
    void resolved_defect_clears_the_blocker() {
        VehicleInspection resolved = inspection(DefectSeverity.CRITICAL)
                .resolveDefect("BRAKES", "WO-2026-0099", metadata());

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(context(FleetFixtures.vehicle(),
                allMandatoryDocuments(), resolved, null, null, null));

        assertThat(assessment.codes()).doesNotContain(ReadinessBlockerCode.OPEN_CRITICAL_DEFECT);
    }

    @Test
    @DisplayName("a missing inspection blocks only when an inspection is required")
    void missing_inspection_blocks_only_when_required() {
        ReadinessContext notRequired = context(FleetFixtures.vehicle(), allMandatoryDocuments(), null, null, null,
                null);
        assertThat(VehicleReadinessPolicy.assess(notRequired).codes())
                .doesNotContain(ReadinessBlockerCode.MANDATORY_INSPECTION_MISSING);

        ReadinessContext required = new ReadinessContext(FleetFixtures.vehicle(), null, allMandatoryDocuments(),
                null, null, List.of(), List.of(), TOMORROW_MORNING, OperatingMode.ROUTINE, null, NOW,
                WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS, true, true);
        assertThat(VehicleReadinessPolicy.assess(required).blockingCodes())
                .contains(ReadinessBlockerCode.MANDATORY_INSPECTION_MISSING);
    }

    @Test
    @DisplayName("an inspection older than the validity window no longer satisfies the requirement")
    void stale_inspection_no_longer_counts() {
        VehicleInspection old = VehicleInspection.record(UUID.randomUUID(),
                FleetFixtures.vehicle().id(), null, ACCRA, InspectionType.PRE_TRIP, "driver@clet.edu.gh",
                NOW.minus(Duration.ofDays(3)), 42_000L, UUID.randomUUID(), List.of(), null, metadata());

        ReadinessContext required = new ReadinessContext(FleetFixtures.vehicle(), null, allMandatoryDocuments(),
                null, old, List.of(), List.of(), TOMORROW_MORNING, OperatingMode.ROUTINE, null, NOW,
                WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS, true, true);

        assertThat(VehicleReadinessPolicy.assess(required).blockingCodes())
                .contains(ReadinessBlockerCode.MANDATORY_INSPECTION_MISSING);
    }

    @Test
    @DisplayName("an overlapping vehicle or driver assignment blocks readiness and names the trip")
    void assignment_conflicts_block() {
        ReadinessContext conflicting = new ReadinessContext(FleetFixtures.vehicle(), eligibleDriver(),
                allMandatoryDocuments(), null, null, List.of("trip-1"), List.of("trip-2"), TOMORROW_MORNING,
                OperatingMode.ROUTINE, null, NOW, WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS,
                true, false);

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(conflicting);

        assertThat(assessment.blockingCodes()).contains(ReadinessBlockerCode.VEHICLE_ASSIGNMENT_CONFLICT,
                ReadinessBlockerCode.DRIVER_ASSIGNMENT_CONFLICT);
        assertThat(assessment.blockers()).anySatisfy(blocker ->
                assertThat(blocker.context().toString()).contains("trip-1"));
    }

    @Test
    @DisplayName("assessing an assignment period with no driver reports the missing driver")
    void missing_driver_blocks_a_period_assessment() {
        ReadinessContext withPeriod = new ReadinessContext(FleetFixtures.vehicle(), null, allMandatoryDocuments(),
                null, null, List.of(), List.of(), TOMORROW_MORNING, OperatingMode.ROUTINE, null, NOW,
                WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS, true, false);

        assertThat(VehicleReadinessPolicy.assess(withPeriod).blockingCodes())
                .contains(ReadinessBlockerCode.DRIVER_MISSING);
    }

    @Test
    @DisplayName("an ineligible driver blocks the assignment and their own findings are surfaced")
    void ineligible_driver_blocks_and_surfaces_detail() {
        DriverProfileReference expiredLicence = driverWithLicenceExpiring(TODAY.minusDays(1));

        ReadinessContext withDriver = new ReadinessContext(FleetFixtures.vehicle(), expiredLicence,
                allMandatoryDocuments(), null, null, List.of(), List.of(), TOMORROW_MORNING,
                OperatingMode.ROUTINE, null, NOW, WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS,
                true, false);

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(withDriver);

        assertThat(assessment.blockingCodes()).contains(ReadinessBlockerCode.DRIVER_INELIGIBLE,
                ReadinessBlockerCode.DRIVER_LICENCE_EXPIRED);
        assertThat(assessment.driverId()).isEqualTo(expiredLicence.id());
    }

    @Test
    @DisplayName("a vehicle from another site is blocked when a site is required")
    void cross_site_vehicle_is_blocked() {
        ReadinessContext crossSite = new ReadinessContext(FleetFixtures.vehicle(), null, allMandatoryDocuments(),
                null, null, List.of(), List.of(), null, OperatingMode.ROUTINE, KUMASI, NOW, WARNING_WINDOW,
                INSPECTION_VALIDITY, ODOMETER_STALENESS, true, false);

        assertThat(VehicleReadinessPolicy.assess(crossSite).blockingCodes())
                .contains(ReadinessBlockerCode.SITE_RESTRICTION);
    }

    @Test
    @DisplayName("an emergency-only vehicle is blocked for routine work but ready for an emergency")
    void emergency_only_vehicle_is_restricted() {
        Vehicle ambulance = FleetFixtures.emergencyOnlyVehicle();

        ReadinessAssessment routine = VehicleReadinessPolicy.assess(new ReadinessContext(ambulance, null,
                allMandatoryDocuments(ambulance.id()), null, null, List.of(), List.of(), null,
                OperatingMode.ROUTINE, null, NOW, WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS,
                true, false));
        assertThat(routine.blockingCodes()).contains(ReadinessBlockerCode.EMERGENCY_ONLY_RESTRICTION);

        ReadinessAssessment emergency = VehicleReadinessPolicy.assess(new ReadinessContext(ambulance, null,
                allMandatoryDocuments(ambulance.id()), null, null, List.of(), List.of(), null,
                OperatingMode.EMERGENCY, null, NOW, WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS,
                true, false));
        assertThat(emergency.status()).isEqualTo(ReadinessStatus.READY);
    }

    @Test
    @DisplayName("missing required evidence blocks readiness")
    void missing_required_evidence_blocks() {
        ReadinessContext missingEvidence = new ReadinessContext(FleetFixtures.vehicle(), null,
                allMandatoryDocuments(), null, null, List.of(), List.of(), null, OperatingMode.ROUTINE, null, NOW,
                WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS, false, false);

        assertThat(VehicleReadinessPolicy.assess(missingEvidence).blockingCodes())
                .contains(ReadinessBlockerCode.MISSING_REQUIRED_EVIDENCE);
    }

    @Test
    @DisplayName("a stale odometer reading is a warning about provenance, not a block")
    void stale_odometer_is_a_warning() {
        ReadinessContext stale = new ReadinessContext(FleetFixtures.vehicle(), null, allMandatoryDocuments(),
                null, null, List.of(), List.of(), null, OperatingMode.ROUTINE, null,
                NOW.plus(Duration.ofDays(60)), WARNING_WINDOW, INSPECTION_VALIDITY, ODOMETER_STALENESS,
                true, false);

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(stale);

        assertThat(assessment.codes()).contains(ReadinessBlockerCode.ODOMETER_PROVENANCE_STALE);
        assertThat(assessment.blockers()).anySatisfy(blocker ->
                assertThat(blocker.severity())
                        .isEqualTo(gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.BlockerSeverity.WARNING));
    }

    @Test
    @DisplayName("every applicable blocker is collected, not only the first")
    void all_blockers_are_collected() {
        Vehicle broken = FleetFixtures.vehicle()
                .withServiceStatus(VehicleServiceStatus.OVERDUE, metadata())
                .changeLifecycle(VehicleLifecycleStatus.INACTIVE, metadata());

        ReadinessAssessment assessment = VehicleReadinessPolicy.assess(context(broken, List.of(),
                inspection(DefectSeverity.CRITICAL), null, null, null));

        assertThat(assessment.blockingCodes()).contains(
                ReadinessBlockerCode.VEHICLE_NOT_ACTIVE,
                ReadinessBlockerCode.SERVICE_OVERDUE,
                ReadinessBlockerCode.COMPLIANCE_DOCUMENT_MISSING,
                ReadinessBlockerCode.INSPECTION_FAILED,
                ReadinessBlockerCode.OPEN_CRITICAL_DEFECT);
        // All three mandatory document types are reported, not just the first one found missing.
        assertThat(assessment.blockers().stream()
                .filter(blocker -> blocker.code() == ReadinessBlockerCode.COMPLIANCE_DOCUMENT_MISSING)
                .count()).isEqualTo(3);
    }

    // --- fixtures ------------------------------------------------------------------------

    private static ReadinessContext context(Vehicle vehicle, List<ComplianceDocument> documents,
            VehicleInspection inspection, DriverProfileReference driver, DateTimeRange period,
            OperatingMode mode) {
        return new ReadinessContext(vehicle, driver, documents, null, inspection, List.of(), List.of(), period,
                mode == null ? OperatingMode.ROUTINE : mode, null, NOW, WARNING_WINDOW, INSPECTION_VALIDITY,
                ODOMETER_STALENESS, true, false);
    }

    private static List<ComplianceDocument> allMandatoryDocuments() {
        return allMandatoryDocuments(FleetFixtures.vehicle().id());
    }

    private static List<ComplianceDocument> allMandatoryDocuments(UUID vehicleId) {
        return List.of(
                document(vehicleId, ComplianceDocumentType.ROADWORTHINESS_CERTIFICATE, TODAY.plusMonths(6)),
                document(vehicleId, ComplianceDocumentType.INSURANCE_CERTIFICATE, TODAY.plusMonths(8)),
                document(vehicleId, ComplianceDocumentType.VEHICLE_REGISTRATION, TODAY.plusYears(2)));
    }

    private static ComplianceDocument document(ComplianceDocumentType type, LocalDate expiresOn) {
        return document(FleetFixtures.vehicle().id(), type, expiresOn);
    }

    private static ComplianceDocument document(UUID vehicleId, ComplianceDocumentType type, LocalDate expiresOn) {
        return FleetFixtures.complianceDocument(vehicleId, type, TODAY.minusYears(1), expiresOn);
    }

    private static VehicleInspection inspection(DefectSeverity severity) {
        return VehicleInspection.record(UUID.randomUUID(), FleetFixtures.vehicle().id(), null, ACCRA,
                InspectionType.PRE_TRIP, "driver@clet.edu.gh", NOW.minus(Duration.ofHours(2)), 42_000L,
                UUID.randomUUID(), List.of(InspectionFinding.of("BRAKES", "Brake pads worn", severity)), null,
                metadata());
    }

    private static DriverProfileReference eligibleDriver() {
        return driverWithLicenceExpiring(TODAY.plusYears(1));
    }

    private static DriverProfileReference driverWithLicenceExpiring(LocalDate expiry) {
        return DriverProfileReference.register(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "CLET/HR/00123", "Kwame Mensah",
                new LicenceDetails("GHA-DL-4477201", LicenceClass.C, expiry), TODAY.plusYears(1), ACCRA,
                "Transportation & Logistics Unit", "CLET/HR/00123", metadata());
    }
}
