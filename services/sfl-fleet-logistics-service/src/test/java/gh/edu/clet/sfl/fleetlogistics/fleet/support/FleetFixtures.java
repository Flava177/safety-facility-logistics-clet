package gh.edu.clet.sfl.fleetlogistics.fleet.support;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OdometerReading;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OdometerSource;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RegistrationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RestrictedUse;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceOutcome;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleIdentificationNumber;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceRecord;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleSpecification;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Shared fixtures so tests read as scenarios rather than as constructor calls. */
public final class FleetFixtures {

    /** A fixed "now" used across the suite so date-driven assertions are deterministic. */
    public static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");
    public static final LocalDate TODAY = LocalDate.parse("2026-07-21");
    public static final SiteCode ACCRA = SiteCode.of("ACCRA");
    public static final SiteCode KUMASI = SiteCode.of("KUMASI");

    private FleetFixtures() {
    }

    public static RecordMetadata metadata() {
        return RecordMetadata.createdBy("officer@clet.edu.gh", NOW, SourceChannel.WEB, "corr-test");
    }

    public static Vehicle vehicle() {
        return vehicle(UUID.fromString("11111111-1111-1111-1111-111111111111"), "GT-1234-26", ACCRA);
    }

    public static Vehicle vehicle(UUID id, String registration, SiteCode site) {
        return Vehicle.register(
                id,
                RegistrationNumber.of(registration),
                VehicleIdentificationNumber.ofNullable("WVWZZZ1JZXW000001"),
                new VehicleSpecification("Toyota", "Hilux", 2022, VehicleCategory.PICKUP, 5),
                site,
                "Transportation & Logistics Unit",
                "logistics.officer@clet.edu.gh",
                "PO-2026-0012",
                OdometerReading.of(42_000, OdometerSource.MANUAL_ENTRY, NOW),
                RestrictedUse.unrestricted(),
                metadata());
    }

    public static Vehicle emergencyOnlyVehicle() {
        return Vehicle.register(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                RegistrationNumber.of("GA-9999-26"),
                null,
                new VehicleSpecification("Toyota", "Hiace", 2024, VehicleCategory.AMBULANCE, 3),
                ACCRA,
                "Health & Safety Unit",
                "hse.officer@clet.edu.gh",
                null,
                OdometerReading.of(8_000, OdometerSource.MANUAL_ENTRY, NOW),
                RestrictedUse.forEmergencyUseOnly(),
                metadata());
    }

    public static ComplianceDocument validRoadworthiness(UUID vehicleId) {
        return complianceDocument(vehicleId, ComplianceDocumentType.ROADWORTHINESS_CERTIFICATE,
                TODAY.minusMonths(6), TODAY.plusMonths(6));
    }

    public static ComplianceDocument expiredInsurance(UUID vehicleId) {
        return complianceDocument(vehicleId, ComplianceDocumentType.INSURANCE_CERTIFICATE,
                TODAY.minusYears(1), TODAY.minusDays(1));
    }

    public static ComplianceDocument complianceDocument(UUID vehicleId, ComplianceDocumentType type,
            LocalDate issuedOn, LocalDate expiresOn) {
        return ComplianceDocument.register(UUID.randomUUID(), vehicleId, ACCRA, type,
                type.name() + "-REF-001", "DVLA Ghana", issuedOn, expiresOn, UUID.randomUUID(),
                EvidenceRetentionClass.COMPLIANCE_7_YEARS, NOW, Duration.ofDays(30), metadata());
    }

    public static VehicleServiceRecord completedService(UUID vehicleId, LocalDate performedOn, long odometer,
            LocalDate nextDueOn, Long nextDueOdometer) {
        return VehicleServiceRecord.record(UUID.randomUUID(), vehicleId, ACCRA, ServiceType.ROUTINE_SERVICE,
                performedOn, odometer, nextDueOn, nextDueOdometer, "CMMS-VENDOR-01",
                "Routine 10,000 km service completed.", ServiceOutcome.COMPLETED, UUID.randomUUID(), metadata());
    }
}
