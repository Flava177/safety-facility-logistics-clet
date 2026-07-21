package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.NOW;
import static gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetFixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.UpdateDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateActiveIdentifierException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverEligibilityStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverLifecycleStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.support.FleetTestDoubles;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Traces: SRS-SFL-S166-01 for driver profile references; eligibility feeds SRS-SFL-S166-05. */
class DriverApplicationServiceTest {

    private FleetTestDoubles.InMemoryDriverProfileRepository drivers;
    private FleetTestDoubles.RecordingAuditPort audit;
    private FleetTestDoubles.RecordingEventPublisher events;
    private FleetTestDoubles.StubHrmsDirectory hrms;
    private DriverApplicationService service;

    @BeforeEach
    void setUp() {
        drivers = new FleetTestDoubles.InMemoryDriverProfileRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        audit = new FleetTestDoubles.RecordingAuditPort(clock);
        events = new FleetTestDoubles.RecordingEventPublisher();
        hrms = new FleetTestDoubles.StubHrmsDirectory();
        service = new DriverApplicationService(drivers, hrms, new FleetAccessPolicy(), audit, events,
                new FleetTestDoubles.InMemoryIdempotencyPort(),
                new FleetTestDoubles.FixedRuntimeConfiguration(), clock);
    }

    @Test
    @DisplayName("an authorised officer registers a driver reference with eligibility computed")
    void registers_driver_reference() {
        DriverProfileReference driver = service.register(registerCommand("CLET/HR/00123", "GHA-DL-4477201",
                TODAY.plusYears(1), "idem-1"));

        assertThat(driver.staffReference()).isEqualTo("CLET/HR/00123");
        assertThat(driver.lifecycleStatus()).isEqualTo(DriverLifecycleStatus.ACTIVE);
        assertThat(driver.eligibilityStatus()).isEqualTo(DriverEligibilityStatus.ELIGIBLE);
        assertThat(driver.metadata().createdBy()).isEqualTo("officer@clet.edu.gh");
        assertThat(audit.hasRecord(AuditAction.CREATE, "DriverProfileReference")).isTrue();
        assertThat(events.types()).containsExactly(FleetEventType.DRIVER_REGISTERED);
    }

    @Test
    @DisplayName("a driver whose licence is already expiring registers as conditional, not eligible")
    void expiring_licence_registers_as_conditional() {
        DriverProfileReference driver = service.register(registerCommand("CLET/HR/00124", "GHA-DL-4477202",
                TODAY.plusDays(10), "idem-1"));

        assertThat(driver.eligibilityStatus()).isEqualTo(DriverEligibilityStatus.CONDITIONAL);
    }

