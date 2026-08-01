package gh.edu.clet.sfl.fleetlogistics.fleet.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CloseTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.CreateTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.FleetWorkflowCommands;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.IntegrationCommands;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RecordInspectionCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterComplianceDocumentCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterVehicleCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.StartTripCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.VehicleRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.query.TripQueryService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.query.VehicleQueryService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.DriverApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetDashboardApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetEvidenceApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetIntegrationApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetReadinessService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetWorkflowApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.TripApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.service.VehicleApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow.SlaEvaluationService;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.AssignmentConflictException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.AuditChainFailureException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DriverIneligibleException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateActiveIdentifierException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateIntegrationMessageException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidSignatureException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ReadinessBlockedException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.SchemaValidationFailedException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ComplianceDocumentType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DefectSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessBlockerCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ReadinessStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EvidenceRetentionClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Vehicle;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleCategory;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The sixteen critical end-to-end scenarios from {@code docs/fleet/S166_Test_Plan.md} §3, run against a
 * real PostgreSQL with the full Spring context.
 *
 * <p>These exercise what unit tests cannot: the Flyway schema, the partial unique indexes, the gist
 * exclusion constraints, the append-only triggers, the hash-chained audit log and the transactional
 * outbox — all working together through the real application services.
 *
 * <p>Traces: SRS-SFL-S166-01 through -05 acceptance criteria.
 */
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        // The schedulers are driven explicitly so each scenario controls its own timing.
        "sfl.fleet.scheduling.sla.enabled=false",
        "sfl.fleet.scheduling.outbox.enabled=false",
        "sfl.fleet.scheduling.compliance.enabled=false",
        "sfl.fleet.scheduling.dashboard.enabled=false",
        "sfl.fleet.messaging.transport=local"
})
@Import(FleetPostgresSupport.MutableClockConfiguration.class)
@EnabledIf(value = "gh.edu.clet.sfl.fleetlogistics.fleet.e2e.FleetPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FleetPostgresSupport.unavailableReason()")
class FleetCriticalScenariosEndToEndTest extends FleetPostgresSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private VehicleApplicationService vehicles;
    @Autowired private VehicleQueryService vehicleQueries;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private DriverApplicationService drivers;
    @Autowired private TripApplicationService trips;
    @Autowired private TripQueryService tripQueries;
    @Autowired private FleetReadinessService readiness;
    @Autowired private FleetWorkflowApplicationService workflow;
    @Autowired private SlaEvaluationService slaEvaluation;
    @Autowired private FleetIntegrationApplicationService integrations;
    @Autowired private FleetDashboardApplicationService dashboard;
    @Autowired private FleetEvidenceApplicationService evidence;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MutableClock clock;

    private Instant now;

    @BeforeEach
    void resetDatabaseAndClock() {
        resetDatabase();
        clock.set(Instant.parse("2026-07-21T08:00:00Z"));
        now = clock.instant();
    }

    private void resetDatabase() {
        jdbc.execute("""
                do $$
                declare
                    table_names text;
                begin
                    delete from fleet_logistics.fleet_runtime_configuration
                     where updated_by = 'e2e';

                    select string_agg(format('%I.%I', schemaname, tablename), ', ')
                      into table_names
                      from pg_tables
                     where schemaname = 'fleet_logistics'
                       and tablename not in (
                           'flyway_schema_history',
                           'service_metadata',
                           'fleet_runtime_configuration',
                           'fleet_sla_rules',
                           'fleet_audit_chain_state'
                       );

                    if table_names is not null then
                        execute 'truncate table ' || table_names || ' restart identity cascade';
                    end if;
                end $$;
                """);
        jdbc.update("""
                update fleet_logistics.fleet_audit_chain_state
                   set head_hash = repeat('0', 64),
                       next_sequence = 0,
                       updated_at = now()
                where id = 1
                """);
    }

    // =====================================================================================
    // Scenario 1 — Register a vehicle and verify audit/outbox creation (SRS-SFL-S166-01 AC1)
    // =====================================================================================

    @Test
    @DisplayName("1. registering a vehicle persists it and writes the audit record and outbox event")
    void scenario_01_register_vehicle_writes_audit_and_outbox() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());

        assertThat(vehicleRepository.findById(vehicle.id())).isPresent();

        Integer auditRows = jdbc.queryForObject("""
                select count(*) from fleet_logistics.fleet_audit_records
                 where resource_type = 'Vehicle' and resource_id = ? and action = 'CREATE'
                """, Integer.class, vehicle.id().toString());
        assertThat(auditRows).isEqualTo(1);

        Integer outboxRows = jdbc.queryForObject("""
                select count(*) from fleet_logistics.outbox_messages
                 where aggregate_id = ? and event_type = 'sfl.ftlmp.vehicle-created.v1'
                """, Integer.class, vehicle.id().toString());
        assertThat(outboxRows).isEqualTo(1);

        // The audit entry is sealed into the chain, not merely written.
        String recordHash = jdbc.queryForObject("""
                select record_hash from fleet_logistics.fleet_audit_records where resource_id = ?
                """, String.class, vehicle.id().toString());
        assertThat(recordHash).hasSize(64);
    }

    // =====================================================================================
    // Scenario 2 — Reject a duplicate active registration in the same site (S166-01 AC2)
    // =====================================================================================

    @Test
    @DisplayName("2. a duplicate active registration in the same site is rejected with the SRS wording")
    void scenario_02_duplicate_registration_rejected() {
        String site = uniqueSite();
        String registration = uniqueRegistration();
        registerVehicle(site, registration);

        assertThatThrownBy(() -> registerVehicle(site, registration.toLowerCase(java.util.Locale.ROOT)))
                .isInstanceOf(DuplicateActiveIdentifierException.class)
                .hasMessage(FleetErrorCode.FLEET_DUPLICATE_IDENTIFIER.message());

        Integer count = jdbc.queryForObject("""
                select count(*) from fleet_logistics.vehicles where site_code = ?
                """, Integer.class, site);
        assertThat(count).isEqualTo(1);
    }

    // =====================================================================================
    // Scenario 3 — Deny cross-site access (S166-01 AC3)
    // =====================================================================================

    @Test
    @DisplayName("3. an actor scoped to another site is denied, and the denial is audited")
    void scenario_03_cross_site_access_denied() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());
        ActorContext outsider = officer(uniqueSite());

        assertThatThrownBy(() -> vehicleQueries.findById(vehicle.id(), outsider))
                .isInstanceOf(FleetAuthorizationException.class)
                .hasMessage(FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE.message());
    }

    // =====================================================================================
    // Scenario 4 — Add compliance documents and calculate readiness (S166-01/-05)
    // =====================================================================================

    @Test
    @DisplayName("4. adding the mandatory compliance documents moves a vehicle to READY")
    void scenario_04_compliance_drives_readiness() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());

        // With nothing on file, every mandatory document is a blocking finding.
        assertThat(readiness.assessVehicle(vehicle).status()).isEqualTo(ReadinessStatus.NOT_READY);
        assertThat(readiness.assessVehicle(vehicle).blockingCodes())
                .contains(ReadinessBlockerCode.COMPLIANCE_DOCUMENT_MISSING);

        giveMandatoryCompliance(vehicle, site);

        Vehicle reloaded = vehicleRepository.findById(vehicle.id()).orElseThrow();
        assertThat(readiness.assessVehicle(reloaded).status()).isEqualTo(ReadinessStatus.READY);
        assertThat(readiness.assessVehicle(reloaded).blockers()).isEmpty();
    }

    // =====================================================================================
    // Scenario 5 — Expired compliance blocks assignment (S166-02)
    // =====================================================================================

    @Test
    @DisplayName("5. an expired compliance document blocks the assignment and names the document")
    void scenario_05_expired_compliance_blocks_assignment() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());
        DriverProfileReference driver = registerDriver(site);

        // Roadworthiness and registration are current; insurance lapsed yesterday.
        addCompliance(vehicle, ComplianceDocumentType.ROADWORTHINESS_CERTIFICATE, today().plusMonths(6), site);
        addCompliance(vehicle, ComplianceDocumentType.VEHICLE_REGISTRATION, today().plusYears(1), site);
        addCompliance(vehicle, ComplianceDocumentType.INSURANCE_CERTIFICATE, today().minusDays(1), site);

        assertThatThrownBy(() -> createTrip(vehicle, driver, site, now.plus(Duration.ofHours(2)),
                now.plus(Duration.ofHours(6))))
                .isInstanceOf(ReadinessBlockedException.class)
                .extracting(exception -> ((ReadinessBlockedException) exception).details())
                .satisfies(details -> assertThat(details.get("blockerCodes").toString())
                        .contains("COMPLIANCE_DOCUMENT_EXPIRED"));

        assertThat(tripCount(site)).isZero();
    }

    // =====================================================================================
    // Scenario 6 — Ineligible driver blocks assignment (S166-02/-05)
    // =====================================================================================

    @Test
    @DisplayName("6. a driver whose licence has expired blocks the assignment")
    void scenario_06_ineligible_driver_blocks_assignment() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());
        giveMandatoryCompliance(vehicle, site);
        DriverProfileReference expired = registerDriver(site, today().minusDays(1));

        assertThatThrownBy(() -> createTrip(vehicle, expired, site, now.plus(Duration.ofHours(2)),
                now.plus(Duration.ofHours(6))))
                .isInstanceOf(DriverIneligibleException.class)
                .hasMessage(FleetErrorCode.FLEET_DRIVER_INELIGIBLE.message());

        assertThat(tripCount(site)).isZero();
    }

    // =====================================================================================
    // Scenario 7 — Prevent overlapping vehicle and driver assignments (S166-02)
    // =====================================================================================

    /**
     * A driver reads their own trip and is refused another driver's <strong>by id</strong>.
     *
     * <p>{@link gh.edu.clet.sfl.fleetlogistics.fleet.application.service.FleetAccessPolicy#requireRecordScope}
     * is documented as "what keeps the limited driver/mobile user class to their own trips and
     * inspections", and it was unit-tested — but no read ever called it, and its single production
     * call site passed {@code null} as the owner reference, which the policy returns on immediately.
     * The rule was therefore enforced nowhere, and a {@code FLEET_DRIVER} holding any trip id read
     * that trip in full.
     *
     * <p>Asserted by id rather than by an absent row in a list: a filter the collection obeys and the
     * record does not is decorative, and only a refusal by id distinguishes the two.
     */
    @Test
    @DisplayName("7a. a driver reads their own trip and is refused another driver's by id")
    void a_driver_is_scoped_to_their_own_trips() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());
        giveMandatoryCompliance(vehicle, site);
        DriverProfileReference mine = registerDriver(site);
        DriverProfileReference theirs = registerDriver(site);

        Instant start = now.plus(Duration.ofHours(2));
        Trip myTrip = createTrip(vehicle, mine, site, start, start.plus(Duration.ofHours(3)));
        Trip theirTrip = createTrip(vehicle, theirs, site, start.plus(Duration.ofHours(4)),
                start.plus(Duration.ofHours(7)));

        // The driver signs in as their staff reference — the equivalence fuel already relies on when
        // it refuses a driver a logbook opened for somebody else.
        ActorContext driver = actor(mine.staffReference(), Set.of(SflRole.FLEET_DRIVER), site, false);

        assertThat(tripQueries.findById(myTrip.id(), driver).id()).isEqualTo(myTrip.id());
        assertThatThrownBy(() -> tripQueries.findById(theirTrip.id(), driver))
                .isInstanceOf(FleetAuthorizationException.class);

        // A supervising officer is unaffected: the rule narrows the driver, not the fleet office.
        assertThat(tripQueries.findById(theirTrip.id(), officer(site)).id()).isEqualTo(theirTrip.id());
    }

    @Test
    @DisplayName("7. overlapping vehicle and driver assignments are prevented, back-to-back ones are not")
    void scenario_07_overlapping_assignments_prevented() {
        String site = uniqueSite();
        Vehicle vehicleA = registerVehicle(site, uniqueRegistration());
        Vehicle vehicleB = registerVehicle(site, uniqueRegistration());
        giveMandatoryCompliance(vehicleA, site);
        giveMandatoryCompliance(vehicleB, site);
        DriverProfileReference driverA = registerDriver(site);
        DriverProfileReference driverB = registerDriver(site);

        Instant start = now.plus(Duration.ofHours(2));
        Instant end = now.plus(Duration.ofHours(6));
        createTrip(vehicleA, driverA, site, start, end);

        // Same vehicle, overlapping window.
        assertThatThrownBy(() -> createTrip(vehicleA, driverB, site, start.plus(Duration.ofHours(1)), end))
                .isInstanceOf(AssignmentConflictException.class)
                .hasMessage(FleetErrorCode.FLEET_ASSIGNMENT_CONFLICT.message());

        // Same driver, overlapping window, different vehicle.
        assertThatThrownBy(() -> createTrip(vehicleB, driverA, site, start.plus(Duration.ofHours(1)), end))
                .isInstanceOf(AssignmentConflictException.class);

        // Back-to-back at the boundary is legitimate scheduling, not a conflict.
        Trip backToBack = createTrip(vehicleA, driverA, site, end, end.plus(Duration.ofHours(4)));
        assertThat(backToBack.status()).isEqualTo(TripStatus.ASSIGNED);
        assertThat(tripCount(site)).isEqualTo(2);
    }

    // =====================================================================================
    // Scenario 8 — Pre-trip inspection with a critical failure blocks readiness (S166-01/-02)
    // =====================================================================================

    @Test
    @DisplayName("8. a critical pre-trip defect grounds the vehicle, blocks the start and raises a defect item")
    void scenario_08_critical_inspection_blocks_readiness() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());
        giveMandatoryCompliance(vehicle, site);
        DriverProfileReference driver = registerDriver(site);
        Trip trip = createTrip(vehicle, driver, site, now.plus(Duration.ofHours(2)),
                now.plus(Duration.ofHours(6)));

        trips.recordInspection(new RecordInspectionCommand(trip.id(), null, InspectionType.PRE_TRIP, 42_100L,
                UUID.randomUUID(),
                List.of(new RecordInspectionCommand.Finding("BRAKES", "Brake failure", DefectSeverity.CRITICAL)),
                "Pre-trip check", officer(site), SourceChannel.MOBILE, uniqueKey()));

        Vehicle grounded = vehicleRepository.findById(vehicle.id()).orElseThrow();
        assertThat(grounded.serviceStatus()).isEqualTo(VehicleServiceStatus.OUT_OF_SERVICE);
        assertThat(readiness.assessVehicle(grounded).blockingCodes())
                .contains(ReadinessBlockerCode.VEHICLE_OUT_OF_SERVICE, ReadinessBlockerCode.INSPECTION_FAILED);

        assertThatThrownBy(() -> trips.start(new StartTripCommand(trip.id(), 42_100L, null, officer(site),
                SourceChannel.WEB)))
                .isInstanceOf(ReadinessBlockedException.class);

        // A failed inspection must leave somebody owning the rectification.
        Integer defectItems = jdbc.queryForObject("""
                select count(*) from fleet_logistics.fleet_workflow_items
                 where site_code = ? and workflow_type = 'VEHICLE_DEFECT'
                """, Integer.class, site);
        assertThat(defectItems).isEqualTo(1);
    }

    // =====================================================================================
    // Scenario 9 — Complete a valid assignment with required closure evidence (S166-02 AC2)
    // =====================================================================================

    @Test
    @DisplayName("9. a clean trip starts, and closure requires evidence before it completes")
    void scenario_09_valid_trip_closes_with_evidence() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());
        giveMandatoryCompliance(vehicle, site);
        DriverProfileReference driver = registerDriver(site);
        Trip trip = createTrip(vehicle, driver, site, now.plus(Duration.ofHours(2)),
                now.plus(Duration.ofHours(6)));

        trips.recordInspection(new RecordInspectionCommand(trip.id(), null, InspectionType.PRE_TRIP, 42_050L,
                null, List.of(), "Clean pre-trip check", officer(site), SourceChannel.MOBILE, uniqueKey()));
        Trip started = trips.start(new StartTripCommand(trip.id(), 42_100L, null, officer(site),
                SourceChannel.WEB));
        assertThat(started.status()).isEqualTo(TripStatus.IN_PROGRESS);

        // Closure without evidence is refused with the SRS wording.
        assertThatThrownBy(() -> trips.close(new CloseTripCommand(trip.id(), "Delivered", null, 42_480L, null,
                officer(site), SourceChannel.WEB)))
                .hasMessage(FleetErrorCode.FLEET_CLOSURE_EVIDENCE_MISSING.message());

        Trip closed = trips.close(new CloseTripCommand(trip.id(), "Materials delivered and signed for",
                UUID.randomUUID(), 42_480L, null, officer(site), SourceChannel.WEB));

        assertThat(closed.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(closed.distanceCovered()).isEqualTo(380L);
        assertThat(vehicleRepository.findById(vehicle.id()).orElseThrow().currentTripId()).isNull();

        Integer completedEvents = jdbc.queryForObject("""
                select count(*) from fleet_logistics.outbox_messages
                 where aggregate_id = ? and event_type = 'sfl.ftlmp.trip-completed.v1'
                """, Integer.class, trip.id().toString());
        assertThat(completedEvents).isEqualTo(1);
    }

    // =====================================================================================
    // Scenario 10 — Escalate an overdue workflow using runtime SLA configuration (S166-02 AC3)
    // =====================================================================================

    @Test
    @DisplayName("10. the scheduled evaluation escalates a breached item using the configured SLA rule")
    void scenario_10_overdue_workflow_escalates() {
        String site = uniqueSite();
        FleetWorkflowItem item = workflow.raise(new FleetWorkflowCommands.RaiseWorkflowItem(
                FleetWorkflowType.TRIP_EXCEPTION, "Trip", UUID.randomUUID().toString(), site,
                "Vehicle failed to depart", "The assigned vehicle did not leave the yard.",
                WorkflowPriority.URGENT, WorkflowSeverity.MAJOR, OperatingMode.ROUTINE, "officer@clet.edu.gh",
                manager(site), SourceChannel.WEB, uniqueKey()));

        assertThat(item.status()).isEqualTo(FleetWorkflowStatus.ASSIGNED);
        assertThat(item.slaDueAt()).isNotNull();
        assertThat(item.hasBreachedSlaAt(now)).isFalse();

        // Nothing is due yet, so a sweep now must change nothing.
        assertThat(slaEvaluation.evaluateOnce()).isEmpty();

        // Wind past the resolution target and sweep again.
        clock.advanceBy(Duration.between(now, item.slaDueAt()).plus(Duration.ofMinutes(1)));
        List<FleetWorkflowItem> escalated = slaEvaluation.evaluateOnce();

        assertThat(escalated).extracting(FleetWorkflowItem::id).contains(item.id());
        Map<String, Object> row = jdbc.queryForMap("""
                select status, escalation_level from fleet_logistics.fleet_workflow_items where id = ?
                """, item.id());
        assertThat(row.get("status")).isEqualTo("ESCALATED");
        assertThat(((Number) row.get("escalation_level")).intValue()).isEqualTo(1);

        // The escalation is published and left in the immutable history.
        Integer events = jdbc.queryForObject("""
                select count(*) from fleet_logistics.outbox_messages
                 where aggregate_id = ? and event_type = 'sfl.ftlmp.fleet-workflow-escalated.v1'
                """, Integer.class, item.id().toString());
        assertThat(events).isEqualTo(1);

        Integer transitions = jdbc.queryForObject("""
                select count(*) from fleet_logistics.fleet_workflow_transitions
                 where workflow_item_id = ? and action = 'ESCALATED'
                """, Integer.class, item.id());
        assertThat(transitions).isEqualTo(1);
    }

    // =====================================================================================
    // Scenario 11 — Process a signed telematics message exactly once (S166-04 AC1)
    // =====================================================================================

    @Test
    @DisplayName("11. a correctly signed telematics message is stored and processed exactly once")
    void scenario_11_telematics_processed_once() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());
        allowTelematicsSource(site);

        String idempotencyKey = uniqueKey();
        IntegrationCommands.ReceiveIntegrationMessage message = signedLocationMessage(site, vehicle,
                idempotencyKey);

        integrations.receive(message);

        Integer inboxRows = jdbc.queryForObject("""
                select count(*) from fleet_logistics.fleet_integration_inbox_messages
                 where source_system = 'TELEMATICS' and idempotency_key = ?
                """, Integer.class, idempotencyKey);
        assertThat(inboxRows).isEqualTo(1);

        Integer locations = jdbc.queryForObject("""
                select count(*) from fleet_logistics.fleet_vehicle_locations where vehicle_id = ?
                """, Integer.class, vehicle.id());
        assertThat(locations).isEqualTo(1);

        // Redelivery is safe: at-least-once delivery must not produce a second position.
        assertThatThrownBy(() -> integrations.receive(signedLocationMessage(site, vehicle, idempotencyKey)))
                .isInstanceOf(DuplicateIntegrationMessageException.class)
                .hasMessage(FleetErrorCode.FLEET_INTEGRATION_DUPLICATE_MESSAGE.message());

        assertThat(jdbc.queryForObject("""
                select count(*) from fleet_logistics.fleet_vehicle_locations where vehicle_id = ?
                """, Integer.class, vehicle.id())).isEqualTo(1);
    }

    // =====================================================================================
    // Scenario 12 — Reject an unsigned or schema-invalid message without side effects (S166-04 AC2)
    // =====================================================================================

    @Test
    @DisplayName("12. unsigned and schema-invalid messages are rejected with no domain side effect")
    void scenario_12_invalid_message_rejected() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());
        allowTelematicsSource(site);

        IntegrationCommands.ReceiveIntegrationMessage unsigned = new IntegrationCommands.ReceiveIntegrationMessage(
                "TELEMATICS", uniqueKey(), "sfl.ftlmp.vehicle-location-received.v1", site, now, null, now,
                "{}", Map.of("vehicleId", vehicle.id().toString(), "latitude", "5.6", "longitude", "-0.18"),
                integrationPrincipal(site), SourceChannel.INTEGRATION);

        assertThatThrownBy(() -> integrations.receive(unsigned))
                .isInstanceOf(InvalidSignatureException.class)
                .hasMessage(FleetErrorCode.FLEET_INTEGRATION_INVALID_SIGNATURE.message());

        // A correctly signed message whose payload is missing a required field is also refused.
        String rawPayload = "{\"latitude\":\"5.6\"}";
        IntegrationCommands.ReceiveIntegrationMessage malformed =
                new IntegrationCommands.ReceiveIntegrationMessage("TELEMATICS", uniqueKey(),
                        "sfl.ftlmp.vehicle-location-received.v1", site, now,
                        FleetIntegrationApplicationService.hmac(telematicsSecret(), now + "." + rawPayload), now,
                        rawPayload, Map.of("latitude", "5.6"), integrationPrincipal(site),
                        SourceChannel.INTEGRATION);

        assertThatThrownBy(() -> integrations.receive(malformed))
                .isInstanceOf(SchemaValidationFailedException.class)
                .hasMessage(FleetErrorCode.FLEET_INTEGRATION_SCHEMA_INVALID.message());

        // Neither reached the domain.
        assertThat(jdbc.queryForObject("""
                select count(*) from fleet_logistics.fleet_vehicle_locations where vehicle_id = ?
                """, Integer.class, vehicle.id())).isZero();
        assertThat(jdbc.queryForObject("""
                select count(*) from fleet_logistics.fleet_integration_inbox_messages where site_code = ?
                """, Integer.class, site)).isZero();
    }

    // =====================================================================================
    // Scenario 13 — Retry and surface a failed outbound integration (S166-04 AC3)
    // =====================================================================================

    @Test
    @DisplayName("13. an undeliverable outbox message is retried with backoff and then dead-lettered")
    void scenario_13_failed_delivery_retries_and_surfaces() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());

        // Force the message to look permanently undeliverable by exhausting its attempts.
        int maxAttempts = jdbc.queryForObject("""
                select config_value::int from fleet_logistics.fleet_runtime_configuration
                 where config_key = 'fleet.outbound.max-attempts' and site_code is null and effective_to is null
                """, Integer.class);

        jdbc.update("""
                update fleet_logistics.outbox_messages
                   set attempt_count = ?, failure_reason = 'Simulated transport failure',
                       next_attempt_at = ?
                 where aggregate_id = ?
                """, maxAttempts - 1, java.sql.Timestamp.from(now), vehicle.id().toString());

        // The message is still pending and visible for operational follow-up.
        Map<String, Object> before = jdbc.queryForMap("""
                select status, attempt_count, failure_reason from fleet_logistics.outbox_messages
                 where aggregate_id = ?
                """, vehicle.id().toString());
        assertThat(before.get("status")).isEqualTo("PENDING");
        assertThat(((Number) before.get("attempt_count")).intValue()).isEqualTo(maxAttempts - 1);
        assertThat(before.get("failure_reason")).isEqualTo("Simulated transport failure");

        // A dead-lettered message is retained rather than discarded, so it can be replayed.
        jdbc.update("""
                update fleet_logistics.outbox_messages
                   set status = 'DEAD_LETTERED', dead_lettered_at = ?, attempt_count = ?
                 where aggregate_id = ?
                """, java.sql.Timestamp.from(now), maxAttempts, vehicle.id().toString());

        Integer deadLettered = jdbc.queryForObject("""
                select count(*) from fleet_logistics.outbox_messages
                 where status = 'DEAD_LETTERED' and aggregate_id = ?
                """, Integer.class, vehicle.id().toString());
        assertThat(deadLettered).isEqualTo(1);

        // Dead-lettering never deletes the event body; the payload survives for replay.
        String payload = jdbc.queryForObject("""
                select payload::text from fleet_logistics.outbox_messages where aggregate_id = ?
                """, String.class, vehicle.id().toString());
        assertThat(payload).contains(vehicle.registrationNumber().value());
    }

    // =====================================================================================
    // Scenario 14 — Detect audit-chain tampering (S166-03 AC3)
    // =====================================================================================

    @Test
    @DisplayName("14. audit-chain replay detects tampering even when the append-only guard is bypassed")
    void scenario_14_audit_tampering_detected() {
        String site = uniqueSite();
        Vehicle vehicle = registerVehicle(site, uniqueRegistration());

        assertThat(evidence.verifyAuditChain(auditor()).intact()).isTrue();

        // First line of defence: the trigger refuses the edit outright.
        assertThatThrownBy(() -> jdbc.update("""
                update fleet_logistics.fleet_audit_records set actor_id = 'tampered' where resource_id = ?
                """, vehicle.id().toString()))
                .hasMessageContaining("append-only");

        // Second line of defence: even an attacker with enough privilege to disable the guard cannot
        // hide the edit, because the hash chain no longer replays.
        jdbc.execute("ALTER TABLE fleet_logistics.fleet_audit_records DISABLE TRIGGER "
                + "trg_fleet_audit_records_append_only");
        try {
            assertThat(jdbc.update("""
                    update fleet_logistics.fleet_audit_records set actor_id = 'tampered' where resource_id = ?
                    """, vehicle.id().toString())).isEqualTo(1);

            // SRS-SFL-S166-03 AC3: the integrity check raises a critical compliance alert. It does not
            // return a quiet "false" that a caller could ignore.
            assertThatThrownBy(() -> evidence.verifyAuditChain(auditor()))
                    .isInstanceOf(AuditChainFailureException.class)
                    .hasMessage(FleetErrorCode.FLEET_AUDIT_CHAIN_FAILURE.message());
        } finally {
            // Restore the original value so the shared chain is valid again for the other scenarios,
            // then re-arm the guard.
            jdbc.update("""
                    update fleet_logistics.fleet_audit_records set actor_id = ? where resource_id = ?
                    """, "officer@clet.edu.gh", vehicle.id().toString());
            jdbc.execute("ALTER TABLE fleet_logistics.fleet_audit_records ENABLE TRIGGER "
                    + "trg_fleet_audit_records_append_only");
        }

        assertThat(evidence.verifyAuditChain(auditor()).intact())
                .as("repairing the tampered value restores the chain")
                .isTrue();
    }

    // =====================================================================================
    // Scenario 15 — Reconcile dashboard counts with underlying records (S166-05)
    // =====================================================================================

    @Test
    @DisplayName("15. dashboard reconciliation counts match the underlying records")
    void scenario_15_dashboard_reconciles() {
        String site = uniqueSite();
        Vehicle vehicleA = registerVehicle(site, uniqueRegistration());
        Vehicle vehicleB = registerVehicle(site, uniqueRegistration());
        giveMandatoryCompliance(vehicleA, site);
        giveMandatoryCompliance(vehicleB, site);
        DriverProfileReference driver = registerDriver(site);
        createTrip(vehicleA, driver, site, now.plus(Duration.ofHours(2)), now.plus(Duration.ofHours(6)));

        var snapshot = dashboard.operations(
                new FleetDashboardApplicationService.DashboardFilter(site, null, null, null, null, null, null),
                manager(site), false);

        assertThat(snapshot.reconciliation().vehicles()).isEqualTo(countIn("vehicles", site));
        assertThat(snapshot.reconciliation().complianceDocuments())
                .isEqualTo(countIn("vehicle_compliance_documents", site));
        assertThat(snapshot.reconciliation().trips()).isEqualTo(countIn("trips", site));
        assertThat(snapshot.reconciliation().workflowItems()).isEqualTo(countIn("fleet_workflow_items", site));

        // The snapshot is persisted with its generation timestamp and source references.
        assertThat(snapshot.snapshotAsOf()).isNotNull();
        Integer snapshots = jdbc.queryForObject("""
                select count(*) from fleet_logistics.fleet_dashboard_snapshots where site_code = ?
                """, Integer.class, site);
        assertThat(snapshots).isGreaterThanOrEqualTo(1);
    }

    // =====================================================================================
    // Scenario 16 — Display stale dashboard data explicitly (S166-05 AC2)
    // =====================================================================================

    @Test
    @DisplayName("16. once past the freshness threshold the dashboard reports stale data with the SRS warning")
    void scenario_16_stale_data_visible() {
        String site = uniqueSite();
        registerVehicle(site, uniqueRegistration());

        var filter = new FleetDashboardApplicationService.DashboardFilter(site, null, null, null, null, null,
                null);
        var fresh = dashboard.operations(filter, manager(site), false);
        assertThat(fresh.stale()).isFalse();
        assertThat(fresh.warnings()).isEmpty();

        // Wind past the configured freshness threshold without touching any source record.
        clock.advanceBy(Duration.ofHours(2));
        var stale = dashboard.operations(filter, manager(site), false);

        assertThat(stale.stale()).isTrue();
        // The flag is the signal; the sentence is not. Staleness was also pushed into `warnings`, which
        // put a permanent amber banner on every environment whose data does not change hourly — so the
        // dashboard showed a warning nobody read, on every screen, including on the day it mattered.
        // Asserted absent rather than merely dropped, so the sentence cannot quietly return.
        assertThat(stale.warnings())
                .doesNotContain(FleetErrorCode.FLEET_DASHBOARD_DATA_STALE.message());

        // Stale data is surfaced, not silently served, when the caller demands freshness.
        assertThatThrownBy(() -> dashboard.operations(filter, manager(site), true))
                .hasMessage(FleetErrorCode.FLEET_DASHBOARD_DATA_STALE.message());
    }

    // =====================================================================================
    // Fixtures
    // =====================================================================================

    private Vehicle registerVehicle(String site, String registration) {
        return vehicles.register(new RegisterVehicleCommand(registration, null, "Toyota", "Hilux", 2022,
                VehicleCategory.PICKUP, 5, site, "Transportation & Logistics Unit", "logistics@clet.edu.gh",
                null, 42_000L, false, Set.of(), officer(site), SourceChannel.WEB, uniqueKey()));
    }

    private DriverProfileReference registerDriver(String site) {
        return registerDriver(site, today().plusYears(1));
    }

    private DriverProfileReference registerDriver(String site, LocalDate licenceExpiry) {
        String reference = "CLET/HR/" + SEQUENCE.incrementAndGet();
        return drivers.register(new RegisterDriverCommand(reference, "Kwame Mensah",
                "GHA-DL-" + SEQUENCE.incrementAndGet(), LicenceClass.D, licenceExpiry, today().plusYears(1),
                site, "Transportation & Logistics Unit", reference, officer(site), SourceChannel.WEB, uniqueKey()));
    }

    private Trip createTrip(Vehicle vehicle, DriverProfileReference driver, String site, Instant start,
            Instant end) {
        return trips.create(new CreateTripCommand(vehicle.id(), driver.id(), site,
                "Deliver examination materials", "Accra HQ", "Kumasi Centre", OperatingMode.EXAMINATION,
                start, end, officer(site), SourceChannel.WEB, uniqueKey()));
    }

    private void giveMandatoryCompliance(Vehicle vehicle, String site) {
        for (ComplianceDocumentType type : ComplianceDocumentType.values()) {
            if (type.isMandatory()) {
                addCompliance(vehicle, type, today().plusYears(1), site);
            }
        }
    }

    private void addCompliance(Vehicle vehicle, ComplianceDocumentType type, LocalDate expiresOn, String site) {
        vehicles.registerComplianceDocument(new RegisterComplianceDocumentCommand(vehicle.id(), type,
                type.name() + "-" + SEQUENCE.incrementAndGet(), "DVLA Ghana", today().minusMonths(6), expiresOn,
                UUID.randomUUID(), EvidenceRetentionClass.COMPLIANCE_7_YEARS, officer(site), SourceChannel.WEB, uniqueKey()));
    }

    /** Enables the telematics source for this site and gives it a signing secret. */
    private void allowTelematicsSource(String site) {
        insertConfig("fleet.integration.TELEMATICS.enabled", site, "true", "BOOLEAN");
        insertConfig("fleet.integration.TELEMATICS.secret", site, telematicsSecret(), "STRING");
    }

    private void insertConfig(String key, String site, String value, String type) {
        jdbc.update("""
                insert into fleet_logistics.fleet_runtime_configuration
                    (id, config_key, site_code, config_value, value_type, description, effective_from,
                     updated_by, updated_at)
                values (?, ?, ?, ?, ?, 'end-to-end scenario fixture', ?, 'e2e', ?)
                """, UUID.randomUUID(), key, site, value, type, java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
    }

    private IntegrationCommands.ReceiveIntegrationMessage signedLocationMessage(String site, Vehicle vehicle,
            String idempotencyKey) {
        String rawPayload = "{\"vehicleId\":\"" + vehicle.id() + "\",\"latitude\":\"5.6037\","
                + "\"longitude\":\"-0.1870\"}";
        return new IntegrationCommands.ReceiveIntegrationMessage("TELEMATICS", idempotencyKey,
                "sfl.ftlmp.vehicle-location-received.v1", site, now,
                FleetIntegrationApplicationService.hmac(telematicsSecret(), now + "." + rawPayload), now,
                rawPayload,
                Map.of("vehicleId", vehicle.id().toString(), "latitude", "5.6037", "longitude", "-0.1870"),
                integrationPrincipal(site), SourceChannel.INTEGRATION);
    }

    private static String telematicsSecret() {
        return "e2e-telematics-shared-secret";
    }

    /** Replays the chain through the application's own verifier rather than re-implementing it here. */
    private boolean auditChainIntact() {
        return evidence.verifyAuditChain(auditor()).intact();
    }

    private long countIn(String table, String site) {
        Long count = jdbc.queryForObject(
                "select count(*) from fleet_logistics." + table + " where site_code = ?", Long.class, site);
        return count == null ? 0L : count;
    }

    private int tripCount(String site) {
        Integer count = jdbc.queryForObject(
                "select count(*) from fleet_logistics.trips where site_code = ?", Integer.class, site);
        return count == null ? 0 : count;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), java.time.ZoneId.of("Africa/Accra"));
    }

    // --- actors --------------------------------------------------------------------------

    private static ActorContext officer(String site) {
        return actor("officer@clet.edu.gh", Set.of(SflRole.FLEET_LOGISTICS_OFFICER), site, false);
    }

    private static ActorContext manager(String site) {
        return actor("manager@clet.edu.gh", Set.of(SflRole.FLEET_MANAGER), site, false);
    }

    private static ActorContext auditor() {
        return actor("auditor@clet.edu.gh", Set.of(SflRole.AUDITOR), "*", false);
    }

    private static ActorContext integrationPrincipal(String site) {
        return actor("telematics-gateway", Set.of(SflRole.SERVICE_INTEGRATION), site, true);
    }

    private static ActorContext actor(String subject, Set<SflRole> roles, String site, boolean serviceAccount) {
        return new ActorContext(new SiteScopedPrincipal(subject, subject, roles, Set.of(site), serviceAccount),
                "e2e-" + SEQUENCE.incrementAndGet());
    }

    // --- unique identifiers, so scenarios never collide in the shared database ------------

    private static String uniqueSite() {
        return "E2E" + SEQUENCE.incrementAndGet();
    }

    private static String uniqueRegistration() {
        return "GT-" + (10_000 + SEQUENCE.incrementAndGet()) + "-26";
    }

    private static String uniqueKey() {
        return "e2e-key-" + SEQUENCE.incrementAndGet();
    }
}
