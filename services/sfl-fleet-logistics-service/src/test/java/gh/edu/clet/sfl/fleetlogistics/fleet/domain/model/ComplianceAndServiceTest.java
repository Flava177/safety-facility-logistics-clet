package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.ACCRA;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.TODAY;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.metadata;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S166-01 compliance documents, service status and service history. */
class ComplianceAndServiceTest {

    private static final UUID VEHICLE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Duration WARNING_WINDOW = Duration.ofDays(30);

    @Nested
    @DisplayName("ComplianceDocument")
    class ComplianceDocumentTest {

        @Test
        @DisplayName("a document well inside its validity is active")
        void document_well_inside_validity_is_active() {
            ComplianceDocument document = register(TODAY.minusMonths(6), TODAY.plusMonths(6));

            assertThat(document.status()).isEqualTo(ComplianceDocumentStatus.ACTIVE);
            assertThat(document.isExpiredAt(NOW)).isFalse();
            assertThat(document.daysUntilExpiry(NOW)).isGreaterThan(30);
        }

        @Test
        @DisplayName("a document inside the warning window is expiring")
        void document_inside_the_warning_window_is_expiring() {
            ComplianceDocument document = register(TODAY.minusMonths(11), TODAY.plusDays(10));

            assertThat(document.status()).isEqualTo(ComplianceDocumentStatus.EXPIRING);
            assertThat(document.status().isCurrent()).isTrue();
            assertThat(document.daysUntilExpiry(NOW)).isEqualTo(10);
        }

        @Test
        @DisplayName("a document that expires today is still valid today")
        void a_document_expiring_today_is_still_valid() {
            ComplianceDocument document = register(TODAY.minusYears(1), TODAY);

            assertThat(document.isExpiredAt(NOW)).isFalse();
            assertThat(document.status()).isEqualTo(ComplianceDocumentStatus.EXPIRING);
        }

        @Test
        @DisplayName("a document that expired yesterday is expired")
        void a_document_expiring_yesterday_is_expired() {
            ComplianceDocument document = register(TODAY.minusYears(1), TODAY.minusDays(1));

            assertThat(document.status()).isEqualTo(ComplianceDocumentStatus.EXPIRED);
            assertThat(document.status().isCurrent()).isFalse();
            assertThat(document.isExpiredAt(NOW)).isTrue();
            assertThat(document.daysUntilExpiry(NOW)).isNegative();
        }

        @Test
        @DisplayName("reassessment moves a document from active to expiring as the date approaches")
        void reassessment_moves_active_to_expiring() {
            ComplianceDocument document = register(TODAY.minusMonths(6), TODAY.plusDays(40));
            assertThat(document.status()).isEqualTo(ComplianceDocumentStatus.ACTIVE);

            ComplianceDocument reassessed = document.reassessAt(NOW.plus(Duration.ofDays(20)), WARNING_WINDOW,
                    metadata());

            assertThat(reassessed.status()).isEqualTo(ComplianceDocumentStatus.EXPIRING);
        }

        @Test
        @DisplayName("reassessment leaves a superseded or revoked document alone")
        void reassessment_respects_decisions() {
            ComplianceDocument superseded = register(TODAY.minusMonths(6), TODAY.plusMonths(6))
                    .supersede(metadata());
            ComplianceDocument revoked = register(TODAY.minusMonths(6), TODAY.plusMonths(6)).revoke(metadata());

            assertThat(superseded.reassessAt(NOW.plus(Duration.ofDays(400)), WARNING_WINDOW, metadata()).status())
                    .isEqualTo(ComplianceDocumentStatus.SUPERSEDED);
            assertThat(revoked.reassessAt(NOW.plus(Duration.ofDays(400)), WARNING_WINDOW, metadata()).status())
                    .isEqualTo(ComplianceDocumentStatus.REVOKED);
        }

