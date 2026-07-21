package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.ACCRA;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.KUMASI;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.TODAY;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.metadata;
import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverEligibilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EligibilityAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceDetails;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlockerCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S166-05 "driver eligibility blockers"; gates assignment under SRS-SFL-S166-02. */
class DriverEligibilityPolicyTest {

    private static final Duration WARNING_WINDOW = Duration.ofDays(30);

    @Test
    @DisplayName("a driver with a current licence covering the vehicle is eligible with no blockers")
    void current_licence_is_eligible() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(driver(TODAY.plusYears(1),
                LicenceClass.C, TODAY.plusYears(1)), VehicleCategory.MINIBUS, NOW, null, WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.ELIGIBLE);
        assertThat(assessment.blockers()).isEmpty();
        assertThat(assessment.permitsAssignment()).isTrue();
    }

    @Test
    @DisplayName("an expired licence blocks the driver and names the expiry date")
    void expired_licence_blocks() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(driver(TODAY.minusDays(1),
                LicenceClass.C, TODAY.plusYears(1)), VehicleCategory.MINIBUS, NOW, null, WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.INELIGIBLE);
        assertThat(assessment.codes()).contains(ReadinessBlockerCode.DRIVER_LICENCE_EXPIRED);
        assertThat(assessment.blockers().get(0).message()).contains(TODAY.minusDays(1).toString());
        assertThat(assessment.permitsAssignment()).isFalse();
    }

    @Test
    @DisplayName("a licence valid today but expiring mid-trip blocks the assignment")
    void licence_expiring_mid_trip_blocks() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(
                driver(TODAY.plusDays(2), LicenceClass.C, TODAY.plusYears(1)),
                VehicleCategory.MINIBUS, NOW, NOW.plus(Duration.ofDays(5)), WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.INELIGIBLE);
        assertThat(assessment.codes()).contains(ReadinessBlockerCode.DRIVER_LICENCE_EXPIRED);
        assertThat(assessment.blockers().get(0).message()).contains("before the end of the requested period");
    }

    @Test
    @DisplayName("a licence expiring inside the warning window makes the driver conditional, not blocked")
    void licence_expiring_soon_is_a_warning() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(driver(TODAY.plusDays(10),
                LicenceClass.C, TODAY.plusYears(1)), VehicleCategory.MINIBUS, NOW, null, WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.CONDITIONAL);
        assertThat(assessment.codes()).containsExactly(ReadinessBlockerCode.DRIVER_LICENCE_EXPIRING);
        assertThat(assessment.permitsAssignment()).isTrue();
    }

    @Test
    @DisplayName("a licence class that does not cover the vehicle category blocks the driver")
    void licence_class_mismatch_blocks() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(driver(TODAY.plusYears(1),
                LicenceClass.B, TODAY.plusYears(1)), VehicleCategory.BUS, NOW, null, WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.INELIGIBLE);
        assertThat(assessment.codes()).contains(ReadinessBlockerCode.DRIVER_LICENCE_CLASS_MISMATCH);
        assertThat(assessment.blockers().get(0).message()).contains("B").contains("BUS");
    }

    @Test
    @DisplayName("licence class coverage follows the DVLA hierarchy")
    void licence_class_coverage() {
        assertThat(LicenceClass.B.covers(VehicleCategory.PICKUP)).isTrue();
        assertThat(LicenceClass.B.covers(VehicleCategory.MINIBUS)).isFalse();
        assertThat(LicenceClass.C.covers(VehicleCategory.MINIBUS)).isTrue();
        assertThat(LicenceClass.C.covers(VehicleCategory.BUS)).isFalse();
        assertThat(LicenceClass.D.covers(VehicleCategory.BUS)).isTrue();
        assertThat(LicenceClass.E.covers(VehicleCategory.TRUCK)).isTrue();
        assertThat(LicenceClass.A.covers(VehicleCategory.MOTORCYCLE)).isTrue();
        assertThat(LicenceClass.A.covers(VehicleCategory.SALOON_CAR)).isFalse();
    }

    @Test
    @DisplayName("a suspended driver reports SUSPENDED and quotes the suspension reason")
    void suspended_driver_is_reported_as_suspended() {
        DriverProfileReference suspended = driver(TODAY.plusYears(1), LicenceClass.C, TODAY.plusYears(1))
                .changeLifecycle(DriverLifecycleStatus.SUSPENDED, "Under investigation after an incident",
                        metadata());

        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(suspended, VehicleCategory.MINIBUS, NOW,
                null, WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.SUSPENDED);
        assertThat(assessment.codes()).contains(ReadinessBlockerCode.DRIVER_SUSPENDED);
        assertThat(assessment.blockers().get(0).message()).contains("Under investigation");
        assertThat(assessment.permitsAssignment()).isFalse();
    }

    @Test
    @DisplayName("an inactive or archived driver is not eligible")
    void inactive_driver_is_not_eligible() {
        DriverProfileReference inactive = driver(TODAY.plusYears(1), LicenceClass.C, TODAY.plusYears(1))
                .changeLifecycle(DriverLifecycleStatus.INACTIVE, null, metadata());

        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(inactive, VehicleCategory.MINIBUS, NOW,
                null, WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.INELIGIBLE);
        assertThat(assessment.codes()).contains(ReadinessBlockerCode.DRIVER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("expired medical clearance blocks the driver")
    void expired_medical_clearance_blocks() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(driver(TODAY.plusYears(1),
                LicenceClass.C, TODAY.minusDays(1)), VehicleCategory.MINIBUS, NOW, null, WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.INELIGIBLE);
        assertThat(assessment.codes()).contains(ReadinessBlockerCode.DRIVER_MEDICAL_CLEARANCE_EXPIRED);
    }

    @Test
    @DisplayName("medical clearance expiring soon is a warning, not a block")
    void medical_clearance_expiring_soon_is_a_warning() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(driver(TODAY.plusYears(1),
                LicenceClass.C, TODAY.plusDays(5)), VehicleCategory.MINIBUS, NOW, null, WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.CONDITIONAL);
        assertThat(assessment.codes()).containsExactly(ReadinessBlockerCode.DRIVER_MEDICAL_CLEARANCE_EXPIRING);
    }

    @Test
    @DisplayName("a driver with no recorded medical clearance is not blocked for that reason alone")
    void absent_medical_clearance_is_not_a_finding() {
        DriverProfileReference noClearance = driver(TODAY.plusYears(1), LicenceClass.C, null);

        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(noClearance, VehicleCategory.MINIBUS,
                NOW, null, WARNING_WINDOW, null);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.ELIGIBLE);
    }

    @Test
    @DisplayName("a driver from another site is blocked when a site is required")
    void cross_site_driver_is_blocked() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(driver(TODAY.plusYears(1),
                LicenceClass.C, TODAY.plusYears(1)), VehicleCategory.MINIBUS, NOW, null, WARNING_WINDOW, KUMASI);

        assertThat(assessment.codes()).contains(ReadinessBlockerCode.DRIVER_SITE_RESTRICTION);
        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.INELIGIBLE);
    }

    @Test
    @DisplayName("every applicable blocker is reported, not just the first")
    void all_applicable_blockers_are_reported() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assess(driver(TODAY.minusDays(1),
                LicenceClass.B, TODAY.minusDays(1)), VehicleCategory.BUS, NOW, null, WARNING_WINDOW, KUMASI);

        assertThat(assessment.codes()).contains(
                ReadinessBlockerCode.DRIVER_LICENCE_EXPIRED,
                ReadinessBlockerCode.DRIVER_LICENCE_CLASS_MISMATCH,
                ReadinessBlockerCode.DRIVER_MEDICAL_CLEARANCE_EXPIRED,
                ReadinessBlockerCode.DRIVER_SITE_RESTRICTION);
    }

    @Test
    @DisplayName("a general assessment ignores the vehicle category")
    void general_assessment_ignores_category() {
        EligibilityAssessment assessment = DriverEligibilityPolicy.assessGeneral(
                driver(TODAY.plusYears(1), LicenceClass.A, TODAY.plusYears(1)), NOW, WARNING_WINDOW);

        assertThat(assessment.status()).isEqualTo(DriverEligibilityStatus.ELIGIBLE);
        assertThat(assessment.assessedForCategory()).isNull();
    }

    private static DriverProfileReference driver(LocalDate licenceExpiry, LicenceClass licenceClass,
            LocalDate medicalClearanceExpiry) {
        return driver(licenceExpiry, licenceClass, medicalClearanceExpiry, ACCRA);
    }

    private static DriverProfileReference driver(LocalDate licenceExpiry, LicenceClass licenceClass,
            LocalDate medicalClearanceExpiry, SiteCode site) {
        return DriverProfileReference.register(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "CLET/HR/00123",
                "Kwame Mensah",
                new LicenceDetails("GHA-DL-4477201", licenceClass, licenceExpiry),
                medicalClearanceExpiry,
                site,
                "Transportation & Logistics Unit",
                metadata());
    }
}