    @Test
    @DisplayName("a duplicate staff reference in the same site is blocked with the SRS wording")
    void duplicate_staff_reference_is_blocked() {
        service.register(registerCommand("CLET/HR/00123", "GHA-DL-4477201", TODAY.plusYears(1), "idem-1"));

        assertThatThrownBy(() -> service.register(registerCommand("clet/hr/00123", "GHA-DL-9999999",
                TODAY.plusYears(1), "idem-2")))
                .isInstanceOf(DuplicateActiveIdentifierException.class)
                .hasMessage(FleetErrorCode.FLEET_DUPLICATE_IDENTIFIER.message());

        assertThat(drivers.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("a duplicate licence number is blocked and the error masks the licence number")
    void duplicate_licence_number_is_blocked_and_masked() {
        service.register(registerCommand("CLET/HR/00123", "GHA-DL-4477201", TODAY.plusYears(1), "idem-1"));

        assertThatThrownBy(() -> service.register(registerCommand("CLET/HR/00999", "GHA-DL-4477201",
                TODAY.plusYears(1), "idem-2")))
                .isInstanceOf(DuplicateActiveIdentifierException.class)
                .extracting(exception -> ((DuplicateActiveIdentifierException) exception).details())
                .satisfies(details -> assertThat(String.valueOf(details.get("identifier")))
                        .doesNotContain("4477")
                        .endsWith("201"));
    }

    @Test
    @DisplayName("an unknown HRMS staff reference is rejected rather than silently accepted")
    void unknown_hrms_reference_is_rejected() {
        hrms.rejecting("CLET/HR/00999");

        assertThatThrownBy(() -> service.register(registerCommand("CLET/HR/00999", "GHA-DL-4477201",
                TODAY.plusYears(1), "idem-1")))
                .isInstanceOf(RecordNotFoundException.class);

        assertThat(drivers.size()).isZero();
        assertThat(events.published()).isEmpty();
    }

    @Test
    @DisplayName("a reporting viewer cannot register a driver")
    void reporting_viewer_cannot_register() {
        assertThatThrownBy(() -> service.register(new RegisterDriverCommand("CLET/HR/00123", "Kwame Mensah",
                "GHA-DL-4477201", LicenceClass.C, TODAY.plusYears(1), TODAY.plusYears(1), "ACCRA",
                "Transportation & Logistics Unit", FleetTestDoubles.reportingViewer("ACCRA"), SourceChannel.WEB,
                "idem-1")))
                .isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("an officer scoped to another site cannot register there")
    void cross_site_registration_denied() {
        assertThatThrownBy(() -> service.register(new RegisterDriverCommand("CLET/HR/00123", "Kwame Mensah",
                "GHA-DL-4477201", LicenceClass.C, TODAY.plusYears(1), TODAY.plusYears(1), "ACCRA",
                "Transportation & Logistics Unit", FleetTestDoubles.fleetOfficer("KUMASI"), SourceChannel.WEB,
                "idem-1")))
                .isInstanceOf(FleetAuthorizationException.class)
                .hasMessage(FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE.message());
    }

    @Test
    @DisplayName("a replayed registration returns the original driver")
    void replayed_registration_returns_the_original() {
        DriverProfileReference first = service.register(registerCommand("CLET/HR/00123", "GHA-DL-4477201",
                TODAY.plusYears(1), "idem-1"));
        DriverProfileReference replay = service.register(registerCommand("CLET/HR/00123", "GHA-DL-4477201",
                TODAY.plusYears(1), "idem-1"));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(drivers.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("an expired licence recorded on update makes the driver ineligible and publishes the change")
    void expired_licence_on_update_changes_eligibility() {
        DriverProfileReference driver = service.register(registerCommand("CLET/HR/00123", "GHA-DL-4477201",
                TODAY.plusYears(1), "idem-1"));

        DriverProfileReference updated = service.update(new UpdateDriverCommand(driver.id(), "Kwame Mensah",
                "GHA-DL-4477201", LicenceClass.C, TODAY.minusDays(1), TODAY.plusYears(1),
                "Transportation & Logistics Unit", null, null, null, FleetTestDoubles.fleetOfficer("ACCRA"),
                SourceChannel.WEB));

        assertThat(updated.eligibilityStatus()).isEqualTo(DriverEligibilityStatus.INELIGIBLE);
        assertThat(events.types()).contains(FleetEventType.DRIVER_ELIGIBILITY_CHANGED);
        assertThat(events.firstOf(FleetEventType.DRIVER_ELIGIBILITY_CHANGED).orElseThrow().payload().toString())
                .contains("DRIVER_LICENCE_EXPIRED");
    }

    @Test
    @DisplayName("suspending a driver needs a privileged permission and a reason")
    void suspension_needs_privilege_and_reason() {
        DriverProfileReference driver = service.register(registerCommand("CLET/HR/00123", "GHA-DL-4477201",
                TODAY.plusYears(1), "idem-1"));

        assertThatThrownBy(() -> service.update(suspendCommand(driver, "Under investigation",
                FleetTestDoubles.fleetOfficer("ACCRA"))))
                .isInstanceOf(gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.UnauthorizedApprovalException
                        .class);

        DriverProfileReference suspended = service.update(suspendCommand(driver, "Under investigation",
                FleetTestDoubles.fleetManager("ACCRA")));

        assertThat(suspended.lifecycleStatus()).isEqualTo(DriverLifecycleStatus.SUSPENDED);
        assertThat(suspended.eligibilityStatus()).isEqualTo(DriverEligibilityStatus.SUSPENDED);
        assertThat(suspended.suspensionReason()).isEqualTo("Under investigation");
    }

    @Test
    @DisplayName("reassessment publishes an eligibility change when the licence lapses over time")
    void reassessment_detects_a_lapsed_licence() {
        DriverProfileReference driver = service.register(registerCommand("CLET/HR/00123", "GHA-DL-4477201",
                TODAY.plusDays(1), "idem-1"));
        assertThat(driver.eligibilityStatus()).isEqualTo(DriverEligibilityStatus.CONDITIONAL);

        // A later clock is what the scheduled sweep would use the next morning.
        DriverApplicationService laterService = new DriverApplicationService(drivers, hrms, new FleetAccessPolicy(),
                audit, events, new FleetTestDoubles.InMemoryIdempotencyPort(),
                new FleetTestDoubles.FixedRuntimeConfiguration(),
                Clock.fixed(NOW.plus(java.time.Duration.ofDays(3)), ZoneOffset.UTC));

        DriverProfileReference reassessed = laterService.reassessEligibility(driver.id(),
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.SCHEDULER);

        assertThat(reassessed.eligibilityStatus()).isEqualTo(DriverEligibilityStatus.INELIGIBLE);
        assertThat(events.types()).contains(FleetEventType.DRIVER_ELIGIBILITY_CHANGED);
    }

    @Test
    @DisplayName("reassessment publishes nothing when the status has not moved")
    void reassessment_is_quiet_when_nothing_changed() {
        DriverProfileReference driver = service.register(registerCommand("CLET/HR/00123", "GHA-DL-4477201",
                TODAY.plusYears(1), "idem-1"));
        int publishedAfterRegistration = events.published().size();

        service.reassessEligibility(driver.id(), FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.SCHEDULER);

        assertThat(events.published()).hasSize(publishedAfterRegistration);
    }

    private RegisterDriverCommand registerCommand(String staffReference, String licenceNumber,
            LocalDate licenceExpiry, String idempotencyKey) {
        return new RegisterDriverCommand(staffReference, "Kwame Mensah", licenceNumber, LicenceClass.C,
                licenceExpiry, TODAY.plusYears(1), "ACCRA", "Transportation & Logistics Unit",
                FleetTestDoubles.fleetOfficer("ACCRA"), SourceChannel.WEB, idempotencyKey);
    }

    private UpdateDriverCommand suspendCommand(DriverProfileReference driver, String reason,
            gh.edu.clet.sfl.common.security.ActorContext actor) {
        return new UpdateDriverCommand(driver.id(), driver.displayName(), driver.licence().number(),
                driver.licence().licenceClass(), driver.licence().expiresOn(), driver.medicalClearanceExpiresOn(),
                driver.responsibleUnit(), DriverLifecycleStatus.SUSPENDED, reason, null, actor, SourceChannel.WEB);
    }
}