        @Test
        @DisplayName("a superseded document cannot be superseded or revoked again")
        void terminal_statuses_reject_further_transitions() {
            ComplianceDocument superseded = register(TODAY.minusMonths(6), TODAY.plusMonths(6))
                    .supersede(metadata());

            assertThatThrownBy(() -> superseded.supersede(metadata()))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> superseded.revoke(metadata()))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("expiry cannot precede issue")
        void expiry_cannot_precede_issue() {
            assertThatThrownBy(() -> register(TODAY, TODAY.minusDays(1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expiresOn cannot precede issuedOn");
        }

        @Test
        @DisplayName("a retention class is mandatory")
        void retention_class_is_mandatory() {
            assertThatThrownBy(() -> new ComplianceDocument(UUID.randomUUID(), VEHICLE_ID, ACCRA,
                    ComplianceDocumentType.INSURANCE_CERTIFICATE, "REF", "DVLA", TODAY, TODAY.plusYears(1),
                    ComplianceDocumentStatus.ACTIVE, null, null, metadata()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("retentionClass");
        }

        @Test
        @DisplayName("the mandatory document types are the road-legal ones")
        void mandatory_document_types() {
            assertThat(ComplianceDocumentType.ROADWORTHINESS_CERTIFICATE.isMandatory()).isTrue();
            assertThat(ComplianceDocumentType.INSURANCE_CERTIFICATE.isMandatory()).isTrue();
            assertThat(ComplianceDocumentType.VEHICLE_REGISTRATION.isMandatory()).isTrue();
            assertThat(ComplianceDocumentType.COMMERCIAL_PERMIT.isMandatory()).isFalse();
        }

        private ComplianceDocument register(LocalDate issuedOn, LocalDate expiresOn) {
            return ComplianceDocument.register(UUID.randomUUID(), VEHICLE_ID, ACCRA,
                    ComplianceDocumentType.INSURANCE_CERTIFICATE, "INS-001", "SIC Insurance", issuedOn, expiresOn,
                    UUID.randomUUID(), RetentionClass.COMPLIANCE, NOW, WARNING_WINDOW, metadata());
        }
    }

    @Nested
    @DisplayName("VehicleServiceRecord")
    class VehicleServiceRecordTest {

        private static final Duration DUE_WINDOW = Duration.ofDays(14);

        @Test
        @DisplayName("a recent service with distant next-due leaves the vehicle in service")
        void recent_service_is_in_service() {
            VehicleServiceRecord record = FleetFixtures.completedService(VEHICLE_ID, TODAY.minusMonths(1),
                    40_000L, TODAY.plusMonths(5), 50_000L);

            assertThat(record.deriveStatus(NOW, 42_000L, DUE_WINDOW)).isEqualTo(VehicleServiceStatus.IN_SERVICE);
        }

        @Test
        @DisplayName("service becomes due inside the date warning window")
        void service_is_due_by_date() {
            VehicleServiceRecord record = FleetFixtures.completedService(VEHICLE_ID, TODAY.minusMonths(6),
                    40_000L, TODAY.plusDays(7), 90_000L);

            assertThat(record.deriveStatus(NOW, 42_000L, DUE_WINDOW)).isEqualTo(VehicleServiceStatus.DUE);
        }

        @Test
        @DisplayName("service becomes due as the odometer approaches the target")
        void service_is_due_by_odometer() {
            VehicleServiceRecord record = FleetFixtures.completedService(VEHICLE_ID, TODAY.minusMonths(2),
                    40_000L, TODAY.plusYears(1), 50_000L);

            // 49,600 of a 40,000 -> 50,000 interval is inside the final 5% margin.
            assertThat(record.deriveStatus(NOW, 49_600L, DUE_WINDOW)).isEqualTo(VehicleServiceStatus.DUE);
        }

        @Test
        @DisplayName("a past due date makes the service overdue")
        void service_is_overdue_by_date() {
            VehicleServiceRecord record = FleetFixtures.completedService(VEHICLE_ID, TODAY.minusYears(1),
                    40_000L, TODAY.minusDays(1), 90_000L);

            assertThat(record.deriveStatus(NOW, 42_000L, DUE_WINDOW)).isEqualTo(VehicleServiceStatus.OVERDUE);
        }

        @Test
        @DisplayName("passing the odometer target makes the service overdue whatever the date says")
        void service_is_overdue_by_odometer() {
            VehicleServiceRecord record = FleetFixtures.completedService(VEHICLE_ID, TODAY.minusMonths(2),
                    40_000L, TODAY.plusYears(1), 50_000L);

            assertThat(record.deriveStatus(NOW, 50_001L, DUE_WINDOW)).isEqualTo(VehicleServiceStatus.OVERDUE);
        }

        @Test
        @DisplayName("an incomplete or failed service leaves the vehicle out of service")
        void unfinished_service_leaves_the_vehicle_out_of_service() {
            VehicleServiceRecord incomplete = VehicleServiceRecord.record(UUID.randomUUID(), VEHICLE_ID, ACCRA,
                    ServiceType.REPAIR, TODAY.minusDays(1), 42_000L, TODAY.plusMonths(6), 52_000L, "VENDOR-1",
                    "Awaiting parts.", ServiceOutcome.INCOMPLETE, null, metadata());

            assertThat(incomplete.deriveStatus(NOW, 42_000L, DUE_WINDOW))
                    .isEqualTo(VehicleServiceStatus.OUT_OF_SERVICE);
            assertThat(ServiceOutcome.FAILED.returnsVehicleToService()).isFalse();
            assertThat(ServiceOutcome.COMPLETED_WITH_ADVISORIES.returnsVehicleToService()).isTrue();
        }

        @Test
        @DisplayName("a service with no next-due schedule never becomes due on its own")
        void service_without_a_schedule_stays_in_service() {
            VehicleServiceRecord record = VehicleServiceRecord.record(UUID.randomUUID(), VEHICLE_ID, ACCRA,
                    ServiceType.BODYWORK, TODAY.minusDays(3), 42_000L, null, null, null, "Panel repair.",
                    ServiceOutcome.COMPLETED, null, metadata());

            assertThat(record.deriveStatus(NOW, 999_999L, DUE_WINDOW)).isEqualTo(VehicleServiceStatus.IN_SERVICE);
        }

        @Test
        @DisplayName("the next-due schedule cannot precede the work it follows")
        void next_due_cannot_precede_the_service() {
            assertThatThrownBy(() -> FleetFixtures.completedService(VEHICLE_ID, TODAY, 40_000L,
                    TODAY.minusDays(1), 50_000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nextDueOn cannot precede performedOn");

            assertThatThrownBy(() -> FleetFixtures.completedService(VEHICLE_ID, TODAY, 40_000L,
                    TODAY.plusMonths(6), 39_000L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nextDueOdometer cannot be lower");
        }
    }
}
