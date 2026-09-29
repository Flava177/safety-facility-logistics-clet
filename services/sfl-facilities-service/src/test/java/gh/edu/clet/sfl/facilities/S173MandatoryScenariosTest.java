package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.hse.RiskAssessmentCurrency;
import gh.edu.clet.sfl.facilities.booking.application.BookableResourceService;
import gh.edu.clet.sfl.facilities.booking.application.BookingAvailabilityService;
import gh.edu.clet.sfl.facilities.booking.application.BookingCommands;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventHandoffService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventLogisticsCommands;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventLogisticsConfiguration;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventReadinessService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventReadinessView;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventResourceRequestService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventRiskCriteriaService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.EventSetupTaskService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.RiskAssessmentProjectionService;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CateringGatewayPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CcpEventsDirectoryPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CleaningCapacityPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.GatewayOutcome;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.DeliveryOutcome;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.OwningSystem;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration.RecordedCcpEventsDirectory;
import gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration.S153MaintenanceGateway;
import gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration.S159BookingGateway;
import gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration.UnbuiltCateringGateway;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorInboxPort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorRejectionRecorder;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorSourcePort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.security.SecurityEvent;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorRejectionReason;
import gh.edu.clet.sfl.facilities.support.IfimpTestHarness;
import gh.edu.clet.sfl.facilities.support.InMemoryEventLogisticsRepository;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The S173 acceptance criteria, end to end through the application services - one {@code @Nested} class
 * per SRS requirement, one test per acceptance criterion, validation rule and error state, following the
 * pattern of {@code S159MandatoryScenariosTest}.
 *
 * <p>{@link #directory} uses the real {@code RecordedCcpEventsDirectory} over the in-memory register, so
 * "a hand-off is rejected unless S173 already holds a task, or the hand-off itself is a signed
 * confirmation" is exercised as the shipped adapter behaves, not as a mock is told to answer.
 * {@link #booking} and {@link #maintenance} are the real S159/S153 gateways over the harness's real S159
 * and S153 services - both are built and in the same deployable, so nothing about routing to them is
 * simulated. {@link #cleaning} is a double of S173's own {@code CleaningCapacityPort}, because S169 is
 * being built in a different worktree and its placeholder bean throws - exactly what the build brief
 * asks for.
 */
class S173MandatoryScenariosTest {

    private IfimpTestHarness harness;
    private InMemoryEventLogisticsRepository repository;
    private EventLogisticsConfiguration configuration;
    private FakeVendorSource vendorSource;
    private FakeVendorInbox vendorInbox;
    private List<SecurityEvent> siem;
    private FakeCleaningCapacity cleaning;
    private CcpEventsDirectoryPort directory;
    private EventHandoffService handoffs;
    private EventResourceRequestService requests;
    private EventSetupTaskService tasks;
    private EventReadinessService readiness;
    private EventRiskCriteriaService riskCriteria;
    private RiskAssessmentProjectionService riskProjection;

    private static final String SECRET = "test-secret";
    private static final Instant NOW = IfimpTestHarness.NOW;
    private static final Instant START = NOW.plus(Duration.ofDays(10));
    private static final Instant END = START.plus(Duration.ofHours(4));

    @BeforeEach
    void setUp() {
        harness = new IfimpTestHarness();
        repository = new InMemoryEventLogisticsRepository();
        configuration = new EventLogisticsConfiguration(harness.configuration);

        // The real EventBookingObserver: S159 confirming or withdrawing the venue booking pushes the
        // new state straight into S173's requests, same as production. Registered before the first
        // harness.bookings() call, as the harness's own Javadoc requires. requests is assigned later in
        // this method; the lambda reads the field at call time, well after setUp() has finished, so the
        // forward reference is safe.
        harness.bookingObservers.add(new gh.edu.clet.sfl.facilities.booking.application.ports.BookingLifecycleObserver() {
            @Override
            public void bookingConfirmed(Booking booking, ActorContext actor) {
                requests.bookingChanged(booking.id().toString(), S159BookingGateway.map(booking), actor);
            }

            @Override
            public void bookingWithdrawn(Booking booking, String reason, ActorContext actor) {
                requests.bookingChanged(booking.id().toString(), S159BookingGateway.map(booking), actor);
            }
        });

        BookingAvailabilityService availability = new BookingAvailabilityService(harness.bookingStore,
                harness.facilities, harness.authorization);
        BookableResourceService resources = new BookableResourceService(harness.bookingStore, harness.bookings(),
                harness.authorization, harness.audit, harness.idempotency, harness.clock);
        S159BookingGateway bookingGateway = new S159BookingGateway(harness.bookings(), resources, availability);
        S153MaintenanceGateway maintenanceGateway = new S153MaintenanceGateway(harness.intake);
        cleaning = new FakeCleaningCapacity();
        CateringGatewayPort catering = new UnbuiltCateringGateway();

        directory = new RecordedCcpEventsDirectory(repository);
        vendorSource = new FakeVendorSource();
        vendorInbox = new FakeVendorInbox();
        siem = new ArrayList<>();
        VendorRejectionRecorder rejectionRecorder = new VendorRejectionRecorder(vendorInbox, harness.audit,
                event -> {
                    siem.add(event);
                    return new gh.edu.clet.sfl.facilities.shared.application.port.SecurityEventForwarderPort
                            .ForwardResult("TEST", false);
                }, harness.outbox, harness.clock);
        VendorMessageVerifier verifier = new VendorMessageVerifier(vendorSource, vendorInbox, rejectionRecorder,
                harness.clock);

        gh.edu.clet.sfl.facilities.eventlogistics.application.EventLogisticsRefusalRecorder refusals =
                new gh.edu.clet.sfl.facilities.eventlogistics.application.EventLogisticsRefusalRecorder(repository,
                        harness.audit);

        requests = new EventResourceRequestService(repository, bookingGateway, maintenanceGateway, cleaning,
                catering, configuration, harness.authorization, harness.audit, harness.outbox, harness.clock);
        handoffs = new EventHandoffService(repository, harness.facilities, directory, verifier, refusals, requests,
                harness.authorization, harness.audit, harness.outbox, harness.clock);
        tasks = new EventSetupTaskService(repository, configuration, refusals, harness.authorization, harness.audit,
                harness.outbox, harness.clock);
        readiness = new EventReadinessService(repository, configuration, harness.authorization, harness.audit,
                harness.outbox, harness.clock);
        riskCriteria = new EventRiskCriteriaService(harness.configuration, configuration, directory,
                harness.authorization, harness.audit);
        riskProjection = new RiskAssessmentProjectionService(repository);
    }

    // =============================================================================================
    // Helpers
    // =============================================================================================

    private SignedVendorMessage confirmedHandoff(String reference, String idempotencyKey, String roomCode,
            int attendance, String category, boolean externalContractors, boolean temporaryStructures) {
        return signed(idempotencyKey, Map.ofEntries(
                Map.entry("s078EventReference", reference),
                Map.entry("status", "CONFIRMED"),
                Map.entry("title", "Founders' Day " + reference),
                Map.entry("startsAt", START.toString()),
                Map.entry("endsAt", END.toString()),
                Map.entry("roomCode", roomCode),
                Map.entry("expectedAttendance", String.valueOf(attendance)),
                Map.entry("eventCategory", category),
                Map.entry("statedRequirements", "Stage, PA system, extra security"),
                Map.entry("externalContractors", String.valueOf(externalContractors)),
                Map.entry("temporaryStructures", String.valueOf(temporaryStructures))));
    }

    private SignedVendorMessage statusOnly(String reference, String status, String idempotencyKey) {
        return signed(idempotencyKey, Map.of("s078EventReference", reference, "status", status));
    }

    private SignedVendorMessage signed(String idempotencyKey, Map<String, Object> payload) {
        String rawPayload = "payload:" + idempotencyKey;
        String signature = VendorMessageVerifier.sign(SECRET, NOW, rawPayload);
        return new SignedVendorMessage("CCP-EVENTS-SIM", "event.handoff", idempotencyKey, "MAIN", NOW, signature,
                rawPayload, payload);
    }

    private EventSetupTask acceptedTask(String reference) {
        return acceptedTaskInRoom(reference, "HALL-A");
    }

    /** A distinct room per task avoids two independent test events clashing for the same S159 slot. */
    private EventSetupTask acceptedTaskInRoom(String reference, String roomCode) {
        return handoffs.accept(confirmedHandoff(reference, reference + "-k1", roomCode, 40, "MOOT", false, false),
                harness.integration).task();
    }

    // =============================================================================================
    // SRS-SFL-S173-01: Event Hand-Off Intake from CCP Events
    // =============================================================================================

    @Nested
    class HandoffIntake {

        @Test
        @DisplayName("a confirmed event creates a set-up task carrying date, location, attendance and requirements - no re-entry")
        void confirmed_event_creates_task_without_manual_reentry() {
            EventHandoffService.HandoffResult result = handoffs.accept(
                    confirmedHandoff("S078-1001", "k1", "HALL-A", 40, "MOOT", false, false), harness.integration);

            assertThat(result.duplicate()).isFalse();
            EventSetupTask task = result.task();
            assertThat(task.s078EventReference()).isEqualTo("S078-1001");
            assertThat(task.details().startsAt()).isEqualTo(START);
            assertThat(task.details().endsAt()).isEqualTo(END);
            assertThat(task.details().roomCode()).isEqualTo("HALL-A");
            assertThat(task.details().roomId()).isEqualTo(harness.hall.id());
            assertThat(task.details().expectedAttendance()).isEqualTo(40);
            assertThat(task.details().statedRequirements()).isEqualTo("Stage, PA system, extra security");
            assertThat(task.status()).isEqualTo(EventSetupTaskStatus.OPEN);
            assertThat(harness.audit.recorded(AuditAction.EVENT_HANDOFF_ACCEPTED)).isTrue();
            assertThat(harness.audit.recorded(AuditAction.EVENT_SETUP_TASK_CREATED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.event-handoff-accepted.v1")).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.event-setup-task-created.v1")).isTrue();
        }

        @Test
        @DisplayName("a hand-off for an event S078 does not confirm is rejected, EVENT_REFERENCE_UNRESOLVABLE, nothing created")
        void unconfirmed_event_is_rejected() {
            assertThatThrownBy(() -> handoffs.accept(statusOnly("S078-9999", "TENTATIVE", "k1"), harness.integration))
                    .isInstanceOfSatisfying(FacilitiesException.class,
                            e -> assertThat(e.code()).isEqualTo(FacilitiesErrorCode.EVENT_REFERENCE_UNRESOLVABLE));

            assertThat(repository.findTaskByS078Reference("S078-9999")).isEmpty();
            assertThat(harness.audit.recorded(AuditAction.EVENT_HANDOFF_REJECTED)).isTrue();
            assertThat(repository.findHandoffsForReference("S078-9999")).singleElement()
                    .satisfies(h -> assertThat(h.outcome())
                            .isEqualTo(gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff.Outcome.REJECTED));
        }

        @Test
        @DisplayName("a hand-off for a reference S173 has never seen cannot be cancelled - unresolvable, nothing created")
        void unknown_reference_cancellation_is_rejected() {
            assertThatThrownBy(() -> handoffs.accept(statusOnly("S078-UNKNOWN", "CANCELLED", "k1"),
                    harness.integration)).isInstanceOfSatisfying(FacilitiesException.class,
                            e -> assertThat(e.code()).isEqualTo(FacilitiesErrorCode.EVENT_REFERENCE_UNRESOLVABLE));
            assertThat(repository.findTaskByS078Reference("S078-UNKNOWN")).isEmpty();
        }

        @Test
        @DisplayName("a forged hand-off is rejected and logged; nothing is created")
        void forged_handoff_is_rejected_and_nothing_created() {
            SignedVendorMessage genuine = confirmedHandoff("S078-FORGED", "k1", "HALL-A", 40, "MOOT", false, false);
            SignedVendorMessage forged = new SignedVendorMessage(genuine.sourceId(), genuine.messageType(),
                    genuine.idempotencyKey(), genuine.siteCode(), genuine.signedAt(), "00ff-not-a-real-signature",
                    genuine.rawPayload(), genuine.payload());

            assertThatThrownBy(() -> handoffs.accept(forged, harness.integration))
                    .isInstanceOfSatisfying(FacilitiesException.class,
                            e -> assertThat(e.code()).isEqualTo(FacilitiesErrorCode.VENDOR_MESSAGE_REJECTED));

            assertThat(repository.findTaskByS078Reference("S078-FORGED")).isEmpty();
            assertThat(vendorInbox.rejections).containsExactly(VendorRejectionReason.SIGNATURE_INVALID);
            assertThat(harness.audit.recorded(AuditAction.VENDOR_MESSAGE_REJECTED)).isTrue();
            assertThat(siem).isNotEmpty();
            assertThat(harness.outbox.published("sfl.integration.vendor-message-rejected.v1")).isTrue();
        }

        @Test
        @DisplayName("the same hand-off delivered twice under one idempotency key is a duplicate, not re-actioned")
        void duplicate_handoff_is_not_reactioned() {
            SignedVendorMessage message = confirmedHandoff("S078-DUP", "k1", "HALL-A", 40, "MOOT", false, false);
            EventHandoffService.HandoffResult first = handoffs.accept(message, harness.integration);
            harness.audit.clear();
            harness.outbox.clear();

            EventHandoffService.HandoffResult second = handoffs.accept(message, harness.integration);

            assertThat(second.duplicate()).isTrue();
            assertThat(second.task().id()).isEqualTo(first.task().id());
            assertThat(harness.audit.recorded(AuditAction.EVENT_HANDOFF_ACCEPTED)).isFalse();
            assertThat(harness.audit.recorded(AuditAction.EVENT_SETUP_TASK_CREATED)).isFalse();
        }

        @Test
        @DisplayName("an updated hand-off for the same S078 event updates the task idempotently")
        void updated_handoff_updates_the_task() {
            EventSetupTask created = acceptedTask("S078-UPD");
            SignedVendorMessage moved = confirmedHandoff("S078-UPD", "S078-UPD-k2", "MEET-1", 8, "MOOT", false,
                    false);

            EventHandoffService.HandoffResult result = handoffs.accept(moved, harness.integration);

            assertThat(result.task().id()).isEqualTo(created.id());
            assertThat(result.task().details().roomCode()).isEqualTo("MEET-1");
            assertThat(result.task().details().expectedAttendance()).isEqualTo(8);
            assertThat(result.task().handoffCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("a cancelled hand-off for the same S078 event cancels the task idempotently")
        void cancelled_handoff_cancels_the_task() {
            EventSetupTask created = acceptedTask("S078-CANCEL");

            EventHandoffService.HandoffResult result = handoffs.accept(
                    statusOnly("S078-CANCEL", "CANCELLED", "S078-CANCEL-k2"), harness.integration);

            assertThat(result.task().id()).isEqualTo(created.id());
            assertThat(result.task().status()).isEqualTo(EventSetupTaskStatus.CANCELLED);

            // Cancelling again is idempotent: nothing further changes and no error is thrown.
            EventHandoffService.HandoffResult repeated = handoffs.accept(
                    statusOnly("S078-CANCEL", "CANCELLED", "S078-CANCEL-k3"), harness.integration);
            assertThat(repeated.task().status()).isEqualTo(EventSetupTaskStatus.CANCELLED);
        }

        @Test
        @DisplayName("no set-up task may exist without a resolvable S078 reference - the domain factory refuses a blank one")
        void the_domain_refuses_a_blank_reference() {
            assertThatThrownBy(() -> gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask.fromHandoff(
                    UUID.randomUUID(), "MAIN", "EV-MAIN-000001", "  ", eventDetails(), "coordinator", NOW,
                    SourceChannel.INTEGRATION, "corr")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("the coordinator decomposes the hand-off into typed resource requests, not free text")
        void decomposes_into_typed_requests() {
            EventSetupTask task = acceptedTask("S078-DECOMPOSE");

            List<EventResourceRequest> created = requests.decompose(new EventLogisticsCommands.DecomposeSetupTask(
                    task.id(), List.of(
                            new EventLogisticsCommands.NewResourceRequest(EventResourceType.VENUE, "Main hall", 1,
                                    null, null, null),
                            new EventLogisticsCommands.NewResourceRequest(EventResourceType.STAGING,
                                    "Staging blocks", 4, harness.hall.id() == null ? null : registerResource(
                                            EventResourceType.STAGING), null, null)),
                    false, harness.eventCoordinator, SourceChannel.WEB));

            assertThat(created).extracting(EventResourceRequest::resourceType)
                    .containsExactlyInAnyOrder(EventResourceType.VENUE, EventResourceType.STAGING);
            assertThat(created).allSatisfy(request -> assertThat(request.description()).isNotBlank());
        }

        private gh.edu.clet.sfl.facilities.eventlogistics.domain.EventDetails eventDetails() {
            return new gh.edu.clet.sfl.facilities.eventlogistics.domain.EventDetails("Founders' Day", "MOOT", START,
                    END, harness.hall.id(), "HALL-A", 40, null, false, false);
        }
    }

    // =============================================================================================
    // SRS-SFL-S173-02: Resource Requests Across Room Booking, Maintenance, Cleaning and Catering
    // =============================================================================================

    @Nested
    class ResourceRouting {

        @Test
        @DisplayName("VENUE routes to S159 as a booking request; awaiting approval maps to REQUESTED")
        void venue_routes_to_s159() {
            EventSetupTask task = acceptedTask("S078-VENUE");
            List<EventResourceRequest> created = decompose(task, venue());

            EventResourceRequest venue = only(created, EventResourceType.VENUE);
            assertThat(venue.owningSystem()).isEqualTo(OwningSystem.S159);
            assertThat(venue.status()).isEqualTo(ResourceRequestStatus.REQUESTED);
            assertThat(venue.externalReference()).isNotBlank();
        }

        @Test
        @DisplayName("S159 confirming the booking maps the request to CONFIRMED")
        void s159_confirmation_maps_to_confirmed() {
            EventSetupTask task = acceptedTask("S078-VENUE-CONFIRM");
            EventResourceRequest venue = only(decompose(task, venue()), EventResourceType.VENUE);

            harness.bookings().decide(new BookingCommands.DecideBooking(UUID.fromString(venue.externalReference()),
                    true, null, null, harness.director, SourceChannel.WEB));

            EventResourceRequest refreshed = repository.findRequest(venue.id()).orElseThrow();
            assertThat(refreshed.status()).isEqualTo(ResourceRequestStatus.CONFIRMED);
        }

        @Test
        @DisplayName("a VENUE conflict maps to CONFLICTED naming the competing commitment")
        void venue_conflict_is_named() {
            // Book the hall out from under the event first, through an ordinary S159 request.
            harness.bookings().request(new BookingCommands.RequestBooking(harness.hall.id(), BookingPurpose.LECTURE,
                    "Contracts revision", null, START, END, null, null, 40, null, Map.of(), null, null,
                    harness.director, SourceChannel.WEB, null, null));

            EventSetupTask task = acceptedTask("S078-VENUE-CONFLICT");
            EventResourceRequest venue = only(decompose(task, venue()), EventResourceType.VENUE);

            assertThat(venue.status()).isEqualTo(ResourceRequestStatus.CONFLICTED);
            assertThat(venue.competingCommitment()).isNotBlank();
        }

        @Test
        @DisplayName("AV/STAGING/SECURITY/SIGNAGE wait for the venue, then allocate onto it once booked")
        void resource_lines_wait_for_the_venue_then_route() {
            EventSetupTask task = acceptedTask("S078-AV");
            UUID resourceId = registerResource(EventResourceType.AV);

            // The AV line is decomposed before the venue exists: there is nothing to allocate onto yet.
            EventResourceRequest av = only(decompose(task, new EventLogisticsCommands.NewResourceRequest(
                    EventResourceType.AV, "Projector and screen", 1, resourceId, null, null)),
                    EventResourceType.AV);
            assertThat(av.status()).isEqualTo(ResourceRequestStatus.REQUESTED);
            assertThat(av.externalReference()).isNull();
            assertThat(av.statusDetail()).contains("Waiting for the venue");

            // Booking the venue routes the waiting line automatically.
            EventResourceRequest venueRequest = only(decompose(task, venue()), EventResourceType.VENUE);
            EventResourceRequest waitingResolved = repository.findRequest(av.id()).orElseThrow();
            assertThat(waitingResolved.externalReference()).isNotNull();
            assertThat(waitingResolved.externalParentReference()).isEqualTo(venueRequest.externalReference());
            // The venue itself is still only REQUESTED (S159 has not approved it yet).
            assertThat(waitingResolved.status()).isEqualTo(ResourceRequestStatus.REQUESTED);

            harness.bookings().decide(new BookingCommands.DecideBooking(
                    UUID.fromString(venueRequest.externalReference()), true, null, null, harness.director,
                    SourceChannel.WEB));

            // The observer fires on confirmation and every line held by that booking follows it.
            EventResourceRequest confirmedAv = repository.findRequest(av.id()).orElseThrow();
            assertThat(confirmedAv.status()).isEqualTo(ResourceRequestStatus.CONFIRMED);
        }

        @Test
        @DisplayName("PRE_EVENT_MAINTENANCE routes to S153's automated intake under EVENT_PRE_MAINTENANCE")
        void pre_event_maintenance_routes_to_s153() {
            EventSetupTask task = acceptedTask("S078-MAINT");
            EventResourceRequest maintenance = only(decompose(task,
                    new EventLogisticsCommands.NewResourceRequest(EventResourceType.PRE_EVENT_MAINTENANCE,
                            "Fix the air conditioning before the event", 1, null, null, null)),
                    EventResourceType.PRE_EVENT_MAINTENANCE);

            assertThat(maintenance.owningSystem()).isEqualTo(OwningSystem.S153);
            assertThat(maintenance.status()).isEqualTo(ResourceRequestStatus.CONFIRMED);
            var raised = harness.intake.find(UUID.fromString(maintenance.externalReference())).orElseThrow();
            assertThat(harness.maintenance.findFault(raised.faultId()).orElseThrow().category())
                    .isEqualTo("EVENT_PRE_MAINTENANCE");
        }

        @Test
        @DisplayName("CLEANING routes to S169's EventCleaningCapacity contract, confirmed when reserved")
        void cleaning_routes_to_s169_contract() {
            cleaning.nextOutcome = GatewayOutcome.of(ResourceRequestStatus.CONFIRMED, "RES-1", "Reserved in S169.");
            EventSetupTask task = acceptedTask("S078-CLEAN");

            EventResourceRequest cleaningRequest = only(decompose(task,
                    new EventLogisticsCommands.NewResourceRequest(EventResourceType.CLEANING, "Post-event clean", 1,
                            null, null, null)), EventResourceType.CLEANING);

            assertThat(cleaningRequest.owningSystem()).isEqualTo(OwningSystem.S169);
            assertThat(cleaningRequest.status()).isEqualTo(ResourceRequestStatus.CONFIRMED);
            assertThat(cleaningRequest.externalReference()).isEqualTo("RES-1");
        }

        @Test
        @DisplayName("a CLEANING conflict carries and displays the named competing commitment (S169-04)")
        void cleaning_conflict_names_the_competing_commitment() {
            cleaning.nextOutcome = GatewayOutcome.conflict(
                    "CT-MAIN-000123, routine clean of Hall A 09:00-11:00 by Spotless Ltd");
            EventSetupTask task = acceptedTask("S078-CLEAN-CONFLICT");

            EventResourceRequest cleaningRequest = only(decompose(task,
                    new EventLogisticsCommands.NewResourceRequest(EventResourceType.CLEANING, "Post-event clean", 1,
                            null, null, null)), EventResourceType.CLEANING);

            assertThat(cleaningRequest.status()).isEqualTo(ResourceRequestStatus.CONFLICTED);
            assertThat(cleaningRequest.competingCommitment())
                    .contains("CT-MAIN-000123", "routine clean of Hall A", "Spotless Ltd");

            EventReadinessView view = tasks.readiness(task.id(), harness.eventCoordinator, SourceChannel.WEB);
            assertThat(view.conflicts()).extracting(EventResourceRequest::competingCommitment)
                    .anyMatch(commitment -> commitment.contains("Spotless Ltd"));
        }

        @Test
        @DisplayName("CATERING before S172 exists is recorded as an explicit manual coordination item, never fulfilled")
        void catering_is_manual_coordination_not_fulfilled() {
            EventSetupTask task = acceptedTask("S078-CATERING");

            EventResourceRequest catering = only(decompose(task,
                    new EventLogisticsCommands.NewResourceRequest(EventResourceType.CATERING, "Reception for 100", 1,
                            null, null, null)), EventResourceType.CATERING);

            assertThat(catering.status()).isEqualTo(ResourceRequestStatus.MANUAL_COORDINATION);
            assertThat(catering.statusDetail()).contains("S172").contains("Phase 3");
            assertThat(catering.status()).isNotEqualTo(ResourceRequestStatus.FULFILLED);
            assertThat(harness.outbox.published("sfl.ifimp.event-manual-coordination-recorded.v1")).isTrue();

            // It is never silently dropped: it appears, marked, in the consolidated view.
            EventReadinessView view = tasks.readiness(task.id(), harness.eventCoordinator, SourceChannel.WEB);
            assertThat(view.manualCoordination()).extracting(EventResourceRequest::resourceType)
                    .contains(EventResourceType.CATERING);
        }

        @Test
        @DisplayName("a named person accepting a manual coordination item is recorded, and the status never becomes fulfilled")
        void manual_coordination_can_be_accepted_by_a_named_person() {
            EventSetupTask task = acceptedTask("S078-CATERING-ACCEPT");
            EventResourceRequest catering = only(decompose(task,
                    new EventLogisticsCommands.NewResourceRequest(EventResourceType.CATERING, "Reception for 100", 1,
                            null, null, null)), EventResourceType.CATERING);

            EventResourceRequest accepted = requests.acceptManualCoordination(
                    new EventLogisticsCommands.AcceptManualCoordination(catering.id(), "Campus Kitchens (verbal)",
                            harness.eventCoordinator, SourceChannel.WEB));

            assertThat(accepted.status()).isEqualTo(ResourceRequestStatus.MANUAL_COORDINATION);
            assertThat(accepted.manualAcceptedBy()).isEqualTo(harness.eventCoordinator.actorId());
            assertThat(accepted.manualArrangedWith()).isEqualTo("Campus Kitchens (verbal)");
            assertThat(accepted.isManualCoordinationAccepted()).isTrue();
            assertThat(harness.audit.recorded(AuditAction.EVENT_MANUAL_COORDINATION_ACCEPTED)).isTrue();
        }

        @Test
        @DisplayName("an owning system switched off in the registry is routed as manual coordination")
        void unavailable_owning_system_becomes_manual_coordination() {
            harness.configuration.set(EventLogisticsConfiguration.availabilityKey(OwningSystem.S169), "false");
            EventSetupTask task = acceptedTask("S078-S169-OFF");

            EventResourceRequest cleaningRequest = only(decompose(task,
                    new EventLogisticsCommands.NewResourceRequest(EventResourceType.CLEANING, "Post-event clean", 1,
                            null, null, null)), EventResourceType.CLEANING);

            assertThat(cleaningRequest.status()).isEqualTo(ResourceRequestStatus.MANUAL_COORDINATION);
        }

        @Test
        @DisplayName("statuses roll up into one consolidated readiness view: unresourced, conflicted, awaiting, ready")
        void statuses_roll_up_into_one_view() {
            EventSetupTask unresourced = acceptedTask("S078-UNRESOURCED");
            assertThat(tasks.readiness(unresourced.id(), harness.eventCoordinator, SourceChannel.WEB).readiness())
                    .isEqualTo(ReadinessStatus.UNRESOURCED);

            EventSetupTask awaiting = acceptedTask("S078-AWAITING");
            decompose(awaiting, venue());
            assertThat(tasks.readiness(awaiting.id(), harness.eventCoordinator, SourceChannel.WEB).readiness())
                    .isEqualTo(ReadinessStatus.AWAITING);

            EventSetupTask ready = acceptedTaskInRoom("S078-READY", "MEET-1");
            EventResourceRequest venueRequest = only(decompose(ready, venue()), EventResourceType.VENUE);
            harness.bookings().decide(new BookingCommands.DecideBooking(
                    UUID.fromString(venueRequest.externalReference()), true, null, null, harness.director,
                    SourceChannel.WEB));
            assertThat(tasks.readiness(ready.id(), harness.eventCoordinator, SourceChannel.WEB).readiness())
                    .isEqualTo(ReadinessStatus.READY);
        }

        @Test
        @DisplayName("the upcoming-events view lists readiness status, unresourced tasks and conflicts across events")
        void upcoming_events_view_lists_readiness_and_conflicts() {
            EventSetupTask unresourced = acceptedTask("S078-LIST-1");
            EventSetupTask conflicted = acceptedTask("S078-LIST-2");
            harness.bookings().request(new BookingCommands.RequestBooking(harness.meetingRoom.id(),
                    BookingPurpose.LECTURE, "Clash", null, START, END, null, null, 5, null, Map.of(), null, null,
                    harness.director, SourceChannel.WEB, null, null));
            handoffs.accept(confirmedHandoff("S078-LIST-2", "S078-LIST-2-k1", "MEET-1", 5, "MOOT", false, false),
                    harness.integration);
            decompose(repository.findTask(conflicted.id()).orElseThrow(), venue());

            List<EventReadinessView> upcoming = tasks.upcoming("MAIN", NOW, NOW.plus(Duration.ofDays(30)),
                    harness.eventCoordinator, SourceChannel.WEB);

            assertThat(upcoming).extracting(view -> view.task().s078EventReference())
                    .contains("S078-LIST-1", "S078-LIST-2");
            assertThat(upcoming.get(0).readiness().ordinal())
                    .isLessThanOrEqualTo(upcoming.get(upcoming.size() - 1).readiness().ordinal());
        }
    }

    // =============================================================================================
    // SRS-SFL-S173-03: Risk Assessment Attachment for Higher-Risk Events
    // =============================================================================================

    @Nested
    class RiskAssessment {

        @BeforeEach
        void configureRiskCriteria() {
            harness.configuration.set(EventLogisticsConfiguration.KEY_RISK_ATTENDANCE, "50");
        }

        @Test
        @DisplayName("a routine, low-risk event is exempt: confirmation succeeds with no linked assessment")
        void routine_event_is_exempt() {
            EventSetupTask task = acceptedTask("S078-ROUTINE");
            EventSetupTask confirmed = tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(),
                    harness.eventCoordinator, SourceChannel.WEB));
            assertThat(confirmed.status()).isEqualTo(EventSetupTaskStatus.CONFIRMED);
            assertThat(harness.audit.recorded(AuditAction.EVENT_SETUP_TASK_CONFIRMED)).isTrue();
        }

        @Test
        @DisplayName("a higher-risk event with no linked assessment is refused: EVENT_RISK_ASSESSMENT_NOT_CURRENT, audited")
        void higher_risk_event_without_a_linked_assessment_is_refused() {
            EventSetupTask task = higherRiskTask("S078-HR-NONE");

            assertThatThrownBy(() -> tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(),
                    harness.eventCoordinator, SourceChannel.WEB)))
                    .isInstanceOfSatisfying(FacilitiesException.class, e -> {
                        assertThat(e.code()).isEqualTo(FacilitiesErrorCode.EVENT_RISK_ASSESSMENT_NOT_CURRENT);
                        assertThat(e.getMessage()).contains("higher-risk").contains("No risk assessment is linked");
                    });

            assertThat(repository.findTask(task.id()).orElseThrow().status()).isEqualTo(EventSetupTaskStatus.OPEN);
            assertThat(harness.audit.recorded(AuditAction.EVENT_SETUP_CONFIRMATION_REFUSED)).isTrue();
        }

        @Test
        @DisplayName("fail-closed: the S165 projection is empty in this deployment, so every higher-risk event is refused")
        void empty_projection_refuses_every_higher_risk_event() {
            EventSetupTask task = higherRiskTask("S078-HR-EMPTY-PROJECTION");
            assertThat(repository.findRiskAssessmentVersions("ANY-ASSESSMENT")).isEmpty();
            assertThatThrownBy(() -> tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(),
                    harness.eventCoordinator, SourceChannel.WEB))).isInstanceOf(FacilitiesException.class);
        }

        @Test
        @DisplayName("a lapsed linked assessment refuses confirmation, naming the reason")
        void lapsed_assessment_refuses_confirmation() {
            EventSetupTask task = higherRiskTask("S078-HR-LAPSED");
            riskProjection.published("RA-1", 1, "MAIN", RiskAssessmentCurrency.RiskLevel.HIGH,
                    NOW.plus(Duration.ofDays(1)), "author", "reviewer", NOW);
            tasks.linkRiskAssessment(new EventLogisticsCommands.LinkRiskAssessment(task.id(), "RA-1", 1,
                    harness.eventCoordinator, SourceChannel.WEB));
            riskProjection.reviewLapsed("RA-1", 1, NOW.minus(Duration.ofDays(1)), NOW);

            assertThatThrownBy(() -> tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(),
                    harness.eventCoordinator, SourceChannel.WEB)))
                    .isInstanceOfSatisfying(FacilitiesException.class, e -> {
                        assertThat(e.code()).isEqualTo(FacilitiesErrorCode.EVENT_RISK_ASSESSMENT_NOT_CURRENT);
                        assertThat(e.getMessage()).containsIgnoringCase("lapsed");
                    });
        }

        @Test
        @DisplayName("a superseded linked assessment refuses confirmation, naming the reason")
        void superseded_assessment_refuses_confirmation() {
            EventSetupTask task = higherRiskTask("S078-HR-SUPERSEDED");
            riskProjection.published("RA-2", 1, "MAIN", RiskAssessmentCurrency.RiskLevel.MEDIUM,
                    NOW.plus(Duration.ofDays(30)), "author", "author", NOW);
            tasks.linkRiskAssessment(new EventLogisticsCommands.LinkRiskAssessment(task.id(), "RA-2", 1,
                    harness.eventCoordinator, SourceChannel.WEB));
            riskProjection.published("RA-2", 2, "MAIN", RiskAssessmentCurrency.RiskLevel.MEDIUM,
                    NOW.plus(Duration.ofDays(60)), "author", "author", NOW);

            assertThatThrownBy(() -> tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(),
                    harness.eventCoordinator, SourceChannel.WEB)))
                    .isInstanceOfSatisfying(FacilitiesException.class, e -> {
                        assertThat(e.code()).isEqualTo(FacilitiesErrorCode.EVENT_RISK_ASSESSMENT_NOT_CURRENT);
                        assertThat(e.getMessage()).containsIgnoringCase("supersed");
                    });
        }

        @Test
        @DisplayName("a higher-risk assessment signed off only by its own author refuses confirmation")
        void self_signed_off_higher_risk_assessment_refuses_confirmation() {
            EventSetupTask task = higherRiskTask("S078-HR-SELF-SIGNED");
            riskProjection.published("RA-3", 1, "MAIN", RiskAssessmentCurrency.RiskLevel.CRITICAL,
                    NOW.plus(Duration.ofDays(30)), "hse.author", "hse.author", NOW);

            assertThatThrownBy(() -> tasks.linkRiskAssessment(new EventLogisticsCommands.LinkRiskAssessment(
                    task.id(), "RA-3", 1, harness.eventCoordinator, SourceChannel.WEB)))
                    .isInstanceOfSatisfying(FacilitiesException.class, e -> assertThat(e.code())
                            .isEqualTo(FacilitiesErrorCode.EVENT_RISK_ASSESSMENT_NOT_CURRENT));
        }

        @Test
        @DisplayName("a current, independently signed-off assessment lets a higher-risk event confirm")
        void current_assessment_allows_confirmation() {
            EventSetupTask task = higherRiskTask("S078-HR-CURRENT");
            riskProjection.published("RA-4", 1, "MAIN", RiskAssessmentCurrency.RiskLevel.HIGH,
                    NOW.plus(Duration.ofDays(30)), "hse.author", "hse.reviewer", NOW);

            EventSetupTask linked = tasks.linkRiskAssessment(new EventLogisticsCommands.LinkRiskAssessment(task.id(),
                    "RA-4", 1, harness.eventCoordinator, SourceChannel.WEB));
            assertThat(linked.riskAssessmentId()).isEqualTo("RA-4");
            assertThat(harness.audit.recorded(AuditAction.EVENT_RISK_ASSESSMENT_LINKED)).isTrue();

            EventSetupTask confirmed = tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(),
                    harness.eventCoordinator, SourceChannel.WEB));
            assertThat(confirmed.status()).isEqualTo(EventSetupTaskStatus.CONFIRMED);
        }

        @Test
        @DisplayName("each higher-risk trigger alone is enough: external contractors and temporary structures")
        void each_trigger_alone_is_sufficient() {
            harness.configuration.set(EventLogisticsConfiguration.KEY_RISK_ATTENDANCE, "100000");
            EventSetupTask contractorEvent = handoffs.accept(
                    confirmedHandoff("S078-CONTRACTOR", "S078-CONTRACTOR-k1", "HALL-A", 10, "MOOT", true, false),
                    harness.integration).task();
            EventSetupTask structureEvent = handoffs.accept(
                    confirmedHandoff("S078-STRUCTURE", "S078-STRUCTURE-k1", "HALL-A", 10, "MOOT", false, true),
                    harness.integration).task();

            assertThatThrownBy(() -> tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(contractorEvent.id(),
                    harness.eventCoordinator, SourceChannel.WEB))).isInstanceOf(FacilitiesException.class);
            assertThatThrownBy(() -> tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(structureEvent.id(),
                    harness.eventCoordinator, SourceChannel.WEB))).isInstanceOf(FacilitiesException.class);
        }

        @Test
        @DisplayName("a configured higher-risk category is enough, whatever the attendance")
        void configured_category_alone_is_sufficient() {
            harness.configuration.set(EventLogisticsConfiguration.KEY_RISK_ATTENDANCE, "100000");
            harness.configuration.set(EventLogisticsConfiguration.KEY_RISK_CATEGORIES, "GRADUATION");
            EventSetupTask task = handoffs.accept(
                    confirmedHandoff("S078-CATEGORY", "S078-CATEGORY-k1", "HALL-A", 10, "GRADUATION", false, false),
                    harness.integration).task();

            assertThatThrownBy(() -> tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(),
                    harness.eventCoordinator, SourceChannel.WEB))).isInstanceOf(FacilitiesException.class);
        }

        @Test
        @DisplayName("HSE configures the higher-risk criteria; only FACILITIES_EVENT_RISK_CATEGORY_MANAGE may change them")
        void hse_configures_risk_criteria() {
            var updated = riskCriteria.configure(new EventLogisticsCommands.ConfigureRiskCriteria("MAIN", 75, null,
                    null, Set.of("concert", "exhibition"), harness.hseManager, SourceChannel.WEB));

            assertThat(updated.attendanceThreshold()).isEqualTo(75);
            assertThat(updated.higherRiskCategories()).containsExactlyInAnyOrder("CONCERT", "EXHIBITION");
            assertThat(harness.audit.recorded(AuditAction.EVENT_RISK_CATEGORY_CONFIGURED)).isTrue();

            assertThatThrownBy(() -> riskCriteria.configure(new EventLogisticsCommands.ConfigureRiskCriteria("MAIN",
                    10, null, null, null, harness.eventCoordinator, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.class);
        }

        private EventSetupTask higherRiskTask(String reference) {
            return handoffs.accept(confirmedHandoff(reference, reference + "-k1", "HALL-A", 100, "MOOT", false,
                    false), harness.integration).task();
        }
    }

    // =============================================================================================
    // SRS-SFL-S173-04: Hand-Off Completeness and Post-Event Reconciliation
    // =============================================================================================

    @Nested
    class CompletenessAndReconciliation {

        @Test
        @DisplayName("a request still merely requested when the escalation window opens is escalated before the event")
        void unresolved_request_escalates_before_the_event() {
            harness.configuration.set(EventLogisticsConfiguration.KEY_ESCALATION_WINDOW, "PT48H");
            EventSetupTask task = acceptedTask("S078-ESCALATE");
            EventResourceRequest venueRequest = only(decompose(task, venue()), EventResourceType.VENUE);
            assertThat(venueRequest.status()).isEqualTo(ResourceRequestStatus.REQUESTED);

            harness.clock.set(START.minus(Duration.ofHours(47)));
            EventReadinessService.EscalationSweep sweep = readiness.sweepEscalations(harness.system);

            assertThat(sweep.escalated()).isEqualTo(1);
            assertThat(harness.clock.instant()).isBefore(task.details().startsAt());
            assertThat(harness.audit.recorded(AuditAction.EVENT_READINESS_ESCALATED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.event-readiness-escalated.v1")).isTrue();
            var escalation = repository.findEscalationsForTask(task.id());
            assertThat(escalation).singleElement().satisfies(e -> assertThat(e.beforeEventStart()).isTrue());
        }

        @Test
        @DisplayName("a confirmed request does not escalate")
        void confirmed_request_does_not_escalate() {
            harness.configuration.set(EventLogisticsConfiguration.KEY_ESCALATION_WINDOW, "PT48H");
            EventSetupTask task = acceptedTask("S078-NO-ESCALATE-CONFIRMED");
            EventResourceRequest venueRequest = only(decompose(task, venue()), EventResourceType.VENUE);
            harness.bookings().decide(new BookingCommands.DecideBooking(
                    UUID.fromString(venueRequest.externalReference()), true, null, null, harness.director,
                    SourceChannel.WEB));

            harness.clock.set(START.minus(Duration.ofHours(1)));
            EventReadinessService.EscalationSweep sweep = readiness.sweepEscalations(harness.system);

            assertThat(sweep.escalated()).isZero();
        }

        @Test
        @DisplayName("an accepted manual-coordination item does not escalate")
        void accepted_manual_coordination_does_not_escalate() {
            harness.configuration.set(EventLogisticsConfiguration.KEY_ESCALATION_WINDOW, "PT48H");
            EventSetupTask task = acceptedTask("S078-NO-ESCALATE-MANUAL");
            EventResourceRequest catering = only(decompose(task,
                    new EventLogisticsCommands.NewResourceRequest(EventResourceType.CATERING, "Reception", 1, null,
                            null, null)), EventResourceType.CATERING);
            requests.acceptManualCoordination(new EventLogisticsCommands.AcceptManualCoordination(catering.id(),
                    "Campus Kitchens", harness.eventCoordinator, SourceChannel.WEB));

            harness.clock.set(START.minus(Duration.ofHours(1)));
            EventReadinessService.EscalationSweep sweep = readiness.sweepEscalations(harness.system);

            assertThat(sweep.escalated()).isZero();
        }

        @Test
        @DisplayName("a set-up task cannot be marked complete with an unresolved requested request and no escalation record")
        void cannot_complete_with_unresolved_unescalated_request() {
            EventSetupTask task = acceptedTask("S078-INCOMPLETE");
            decompose(task, venue());
            tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(), harness.eventCoordinator,
                    SourceChannel.WEB));

            assertThatThrownBy(() -> tasks.complete(new EventLogisticsCommands.CompleteSetupTask(task.id(), null,
                    harness.eventCoordinator, SourceChannel.WEB)))
                    .isInstanceOfSatisfying(FacilitiesException.class, e -> assertThat(e.code())
                            .isEqualTo(FacilitiesErrorCode.EVENT_UNRESOLVED_RESOURCE_REQUEST));
        }

        @Test
        @DisplayName("post-event reconciliation records delivered/partial/not-delivered per line, with notes")
        void reconciliation_records_actual_delivery_per_line() {
            EventSetupTask task = confirmedReadyTask("S078-RECONCILE");
            EventResourceRequest venueRequest = only(repository.findRequestsForTask(task.id()),
                    EventResourceType.VENUE);
            harness.clock.set(END.plus(Duration.ofMinutes(1)));

            List<gh.edu.clet.sfl.facilities.eventlogistics.domain.EventReconciliationLine> lines =
                    readiness.reconcile(new EventLogisticsCommands.RecordReconciliation(task.id(), List.of(
                            new EventLogisticsCommands.ReconciliationEntry(venueRequest.id(),
                                    DeliveryOutcome.DELIVERED, 1, "Hall ready on time")),
                            harness.eventCoordinator, SourceChannel.WEB));

            assertThat(lines).singleElement().satisfies(line -> {
                assertThat(line.outcome()).isEqualTo(DeliveryOutcome.DELIVERED);
                assertThat(line.notes()).isEqualTo("Hall ready on time");
            });
            assertThat(harness.audit.recorded(AuditAction.EVENT_RECONCILIATION_RECORDED)).isTrue();
        }

        @Test
        @DisplayName("a persistent reconciliation gap feeds a per-category template line, tested through the feedback loop")
        void persistent_gap_feeds_the_template_and_pre_populates_the_next_decomposition() {
            harness.configuration.set(EventLogisticsConfiguration.KEY_TEMPLATE_GAP_THRESHOLD, "2");

            for (int i = 0; i < 2; i++) {
                String reference = "S078-GAP-" + i;
                EventSetupTask task = confirmedReadyTask(reference, i == 0 ? "HALL-A" : "MEET-1");
                EventResourceRequest av = decomposeAvOnly(task);
                harness.clock.set(END.plus(Duration.ofMinutes(1)));
                readiness.reconcile(new EventLogisticsCommands.RecordReconciliation(task.id(), List.of(
                        new EventLogisticsCommands.ReconciliationEntry(av.id(), DeliveryOutcome.NOT_DELIVERED, 0,
                                "Projector never arrived")), harness.eventCoordinator, SourceChannel.WEB));
                harness.clock.set(NOW);
            }

            var templates = readiness.templates("MAIN", "MOOT", harness.eventCoordinator, SourceChannel.WEB);
            assertThat(templates).anySatisfy(line -> {
                assertThat(line.resourceType()).isEqualTo(EventResourceType.AV);
                assertThat(line.gapCount()).isGreaterThanOrEqualTo(2);
                assertThat(line.isPersistent(2)).isTrue();
            });
            assertThat(harness.audit.recorded(AuditAction.EVENT_TEMPLATE_GAP_RECORDED)).isTrue();

            // The feedback loop: a later decomposition of the same category picks the lesson up automatically.
            EventSetupTask nextEvent = acceptedTask("S078-GAP-NEXT");
            List<EventResourceRequest> decomposed = requests.decompose(new EventLogisticsCommands.DecomposeSetupTask(
                    nextEvent.id(), List.of(new EventLogisticsCommands.NewResourceRequest(EventResourceType.VENUE,
                            "Main hall", 1, null, null, null)), true, harness.eventCoordinator, SourceChannel.WEB));
            assertThat(decomposed).extracting(EventResourceRequest::resourceType).contains(EventResourceType.AV);
            assertThat(decomposed.stream().filter(r -> r.resourceType() == EventResourceType.AV).findFirst()
                    .orElseThrow().description()).contains("lesson");
        }

        private EventSetupTask confirmedReadyTask(String reference) {
            return confirmedReadyTask(reference, "HALL-A");
        }

        /** A distinct room per event avoids two independent test events clashing for the same S159 slot. */
        private EventSetupTask confirmedReadyTask(String reference, String roomCode) {
            EventSetupTask task = handoffs.accept(confirmedHandoff(reference, reference + "-k1", roomCode, 40,
                    "MOOT", false, false), harness.integration).task();
            EventResourceRequest venueRequest = only(decompose(task, venue()), EventResourceType.VENUE);
            harness.bookings().decide(new BookingCommands.DecideBooking(
                    UUID.fromString(venueRequest.externalReference()), true, null, null, harness.director,
                    SourceChannel.WEB));
            return tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(), harness.eventCoordinator,
                    SourceChannel.WEB));
        }

        private EventResourceRequest decomposeAvOnly(EventSetupTask task) {
            UUID resourceId = registerResource(EventResourceType.AV);
            List<EventResourceRequest> created = requests.decompose(new EventLogisticsCommands.DecomposeSetupTask(
                    task.id(), List.of(new EventLogisticsCommands.NewResourceRequest(EventResourceType.AV,
                            "Projector", 1, resourceId, null, null)), false, harness.eventCoordinator,
                    SourceChannel.WEB));
            return only(created, EventResourceType.AV);
        }
    }

    // =============================================================================================
    // Refusals: wrong role, other site, per-record narrowing
    // =============================================================================================

    @Nested
    class Refusals {

        @Test
        @DisplayName("only an integration principal may ingest a hand-off")
        void only_integration_may_ingest() {
            assertThatThrownBy(() -> handoffs.accept(confirmedHandoff("S078-NOPERM", "k1", "HALL-A", 10, "MOOT",
                    false, false), harness.requester)).isInstanceOf(FacilitiesException.class);
            assertThat(repository.findTaskByS078Reference("S078-NOPERM")).isEmpty();
        }

        @Test
        @DisplayName("only a holder of FACILITIES_EVENT_COORDINATE may decompose or confirm")
        void only_coordinator_may_act() {
            EventSetupTask task = acceptedTask("S078-NOCOORD");
            assertThatThrownBy(() -> requests.decompose(new EventLogisticsCommands.DecomposeSetupTask(task.id(),
                    List.of(new EventLogisticsCommands.NewResourceRequest(EventResourceType.VENUE, "hall", 1, null,
                            null, null)), false, harness.requester, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.class);
            assertThatThrownBy(() -> tasks.confirm(new EventLogisticsCommands.ConfirmSetupTask(task.id(),
                    harness.requester, SourceChannel.WEB))).isInstanceOf(FacilitiesException.class);
        }

        @Test
        @DisplayName("an actor scoped to another site cannot read or act on this site's event")
        void other_site_actor_is_refused() {
            EventSetupTask task = acceptedTask("S078-KSI-SCOPE");
            assertThatThrownBy(() -> tasks.findById(task.id(), harness.kumasiManager, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.class);
        }

        @Test
        @DisplayName("the upcoming view for one site never returns another site's events")
        void upcoming_view_is_narrowed_by_site() {
            acceptedTask("S078-MAIN-ONLY");
            List<EventReadinessView> mainView = tasks.upcoming("MAIN", NOW, NOW.plus(Duration.ofDays(30)),
                    harness.manager, SourceChannel.WEB);
            List<EventReadinessView> kumasiView = tasks.upcoming("KSI", NOW, NOW.plus(Duration.ofDays(30)),
                    harness.kumasiManager, SourceChannel.WEB);
            assertThat(mainView).isNotEmpty();
            assertThat(kumasiView).extracting(view -> view.task().s078EventReference())
                    .doesNotContain("S078-MAIN-ONLY");
        }
    }

    // ---- shared helpers -----------------------------------------------------------------------

    private List<EventResourceRequest> decompose(EventSetupTask task,
            EventLogisticsCommands.NewResourceRequest... lines) {
        return requests.decompose(new EventLogisticsCommands.DecomposeSetupTask(task.id(), List.of(lines), false,
                harness.eventCoordinator, SourceChannel.WEB));
    }

    private static EventLogisticsCommands.NewResourceRequest venue() {
        return new EventLogisticsCommands.NewResourceRequest(EventResourceType.VENUE, "Main hall", 1, null, null,
                null);
    }

    private static EventResourceRequest only(List<EventResourceRequest> requests, EventResourceType type) {
        return requests.stream().filter(request -> request.resourceType() == type).findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " request found"));
    }

    private UUID registerResource(EventResourceType type) {
        BookableResourceService resourceService = new BookableResourceService(harness.bookingStore,
                harness.bookings(), harness.authorization, harness.audit, harness.idempotency, harness.clock);
        var category = switch (type) {
            case AV -> gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory.AUDIO_VISUAL;
            case STAGING, SIGNAGE -> gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory.FURNITURE_SET;
            case SECURITY -> gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory.OTHER;
            default -> gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory.OTHER;
        };
        return resourceService.register(new BookingCommands.RegisterResource("MAIN", type.name() + "-" + UUID
                .randomUUID().toString().substring(0, 8), type.name() + " kit", category, null, 10, null, null,
                false, harness.director, SourceChannel.WEB, null, null)).id();
    }

    // ---- vendor test doubles --------------------------------------------------------------------

    private static final class FakeVendorSource implements VendorSourcePort {
        @Override
        public Optional<VendorSource> find(String sourceId) {
            return "CCP-EVENTS-SIM".equals(sourceId)
                    ? Optional.of(new VendorSource("CCP-EVENTS-SIM", VendorChannel.CCP_EVENTS, SECRET, Set.of("MAIN")))
                    : Optional.empty();
        }
    }

    private static final class FakeVendorInbox implements VendorInboxPort {
        final Set<String> accepted = new HashSet<>();
        final List<VendorRejectionReason> rejections = new ArrayList<>();

        @Override
        public boolean alreadyAccepted(String sourceId, String idempotencyKey) {
            return accepted.contains(sourceId + "|" + idempotencyKey);
        }

        @Override
        public UUID recordAccepted(InboxEntry entry) {
            accepted.add(entry.sourceId() + "|" + entry.idempotencyKey());
            return UUID.randomUUID();
        }

        @Override
        public UUID recordDuplicate(InboxEntry entry) {
            return UUID.randomUUID();
        }

        @Override
        public UUID recordRejected(InboxEntry entry, VendorRejectionReason reason, String detail) {
            rejections.add(reason);
            return UUID.randomUUID();
        }
    }

    private static final class FakeCleaningCapacity implements CleaningCapacityPort {
        GatewayOutcome nextOutcome = GatewayOutcome.unavailable("not configured in this test");
        final Map<String, GatewayOutcome> byReservation = new java.util.HashMap<>();
        final List<String> released = new ArrayList<>();

        @Override
        public GatewayOutcome reserve(CleaningSlot slot) {
            GatewayOutcome outcome = nextOutcome;
            if (outcome.reference() != null) {
                byReservation.put(outcome.reference(), outcome);
            }
            return outcome;
        }

        @Override
        public Optional<GatewayOutcome> state(String reservationId) {
            return Optional.ofNullable(byReservation.get(reservationId));
        }

        @Override
        public void release(String reservationId, String reason) {
            released.add(reservationId);
        }
    }
}
