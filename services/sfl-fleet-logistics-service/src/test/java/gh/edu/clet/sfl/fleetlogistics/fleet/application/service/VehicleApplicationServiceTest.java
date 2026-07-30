package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.ChangeVehicleLifecycleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CorrectOdometerCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RecordVehicleServiceCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterComplianceDocumentCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterVehicleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateActiveIdentifierException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OdometerRegressionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OptimisticLockConflictException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocument;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceOutcome;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Traces: SRS-SFL-S166-01 acceptance criteria 1-3, plus the audit and change-event obligations of the
 * S166-01 workflow ("System saves the record, writes audit evidence and publishes any required change
 * event").
 */
class VehicleApplicationServiceTest {

    private FleetTestDoubles.InMemoryVehicleRepository vehicles;
    private FleetTestDoubles.InMemoryComplianceDocumentRepository complianceDocuments;
    private FleetTestDoubles.InMemoryServiceRecordRepository serviceRecords;
    private FleetTestDoubles.RecordingAuditPort audit;
    private FleetTestDoubles.RecordingEventPublisher events;
    private FleetTestDoubles.InMemoryIdempotencyPort idempotency;
    private VehicleApplicationService service;

    @BeforeEach
    void setUp() {
        vehicles = new FleetTestDoubles.InMemoryVehicleRepository();
        complianceDocuments = new FleetTestDoubles.InMemoryComplianceDocumentRepository();
        serviceRecords = new FleetTestDoubles.InMemoryServiceRecordRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        audit = new FleetTestDoubles.RecordingAuditPort(clock);
        events = new FleetTestDoubles.RecordingEventPublisher();
        idempotency = new FleetTestDoubles.InMemoryIdempotencyPort();
        service = new VehicleApplicationService(vehicles, complianceDocuments, serviceRecords,
                new FleetTestDoubles.InMemoryLocationRepository(),
                FleetTestDoubles.readinessService(complianceDocuments, clock),
                new FleetAccessPolicy(), audit, events, idempotency,
                new FleetTestDoubles.FixedRuntimeConfiguration(), clock);
    }

    // --- SRS-SFL-S166-01 AC1: create a valid record ------------------------------------

    @Test
    @DisplayName("an authorised officer registers a vehicle with the system-managed fields populated")
    void registers_vehicle_with_metadata() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        assertThat(vehicle.registrationNumber().value()).isEqualTo("GT-1234-26");
        assertThat(vehicle.siteCode().value()).isEqualTo("ACCRA");
        assertThat(vehicle.lifecycleStatus()).isEqualTo(VehicleLifecycleStatus.ACTIVE);
        assertThat(vehicle.metadata().createdBy()).isEqualTo("officer@clet.edu.gh");
        assertThat(vehicle.metadata().createdAt()).isEqualTo(NOW);
        assertThat(vehicle.metadata().sourceChannel()).isEqualTo(SourceChannel.WEB);
        assertThat(vehicle.metadata().auditCorrelationId()).isEqualTo("corr-test");
    }

    @Test
    @DisplayName("registration writes an audit record carrying the after image")
    void registration_writes_audit_with_before_after() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        assertThat(audit.records()).hasSize(1);
        var record = audit.records().get(0);
        assertThat(record.action()).isEqualTo(AuditAction.CREATE);
        assertThat(record.resourceType()).isEqualTo("Vehicle");
        assertThat(record.resourceId()).isEqualTo(vehicle.id().toString());
        assertThat(record.beforeValue()).isNull();
        assertThat(record.afterValue()).contains("GT-1234-26");
        assertThat(record.actorId()).isEqualTo("officer@clet.edu.gh");
        assertThat(record.isSealed()).isTrue();
    }

    @Test
    @DisplayName("registration writes the vehicle-created event to the outbox")
    void registration_writes_outbox_event() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        assertThat(events.types()).containsExactly(FleetEventType.VEHICLE_CREATED);
        var published = events.firstOf(FleetEventType.VEHICLE_CREATED).orElseThrow();
        assertThat(published.aggregateId()).isEqualTo(vehicle.id().toString());
        assertThat(published.aggregateType()).isEqualTo("Vehicle");
        assertThat(published.siteScope()).isEqualTo("ACCRA");
    }

    // --- SRS-SFL-S166-01 AC2: duplicate identifier -------------------------------------

    @Test
    @DisplayName("a duplicate active registration in the same site is blocked with the SRS wording")
    void duplicate_active_registration_is_blocked() {
        service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        // Different case and padding, same registration: normalisation is what makes the rule hold.
        assertThatThrownBy(() -> service.register(registerCommand("  gt-1234-26 ", "ACCRA", "idem-2")))
                .isInstanceOf(DuplicateActiveIdentifierException.class)
                .hasMessage(FleetErrorCode.FLEET_DUPLICATE_IDENTIFIER.message());

        assertThat(vehicles.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("the same registration in a different site is allowed")
    void same_registration_other_site_allowed() {
        service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));
        Vehicle other = service.register(new RegisterVehicleCommand("GT-1234-26", null, "Toyota", "Hilux", 2022,
                VehicleCategory.PICKUP, 5, "KUMASI", "Transport", "owner@clet.edu.gh", null, 0, false, Set.of(),
                FleetTestDoubles.fleetOfficer("ACCRA", "KUMASI"), SourceChannel.WEB, "idem-2"));

        assertThat(other.siteCode().value()).isEqualTo("KUMASI");
        assertThat(vehicles.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("an archived registration can be reissued to a replacement vehicle")
    void archived_registration_can_be_reused() {
        Vehicle original = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));
        service.changeLifecycle(new ChangeVehicleLifecycleCommand(original.id(), VehicleLifecycleStatus.ARCHIVED,
                "Disposed at auction", null, FleetTestDoubles.fleetManager("ACCRA"), SourceChannel.WEB));

        Vehicle replacement = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-3"));

        assertThat(replacement.id()).isNotEqualTo(original.id());
        assertThat(replacement.lifecycleStatus()).isEqualTo(VehicleLifecycleStatus.ACTIVE);
    }

    @Test
    @DisplayName("a duplicate VIN in the same site is blocked and the error masks the VIN")
    void duplicate_vin_is_blocked_and_masked() {
        service.register(new RegisterVehicleCommand("GT-1111-26", "WVWZZZ1JZXW000001", "Toyota", "Hilux", 2022,
                VehicleCategory.PICKUP, 5, "ACCRA", "Transport", "owner@clet.edu.gh", null, 0, false, Set.of(),
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB, "idem-1"));

        assertThatThrownBy(() -> service.register(new RegisterVehicleCommand("GT-2222-26", "WVWZZZ1JZXW000001",
                "Toyota", "Hilux", 2022, VehicleCategory.PICKUP, 5, "ACCRA", "Transport", "owner@clet.edu.gh",
                null, 0, false, Set.of(), FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB, "idem-2")))
                .isInstanceOf(DuplicateActiveIdentifierException.class)
                .extracting(exception -> ((DuplicateActiveIdentifierException) exception).details())
                .satisfies(details -> assertThat(String.valueOf(details.get("identifier")))
                        .doesNotContain("WVWZZZ")
                        .endsWith("0001"));
    }

    // --- SRS-SFL-S166-01 AC3: authorisation --------------------------------------------

    @Test
    @DisplayName("a reporting viewer cannot register a vehicle")
    void reporting_viewer_cannot_register() {
        assertThatThrownBy(() -> service.register(new RegisterVehicleCommand("GT-1234-26", null, "Toyota",
                "Hilux", 2022, VehicleCategory.PICKUP, 5, "ACCRA", "Transport", "owner@clet.edu.gh", null, 0,
                false, Set.of(), FleetTestDoubles.reportingViewer("ACCRA"), SourceChannel.WEB, "idem-1")))
                .isInstanceOf(FleetAuthorizationException.class)
                .hasMessage(FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE.message());

        assertThat(vehicles.size()).isZero();
        assertThat(events.published()).isEmpty();
    }

    @Test
    @DisplayName("an officer scoped to another site cannot register there")
    void cross_site_registration_denied() {
        assertThatThrownBy(() -> service.register(new RegisterVehicleCommand("GT-1234-26", null, "Toyota",
                "Hilux", 2022, VehicleCategory.PICKUP, 5, "ACCRA", "Transport", "owner@clet.edu.gh", null, 0,
                false, Set.of(), FleetTestDoubles.fleetOfficer("KUMASI"), SourceChannel.WEB, "idem-1")))
                .isInstanceOf(FleetAuthorizationException.class)
                .hasMessage(FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE.message());
    }

    @Test
    @DisplayName("only a manager may archive; an officer is refused")
    void archiving_needs_a_privileged_permission() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        assertThatThrownBy(() -> service.changeLifecycle(new ChangeVehicleLifecycleCommand(vehicle.id(),
                VehicleLifecycleStatus.ARCHIVED, "Disposed", null, FleetTestDoubles.fleetOfficer("ACCRA"),
                SourceChannel.WEB)))
                .isInstanceOf(FleetAuthorizationException.class);
    }

    // --- Idempotency and concurrency ---------------------------------------------------

    @Test
    @DisplayName("a replayed registration returns the original vehicle rather than creating a second one")
    void replayed_registration_returns_the_original() {
        Vehicle first = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));
        Vehicle replay = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(vehicles.size()).isEqualTo(1);
        assertThat(events.types()).containsExactly(FleetEventType.VEHICLE_CREATED);
    }

    @Test
    @DisplayName("a stale expected version is rejected instead of overwriting a concurrent edit")
    void stale_expected_version_is_rejected() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        assertThatThrownBy(() -> service.changeLifecycle(new ChangeVehicleLifecycleCommand(vehicle.id(),
                VehicleLifecycleStatus.INACTIVE, "Withdrawn for review", 7L,
                FleetTestDoubles.fleetManager("ACCRA"), SourceChannel.WEB)))
                .isInstanceOf(OptimisticLockConflictException.class)
                .hasMessage(FleetErrorCode.FLEET_RECORD_VERSION_CONFLICT.message());
    }

    // --- Lifecycle, compliance, service ------------------------------------------------

    @Test
    @DisplayName("a lifecycle change records the reason in the audit trail and publishes the event")
    void lifecycle_change_is_audited_and_published() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        Vehicle inactive = service.changeLifecycle(new ChangeVehicleLifecycleCommand(vehicle.id(),
                VehicleLifecycleStatus.INACTIVE, "Awaiting reassignment", null,
                FleetTestDoubles.fleetManager("ACCRA"), SourceChannel.WEB));

        assertThat(inactive.lifecycleStatus()).isEqualTo(VehicleLifecycleStatus.INACTIVE);
        assertThat(audit.hasRecord(AuditAction.STATE_TRANSITION, "Vehicle")).isTrue();
        assertThat(audit.records().get(1).afterValue()).contains("Awaiting reassignment");
        assertThat(events.types()).contains(FleetEventType.VEHICLE_LIFECYCLE_CHANGED,
                FleetEventType.VEHICLE_AVAILABILITY_CHANGED);
    }

    @Test
    @DisplayName("registering a replacement document supersedes the current one of the same type")
    void new_document_supersedes_the_current_one() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));
        ComplianceDocument first = service.registerComplianceDocument(complianceCommand(vehicle, "idem-doc-1",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31")));

        ComplianceDocument second = service.registerComplianceDocument(complianceCommand(vehicle, "idem-doc-2",
                LocalDate.parse("2026-07-01"), LocalDate.parse("2027-06-30")));

        assertThat(complianceDocuments.findById(first.id()).orElseThrow().status())
                .isEqualTo(ComplianceDocumentStatus.SUPERSEDED);
        assertThat(second.status()).isEqualTo(ComplianceDocumentStatus.ACTIVE);
        assertThat(complianceDocuments.findCurrentByVehicle(vehicle.id())).hasSize(1);
    }

    @Test
    @DisplayName("recording a service advances the odometer and recomputes the service status")
    void service_record_updates_odometer_and_status() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        service.recordService(new RecordVehicleServiceCommand(vehicle.id(), ServiceType.ROUTINE_SERVICE,
                LocalDate.parse("2026-07-20"), 60_000L, LocalDate.parse("2026-07-25"), 70_000L, "CMMS-1",
                "Routine service", ServiceOutcome.COMPLETED, null, FleetTestDoubles.fleetOfficer("ACCRA"),
                SourceChannel.WEB, "idem-svc-1"));

        Vehicle updated = vehicles.findById(vehicle.id()).orElseThrow();
        assertThat(updated.odometer().value()).isEqualTo(60_000L);
        // The next service falls due in five days, inside the fourteen-day warning window.
        assertThat(updated.serviceStatus()).isEqualTo(VehicleServiceStatus.DUE);
        assertThat(events.types()).contains(FleetEventType.VEHICLE_SERVICE_DUE);
    }

    @Test
    @DisplayName("a service reading below the current odometer is rejected")
    void service_reading_cannot_regress_the_odometer() {
        Vehicle vehicle = service.register(new RegisterVehicleCommand("GT-1234-26", null, "Toyota", "Hilux",
                2022, VehicleCategory.PICKUP, 5, "ACCRA", "Transport", "owner@clet.edu.gh", null, 80_000L, false,
                Set.of(), FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB, "idem-1"));

        assertThatThrownBy(() -> service.recordService(new RecordVehicleServiceCommand(vehicle.id(),
                ServiceType.ROUTINE_SERVICE, LocalDate.parse("2026-07-20"), 70_000L, null, null, null,
                "Routine service", ServiceOutcome.COMPLETED, null, FleetTestDoubles.fleetOfficer("ACCRA"),
                SourceChannel.WEB, "idem-svc-1")))
                .isInstanceOf(OdometerRegressionException.class);
    }

    @Test
    @DisplayName("an odometer correction needs a reason and evidence and is audited as a correction")
    void odometer_correction_requires_reason_and_evidence() {
        Vehicle vehicle = service.register(new RegisterVehicleCommand("GT-1234-26", null, "Toyota", "Hilux",
                2022, VehicleCategory.PICKUP, 5, "ACCRA", "Transport", "owner@clet.edu.gh", null, 80_000L, false,
                Set.of(), FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB, "idem-1"));
        ActorContext manager = FleetTestDoubles.fleetManager("ACCRA");

        assertThatThrownBy(() -> service.correctOdometer(new CorrectOdometerCommand(vehicle.id(), 8_000L,
                "Transposed digits", null, null, manager, SourceChannel.WEB)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceId");

        Vehicle corrected = service.correctOdometer(new CorrectOdometerCommand(vehicle.id(), 8_000L,
                "Transposed digits at registration", java.util.UUID.randomUUID(), null, manager,
                SourceChannel.WEB));

        assertThat(corrected.odometer().value()).isEqualTo(8_000L);
        assertThat(audit.hasRecord(AuditAction.ODOMETER_CORRECTION, "Vehicle")).isTrue();
    }

    @Test
    @DisplayName("an officer may not correct an odometer")
    void officer_cannot_correct_odometer() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));

        assertThatThrownBy(() -> service.correctOdometer(new CorrectOdometerCommand(vehicle.id(), 100L,
                "Typo", java.util.UUID.randomUUID(), null, FleetTestDoubles.fleetOfficer("ACCRA"),
                SourceChannel.WEB)))
                .isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("the audit chain stays intact across a sequence of vehicle operations")
    void audit_chain_stays_intact() {
        Vehicle vehicle = service.register(registerCommand("GT-1234-26", "ACCRA", "idem-1"));
        service.changeLifecycle(new ChangeVehicleLifecycleCommand(vehicle.id(), VehicleLifecycleStatus.INACTIVE,
                "Seasonal stand-down", null, FleetTestDoubles.fleetManager("ACCRA"), SourceChannel.WEB));

        assertThat(audit.verifyChain().intact()).isTrue();
        assertThat(audit.verifyChain().recordsChecked()).isEqualTo(2);
    }

    private RegisterVehicleCommand registerCommand(String registration, String site, String idempotencyKey) {
        return new RegisterVehicleCommand(registration, null, "Toyota", "Hilux", 2022, VehicleCategory.PICKUP, 5,
                site, "Transportation & Logistics Unit", "logistics.officer@clet.edu.gh", "PO-2026-0012", 0,
                false, Set.of(), FleetTestDoubles.fleetOfficer(site), SourceChannel.WEB, idempotencyKey);
    }

    private RegisterComplianceDocumentCommand complianceCommand(Vehicle vehicle, String idempotencyKey,
            LocalDate issuedOn, LocalDate expiresOn) {
        return new RegisterComplianceDocumentCommand(vehicle.id(), ComplianceDocumentType.INSURANCE_CERTIFICATE,
                "INS-" + idempotencyKey, "SIC Insurance", issuedOn, expiresOn, java.util.UUID.randomUUID(),
                RetentionClass.COMPLIANCE, FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB,
                idempotencyKey);
    }
}
