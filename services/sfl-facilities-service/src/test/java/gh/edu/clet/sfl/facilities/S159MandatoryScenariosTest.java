package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.booking.application.BookableResourceService;
import gh.edu.clet.sfl.facilities.booking.application.BookingApplicationService;
import gh.edu.clet.sfl.facilities.booking.application.BookingAvailabilityService;
import gh.edu.clet.sfl.facilities.booking.application.BookingCommands;
import gh.edu.clet.sfl.facilities.booking.application.BookingConfiguration;
import gh.edu.clet.sfl.facilities.booking.application.BookingReconciliationService;
import gh.edu.clet.sfl.facilities.booking.application.BookingSetupService;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.NoShowRecord;
import gh.edu.clet.sfl.facilities.booking.domain.ReadinessHoldReason;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTask;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTaskStatus;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesMasterDataService;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.support.InMemoryBookingRepository;
import gh.edu.clet.sfl.facilities.support.InMemoryFacilitiesRepository;
import gh.edu.clet.sfl.facilities.support.RecordingAuditPort;
import gh.edu.clet.sfl.facilities.support.TestDoubles;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The S159 acceptance criteria, end to end through the application services.
 *
 * <p>One nested class per requirement, one test per criterion. They run against in-memory adapters so
 * a failure points at a rule rather than at a mapping.
 *
 * <p>The clock is mutable, because half of S159 is about time passing: a no-show is a statement about
 * a moment that has arrived, and a fixed clock cannot express "twenty-five minutes later the sweep
 * runs".
 *
 * <h2>What these tests cannot cover, and what covers it</h2>
 *
 * The double-booking guarantee is the {@code GIST} exclusion constraint in V10, and no single-threaded
 * test can exercise it — the race it exists to catch needs two transactions. So the tests here prove
 * the readable refusal, and {@link DatabaseAgreesWithTheDomain} pins the constraint's status list
 * against {@link BookingStatus#holdsTheSpace()} so the two expressions of the rule cannot drift while
 * both look right in isolation.
 */
class S159MandatoryScenariosTest {

    private static final Instant NOW = Instant.parse("2026-07-31T08:00:00Z");
    /** Monday morning, three days out. Every booking below hangs off these. */
    private static final Instant NINE = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant TEN = Instant.parse("2026-08-03T10:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-08-03T11:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-08-03T12:00:00Z");
    private static final Instant ONE = Instant.parse("2026-08-03T13:00:00Z");

    private MutableClock clock;
    private InMemoryFacilitiesRepository facilities;
    private InMemoryBookingRepository store;
    private RecordingAuditPort audit;
    private TestDoubles.RecordingOutbox outbox;
    private TestDoubles.InMemoryConfiguration configuration;

    private FacilitiesMasterDataService estate;
    private BookingApplicationService bookings;
    private BookableResourceService resources;
    private BookingAvailabilityService availability;
    private BookingSetupService setup;
    private BookingReconciliationService reconciliation;

    private ActorContext manager;
    private ActorContext centreManager;
    private ActorContext requester;
    private ActorContext otherRequester;
    private ActorContext technician;
    private ActorContext system;

    private FacilityRoom hall;
    private FacilityRoom meetingRoom;
    private FacilityRoom seminarRoom;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        facilities = new InMemoryFacilitiesRepository();
        store = new InMemoryBookingRepository();
        audit = new RecordingAuditPort(NOW);
        outbox = new TestDoubles.RecordingOutbox();
        configuration = new TestDoubles.InMemoryConfiguration();
        TestDoubles.InMemoryIdempotency idempotency = new TestDoubles.InMemoryIdempotency();
        FacilitiesAuthorization authorization = new FacilitiesAuthorization(audit);
        BookingConfiguration bookingConfiguration = new BookingConfiguration(configuration);

        estate = new FacilitiesMasterDataService(facilities, outbox, audit, idempotency, authorization,
                clock);
        bookings = new BookingApplicationService(store, facilities, bookingConfiguration, authorization,
                audit, idempotency, outbox, clock);
        resources = new BookableResourceService(store, bookings, authorization, audit, idempotency, clock);
        availability = new BookingAvailabilityService(store, facilities, authorization);
        setup = new BookingSetupService(store, bookings, authorization, audit, clock);
        reconciliation = new BookingReconciliationService(store, facilities, bookings, bookingConfiguration,
                audit, outbox, clock);

        manager = TestDoubles.actor("manager", Set.of(SflRole.FACILITIES_MANAGER), "MAIN");
        centreManager = TestDoubles.actor("centre.manager", Set.of(SflRole.CENTRE_MANAGER), "MAIN");
        requester = TestDoubles.actor("lecturer", Set.of(SflRole.IFIMP_REQUESTER), "MAIN");
        otherRequester = TestDoubles.actor("other.lecturer", Set.of(SflRole.IFIMP_REQUESTER), "MAIN");
        technician = TestDoubles.actor("technician", Set.of(SflRole.IFIMP_TECHNICIAN), "MAIN");
        system = TestDoubles.actor("system", Set.of(SflRole.SFL_ADMIN), "*", "MAIN");

        Site site = estate.createSite(new FacilitiesCommands.CreateSite("MAIN", "CLET Headquarters", null,
                manager, SourceChannel.WEB, null));
        Building building = estate.createBuilding(new FacilitiesCommands.CreateBuilding(site.id(), "LAW",
                "Law Block", null, manager, SourceChannel.WEB, null));
        FacilityFloor floor = estate.createFloor(new FacilitiesCommands.CreateFloor(building.id(), "GF",
                "Ground floor", 0, manager, SourceChannel.WEB, null));

        hall = estate.createRoom(new FacilitiesCommands.CreateRoom(floor.id(), "HALL-A",
                "Examination Hall A", SpaceType.EXAMINATION_HALL, 200, null, null, true, true, manager,
                SourceChannel.WEB, null));
        meetingRoom = estate.createRoom(new FacilitiesCommands.CreateRoom(floor.id(), "MR-1",
                "Meeting Room 1", SpaceType.MEETING_ROOM, 12, null, null, true, false, manager,
                SourceChannel.WEB, null));
        seminarRoom = estate.createRoom(new FacilitiesCommands.CreateRoom(floor.id(), "SEM-1",
                "Seminar Room 1", SpaceType.LECTURE_HALL, 60, null, null, true, true, manager,
                SourceChannel.WEB, null));

        // An examination hall that has been assessed. Without this every EXAMINATION booking below
        // would be refused for readiness rather than exercising the rule under test.
        hall = readiness(hall, LocationReadinessStatus.READY);
    }

    // =============================================================================================

    @Nested
    @DisplayName("S159-01. Maintain booking operational records")
    class OperationalRecords {

        @Test
        void a_booking_carries_a_unique_reference_provenance_and_an_audit_trail() {
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN);

            assertThat(booking.bookingReference()).isEqualTo("BK-MAIN-000001");
            assertThat(booking.siteCode()).isEqualTo("MAIN");
            assertThat(booking.roomCode()).isEqualTo("HALL-A");
            assertThat(booking.requestedBy()).isEqualTo("lecturer");
            assertThat(booking.metadata().createdBy()).isEqualTo("lecturer");
            assertThat(booking.metadata().sourceChannel()).isEqualTo(SourceChannel.WEB);
            assertThat(audit.actions()).contains(AuditAction.BOOKING_REQUESTED, AuditAction.BOOKING_CONFIRMED);
            assertThat(outbox.published("sfl.ifimp.booking-requested.v1")).isTrue();
        }

        @Test
        void booking_references_do_not_repeat() {
            assertThat(List.of(
                    book(requester, hall, BookingPurpose.LECTURE, NINE, TEN).bookingReference(),
                    book(requester, meetingRoom, BookingPurpose.MEETING, NINE, TEN).bookingReference(),
                    book(requester, seminarRoom, BookingPurpose.LECTURE, NINE, TEN).bookingReference()))
                    .containsExactly("BK-MAIN-000001", "BK-MAIN-000002", "BK-MAIN-000003");
        }

        @Test
        void a_bookable_resource_takes_a_code_that_is_unique_at_its_site() {
            registerResource("PROJ-01", 1, false);

            assertThatThrownBy(() -> registerResource("PROJ-01", 1, false))
                    .isInstanceOf(FacilitiesException.DuplicateIdentifierException.class);
        }

        /** One row for a set of forty chairs, not forty rows — and one of a thing makes it exclusive. */
        @Test
        void a_resource_there_is_only_one_of_is_exclusive() {
            assertThat(registerResource("PROJ-01", 1, false).isExclusive()).isTrue();
            assertThat(registerResource("CHAIRS", 40, true).isExclusive()).isFalse();
        }

        @Test
        void a_booking_cannot_be_made_for_a_window_that_has_already_finished() {
            assertThatThrownBy(() -> book(requester, hall, BookingPurpose.LECTURE,
                    NOW.minus(Duration.ofHours(3)), NOW.minus(Duration.ofHours(2))))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("already finished");
        }

        @Test
        void a_booking_beyond_the_horizon_is_a_data_entry_error() {
            Instant tooFar = NOW.plus(Duration.ofDays(400));

            assertThatThrownBy(() -> book(requester, hall, BookingPurpose.LECTURE, tooFar,
                    tooFar.plus(Duration.ofHours(1))))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("365 days ahead");
        }
    }

    // =============================================================================================

    @Nested
    @DisplayName("S159-02. Booking workflow, conflicts and approval")
    class Workflow {

        @Test
        void a_second_booking_of_the_same_hall_is_refused_and_names_the_first() {
            Booking first = book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);

            assertThatThrownBy(() -> book(otherRequester, hall, BookingPurpose.LECTURE, TEN, TWELVE))
                    .isInstanceOf(FacilitiesException.BookingConflictException.class)
                    .hasMessageContaining("HALL-A")
                    .hasMessageContaining(first.bookingReference());
        }

        /**
         * The boundary the whole module turns on. A lecture ending at ten and one starting at ten do
         * not clash, and getting this wrong makes every back-to-back timetable unusable.
         */
        @Test
        void a_back_to_back_booking_is_accepted() {
            book(requester, hall, BookingPurpose.LECTURE, NINE, TEN);

            assertThat(book(otherRequester, hall, BookingPurpose.LECTURE, TEN, ELEVEN).status())
                    .isEqualTo(BookingStatus.CONFIRMED);
        }

        /** Setup and teardown are part of the occupancy, which is what stops the overlap nobody sees. */
        @Test
        void an_examination_holds_the_hall_either_side_for_the_layout_change() {
            Booking examination = requestAndApprove(BookingPurpose.EXAMINATION, TEN, TWELVE);

            assertThat(examination.window().occupied().start())
                    .isEqualTo(Instant.parse("2026-08-03T09:30:00Z"));
            assertThat(examination.window().occupied().end())
                    .isEqualTo(Instant.parse("2026-08-03T12:30:00Z"));
            assertThatThrownBy(() -> book(otherRequester, hall, BookingPurpose.LECTURE, NINE, TEN))
                    .isInstanceOf(FacilitiesException.BookingConflictException.class);
        }

        /**
         * A request holds the space before anybody approves it. The alternative — letting everyone
         * request and resolving clashes at approval — has three people planning around one hall and
         * hands the approver a conflict to arbitrate rather than a decision to make.
         */
        @Test
        void a_request_holds_the_space_before_it_is_approved() {
            Booking examination = book(requester, hall, BookingPurpose.EXAMINATION, TEN, TWELVE);

            assertThat(examination.status()).isEqualTo(BookingStatus.REQUESTED);
            assertThat(examination.approvalRequired()).isTrue();
            assertThatThrownBy(() -> book(otherRequester, hall, BookingPurpose.LECTURE, TEN, ELEVEN))
                    .isInstanceOf(FacilitiesException.BookingConflictException.class);
        }

        @Test
        void a_booking_needing_no_approval_is_confirmed_at_once_and_records_no_approval() {
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN);

            assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.approvalRequired()).isFalse();
            assertThat(booking.approvalId()).isNull();
            assertThat(bookings.approvals(booking.id(), manager, SourceChannel.WEB)).isEmpty();
        }

        @Test
        void a_long_booking_needs_approving_whatever_its_purpose() {
            Booking booking = book(requester, hall, BookingPurpose.MEETING, NINE,
                    NINE.plus(Duration.ofHours(9)));

            assertThat(booking.status()).isEqualTo(BookingStatus.REQUESTED);
        }

        @Test
        void approving_confirms_the_booking_and_records_who_decided() {
            Booking approved = requestAndApprove(BookingPurpose.EXAMINATION, TEN, TWELVE);

            assertThat(approved.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(approved.approvalId()).isNotNull();
            assertThat(bookings.approvals(approved.id(), manager, SourceChannel.WEB))
                    .singleElement()
                    .satisfies(approval -> assertThat(approval.decidedBy()).isEqualTo("manager"));
        }

        /** Separation of duties, and administrators are not exempt. */
        @Test
        void an_approver_may_not_decide_their_own_request() {
            Booking mine = book(manager, hall, BookingPurpose.EXAMINATION, TEN, TWELVE);

            assertThatThrownBy(() -> decide(manager, mine, true, null))
                    .isInstanceOf(FacilitiesException.UnauthorizedApprovalException.class);
            assertThat(audit.recorded(AuditAction.AUTHORIZATION_DENIED)).isTrue();
        }

        @Test
        void a_rejection_must_say_why() {
            Booking requested = book(requester, hall, BookingPurpose.EXAMINATION, TEN, TWELVE);

            assertThatThrownBy(() -> decide(manager, requested, false, null))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class);
        }

        @Test
        void rejecting_releases_the_space() {
            Booking requested = book(requester, hall, BookingPurpose.EXAMINATION, TEN, TWELVE);
            decide(manager, requested, false, "The hall is needed for a resit");

            assertThat(book(otherRequester, hall, BookingPurpose.LECTURE, TEN, ELEVEN).status())
                    .isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void only_a_requested_booking_can_be_decided_on() {
            Booking confirmed = book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN);

            assertThatThrownBy(() -> decide(manager, confirmed, true, null))
                    .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class);
        }

        @Test
        void moving_a_booking_moves_the_resources_it_holds() {
            BookableResource projector = registerResource("PROJ-01", 1, false);
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, NINE, TEN,
                    Map.of(projector.id(), 1));

            bookings.reschedule(new BookingCommands.RescheduleBooking(booking.id(), TWELVE, ONE, null, null,
                    null, null, requester, SourceChannel.WEB));

            assertThat(store.findAllocationsForBooking(booking.id()))
                    .singleElement()
                    .satisfies(allocation -> {
                        assertThat(allocation.window().start()).isEqualTo(TWELVE);
                        assertThat(allocation.window().end()).isEqualTo(ONE);
                    });
        }

        @Test
        void a_booking_in_use_cannot_be_moved() {
            Booking booking = start(book(requester, hall, BookingPurpose.LECTURE, NINE, TEN));

            assertThatThrownBy(() -> bookings.reschedule(new BookingCommands.RescheduleBooking(booking.id(),
                    TWELVE, ONE, null, null, null, null, requester, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class)
                    .hasMessageContaining("Complete it and raise a new one");
        }

        @Test
        void a_move_into_a_taken_window_is_refused_and_leaves_the_booking_where_it_was() {
            book(otherRequester, hall, BookingPurpose.LECTURE, NINE, TEN);
            Booking mine = book(requester, hall, BookingPurpose.LECTURE, ELEVEN, TWELVE);

            assertThatThrownBy(() -> bookings.reschedule(new BookingCommands.RescheduleBooking(mine.id(),
                    NINE, TEN, null, null, null, null, requester, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.BookingConflictException.class);
            assertThat(store.findBooking(mine.id()).orElseThrow().window().start()).isEqualTo(ELEVEN);
        }

        @Test
        void a_booking_can_be_moved_within_its_own_window_without_clashing_with_itself() {
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);

            assertThat(bookings.reschedule(new BookingCommands.RescheduleBooking(booking.id(), TEN, TWELVE,
                    null, null, null, null, requester, SourceChannel.WEB)).window().start())
                    .isEqualTo(TEN);
        }

        @Test
        void cancelling_requires_a_reason_and_releases_the_space() {
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);

            bookings.cancel(new BookingCommands.CancelBooking(booking.id(), "Lecturer unwell", null,
                    requester, SourceChannel.WEB));

            assertThat(book(otherRequester, hall, BookingPurpose.LECTURE, NINE, ELEVEN).status())
                    .isEqualTo(BookingStatus.CONFIRMED);
            assertThat(audit.recorded(AuditAction.BOOKING_CANCELLED)).isTrue();
        }
    }

    // =============================================================================================

    @Nested
    @DisplayName("S159-02. Resources and room turnaround")
    class ResourcesAndSetup {

        @Test
        void an_exclusive_resource_cannot_be_in_two_places_at_once() {
            BookableResource projector = registerResource("PROJ-01", 1, false);
            book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN, Map.of(projector.id(), 1));

            assertThatThrownBy(() -> book(otherRequester, meetingRoom, BookingPurpose.MEETING, TEN, TWELVE,
                    Map.of(projector.id(), 1)))
                    .isInstanceOf(FacilitiesException.ResourceUnavailableException.class)
                    .hasMessageContaining("PROJ-01");
        }

        @Test
        void a_pool_is_shared_until_it_runs_out_and_then_reports_the_shortfall() {
            BookableResource chairs = registerResource("CHAIRS", 40, false);
            book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN, Map.of(chairs.id(), 25));
            book(otherRequester, meetingRoom, BookingPurpose.MEETING, NINE, ELEVEN, Map.of(chairs.id(), 15));

            assertThatThrownBy(() -> book(requester, seminarRoom, BookingPurpose.LECTURE, NINE, ELEVEN,
                    Map.of(chairs.id(), 1)))
                    .isInstanceOf(FacilitiesException.ResourceUnavailableException.class)
                    .hasMessageContaining("Only 0");
        }

        @Test
        void a_resource_registered_at_another_site_cannot_be_booked_here() {
            BookableResource elsewhere = resources.register(new BookingCommands.RegisterResource("KUMASI",
                    "PROJ-K1", "Kumasi projector", ResourceCategory.PROJECTOR, null, 1, null, null, false,
                    TestDoubles.actor("kumasi.manager", Set.of(SflRole.FACILITIES_MANAGER), "KUMASI"),
                    SourceChannel.WEB, null, null));

            assertThatThrownBy(() -> book(requester, hall, BookingPurpose.LECTURE, NINE, TEN,
                    Map.of(elsewhere.id(), 1)))
                    .isInstanceOf(FacilitiesException.ResourceUnavailableException.class)
                    .hasMessageContaining("KUMASI");
        }

        @Test
        void completing_a_booking_releases_everything_it_held() {
            BookableResource projector = registerResource("PROJ-01", 1, false);
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN,
                    Map.of(projector.id(), 1));
            complete(start(booking));

            assertThat(store.findAllocationsForBooking(booking.id()))
                    .allMatch(ResourceAllocation::releasedWithBooking);
            assertThat(book(otherRequester, meetingRoom, BookingPurpose.MEETING, NINE, ELEVEN,
                    Map.of(projector.id(), 1)).status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void a_resource_that_needs_putting_out_raises_a_turnaround_task() {
            BookableResource chairs = registerResource("CHAIRS", 40, true);
            BookableResource laptop = registerResource("LAPTOP", 12, false);
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN,
                    Map.of(chairs.id(), 40, laptop.id(), 2));

            assertThat(bookings.setupTasks(booking.id(), manager, SourceChannel.WEB))
                    .singleElement()
                    .satisfies(task -> {
                        assertThat(task.description()).contains("CHAIRS");
                        assertThat(task.status()).isEqualTo(SetupTaskStatus.PENDING);
                        // Due when the room must be ready, which is the start of the occupied window.
                        assertThat(task.dueBy()).isEqualTo(booking.window().occupied().start());
                    });
        }

        @Test
        void cancelling_a_booking_marks_its_outstanding_turnaround_work_as_deliberately_skipped() {
            BookableResource chairs = registerResource("CHAIRS", 40, true);
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN,
                    Map.of(chairs.id(), 40));

            bookings.cancel(new BookingCommands.CancelBooking(booking.id(), "Class cancelled", null,
                    requester, SourceChannel.WEB));

            assertThat(bookings.setupTasks(booking.id(), manager, SourceChannel.WEB))
                    .singleElement()
                    .satisfies(task -> {
                        assertThat(task.status()).isEqualTo(SetupTaskStatus.SKIPPED);
                        assertThat(task.notes()).contains("Class cancelled");
                    });
        }

        @Test
        void a_skipped_setup_task_must_say_why() {
            SetupTask task = raiseSetupTask();

            assertThatThrownBy(() -> setup.resolve(new BookingCommands.ResolveSetupTask(task.id(),
                    SetupTaskStatus.SKIPPED, null, technician, SourceChannel.MOBILE)))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("must say why");
        }

        @Test
        void a_technician_can_complete_turnaround_work() {
            SetupTask task = raiseSetupTask();

            SetupTask done = setup.resolve(new BookingCommands.ResolveSetupTask(task.id(),
                    SetupTaskStatus.DONE, "Chairs in examination layout", technician, SourceChannel.MOBILE));

            assertThat(done.status()).isEqualTo(SetupTaskStatus.DONE);
            assertThat(done.completedBy()).isEqualTo("technician");
            assertThat(audit.recorded(AuditAction.BOOKING_SETUP_TASK_RESOLVED)).isTrue();
        }

        /** Ordered by when the room is needed, not by when the task was raised. */
        @Test
        void the_turnaround_queue_is_ordered_by_when_the_room_is_needed() {
            BookableResource chairs = registerResource("CHAIRS", 40, true);
            book(requester, hall, BookingPurpose.LECTURE, TWELVE, ONE, Map.of(chairs.id(), 10));
            book(otherRequester, meetingRoom, BookingPurpose.MEETING, NINE, TEN, Map.of(chairs.id(), 10));

            assertThat(setup.queue("MAIN", ONE, 50, manager, SourceChannel.WEB))
                    .extracting(SetupTask::dueBy)
                    .containsExactly(NINE, TWELVE);
        }
    }

    // =============================================================================================

    @Nested
    @DisplayName("S159-02. Readiness, overrides and no-shows")
    class ReadinessAndNoShows {

        @Test
        void a_blocked_hall_refuses_a_booking_and_says_why() {
            readiness(hall, LocationReadinessStatus.BLOCKED);

            assertThatThrownBy(() -> book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN))
                    .isInstanceOf(FacilitiesException.SpaceNotBookableException.class)
                    .hasMessageContaining("HALL-A")
                    .hasMessageContaining("critical readiness blocker");
        }

        @Test
        void an_examination_needs_a_hall_assessed_ready_not_merely_usable() {
            readiness(hall, LocationReadinessStatus.DEGRADED);

            assertThatThrownBy(() -> book(requester, hall, BookingPurpose.EXAMINATION, TEN, TWELVE))
                    .isInstanceOf(FacilitiesException.SpaceNotBookableException.class)
                    .hasMessageContaining("not certified ready for examination use");
        }

        @Test
        void a_degraded_hall_still_takes_an_ordinary_booking() {
            readiness(hall, LocationReadinessStatus.DEGRADED);

            assertThat(book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN).status())
                    .isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void an_override_needs_the_permission_a_reason_and_leaves_an_audit_record() {
            readiness(hall, LocationReadinessStatus.BLOCKED);

            Booking overridden = bookings.request(new BookingCommands.RequestBooking(hall.id(),
                    BookingPurpose.LECTURE, "Resit briefing", null, TEN, ELEVEN, null, null, 40, null,
                    Map.of(), "The Dean has authorised it in writing", centreManager, SourceChannel.WEB,
                    null, null));

            assertThat(overridden.wasOverridden()).isTrue();
            assertThat(overridden.overrideReason()).contains("The Dean has authorised it");
            assertThat(audit.recorded(AuditAction.BOOKING_READINESS_OVERRIDDEN)).isTrue();
        }

        @Test
        void a_reason_without_the_permission_is_not_an_override() {
            readiness(hall, LocationReadinessStatus.BLOCKED);

            assertThatThrownBy(() -> bookings.request(new BookingCommands.RequestBooking(hall.id(),
                    BookingPurpose.LECTURE, "Resit briefing", null, TEN, ELEVEN, null, null, 40, null,
                    Map.of(), "I really need the hall", manager, SourceChannel.WEB, null, null)))
                    .isInstanceOf(FacilitiesException.SpaceNotBookableException.class);
            assertThat(audit.recorded(AuditAction.AUTHORIZATION_DENIED)).isTrue();
        }

        /**
         * The rule the readiness hold exists for. A hall blocked on Tuesday must not silently cancel
         * Friday's examination — somebody has it in their diary and is planning around it.
         */
        @Test
        void blocking_a_hall_flags_its_bookings_rather_than_cancelling_them() {
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN);
            readiness(hall, LocationReadinessStatus.BLOCKED);

            BookingReconciliationService.ReadinessSweep sweep = reconciliation.sweepReadinessHolds(system);

            assertThat(sweep.holdsPlaced()).isEqualTo(1);
            Booking held = store.findBooking(booking.id()).orElseThrow();
            assertThat(held.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(held.readinessHoldReason()).isEqualTo(ReadinessHoldReason.SPACE_BLOCKED);
            assertThat(audit.recorded(AuditAction.BOOKING_READINESS_HOLD_PLACED)).isTrue();
        }

        @Test
        void repairing_the_hall_clears_the_hold_without_anybody_remembering_to() {
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN);
            readiness(hall, LocationReadinessStatus.BLOCKED);
            reconciliation.sweepReadinessHolds(system);

            readiness(hall, LocationReadinessStatus.READY);
            BookingReconciliationService.ReadinessSweep sweep = reconciliation.sweepReadinessHolds(system);

            assertThat(sweep.holdsCleared()).isEqualTo(1);
            assertThat(store.findBooking(booking.id()).orElseThrow().readinessHoldReason()).isNull();
            assertThat(audit.recorded(AuditAction.BOOKING_READINESS_HOLD_CLEARED)).isTrue();
        }

        @Test
        void the_readiness_sweep_changes_nothing_the_second_time() {
            book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN);
            readiness(hall, LocationReadinessStatus.BLOCKED);
            reconciliation.sweepReadinessHolds(system);

            assertThat(reconciliation.sweepReadinessHolds(system).total()).isZero();
        }

        /**
         * A three-hour lecture nobody attended should not hold a hall for three hours. The sweep
         * releases it a configured grace period after the start, not at the end of the window.
         */
        @Test
        void a_booking_nobody_took_up_is_released_after_the_grace_period() {
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);
            clock.moveTo(NINE.plus(Duration.ofMinutes(25)));

            assertThat(reconciliation.sweepNoShows(system).recorded()).isEqualTo(1);
            assertThat(store.findBooking(booking.id()).orElseThrow().status())
                    .isEqualTo(BookingStatus.NO_SHOW);
            assertThat(audit.recorded(AuditAction.BOOKING_NO_SHOW_RECORDED)).isTrue();
        }

        @Test
        void a_booking_still_inside_its_grace_period_is_left_alone() {
            book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);
            clock.moveTo(NINE.plus(Duration.ofMinutes(15)));

            assertThat(reconciliation.sweepNoShows(system).recorded()).isZero();
        }

        @Test
        void a_booking_somebody_turned_up_for_is_never_a_no_show() {
            start(book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN));
            clock.moveTo(NINE.plus(Duration.ofHours(1)));

            assertThat(reconciliation.sweepNoShows(system).recorded()).isZero();
        }

        @Test
        void the_no_show_record_carries_the_room_time_the_booking_took_out_of_the_diary() {
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);
            clock.moveTo(NINE.plus(Duration.ofMinutes(25)));
            reconciliation.sweepNoShows(system);

            assertThat(store.findNoShows("MAIN", null, null, null, 10))
                    .singleElement()
                    .satisfies(record -> {
                        assertThat(record.bookingReference()).isEqualTo(booking.bookingReference());
                        assertThat(record.roomCode()).isEqualTo("HALL-A");
                        assertThat(record.requestedBy()).isEqualTo("lecturer");
                        assertThat(record.minutesHeldUnused()).isEqualTo(120);
                    });
        }

        @Test
        void a_no_show_releases_the_space_for_somebody_else() {
            book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);
            clock.moveTo(NINE.plus(Duration.ofMinutes(25)));
            reconciliation.sweepNoShows(system);

            assertThat(book(otherRequester, hall, BookingPurpose.LECTURE, NINE.plus(Duration.ofMinutes(30)),
                    ELEVEN).status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        void the_no_show_sweep_changes_nothing_the_second_time() {
            book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);
            clock.moveTo(NINE.plus(Duration.ofMinutes(25)));
            reconciliation.sweepNoShows(system);

            assertThat(reconciliation.sweepNoShows(system).recorded()).isZero();
        }

        @Test
        void a_site_can_lengthen_its_grace_period_without_a_redeploy() {
            configuration.set("booking.no-show.grace", "PT90M");
            book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);
            clock.moveTo(NINE.plus(Duration.ofMinutes(25)));

            assertThat(reconciliation.sweepNoShows(system).recorded()).isZero();
        }
    }

    // =============================================================================================

    @Nested
    @DisplayName("S159-02. Availability")
    class Availability {

        @Test
        void a_taken_hall_is_returned_with_the_booking_that_has_it_rather_than_hidden() {
            Booking booking = book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN);

            BookingAvailabilityService.SpaceAvailability taken = spaces(TEN, TWELVE).stream()
                    .filter(space -> space.room().id().equals(hall.id()))
                    .findFirst()
                    .orElseThrow();

            assertThat(taken.free()).isFalse();
            assertThat(taken.heldBy()).extracting(Booking::bookingReference)
                    .containsExactly(booking.bookingReference());
        }

        @Test
        void a_blocked_hall_comes_back_with_the_reason_and_is_flagged_as_overridable() {
            readiness(hall, LocationReadinessStatus.BLOCKED);

            BookingAvailabilityService.SpaceAvailability blocked = spaces(TEN, TWELVE).stream()
                    .filter(space -> space.room().id().equals(hall.id()))
                    .findFirst()
                    .orElseThrow();

            assertThat(blocked.free()).isFalse();
            assertThat(blocked.readinessIssue()).isEqualTo(ReadinessHoldReason.SPACE_BLOCKED);
            assertThat(blocked.availableWithOverride()).isTrue();
        }

        @Test
        void a_space_too_small_for_the_party_is_not_offered() {
            List<BookingAvailabilityService.SpaceAvailability> found = availability.spaces("MAIN",
                    BookingWindow.of(TEN, TWELVE), BookingPurpose.LECTURE, null, 100, manager,
                    SourceChannel.WEB);

            assertThat(found).extracting(space -> space.room().roomCode()).containsExactly("HALL-A");
        }

        @Test
        void resource_availability_reports_what_is_left_rather_than_only_what_is_taken() {
            BookableResource chairs = registerResource("CHAIRS", 40, false);
            book(requester, hall, BookingPurpose.LECTURE, NINE, ELEVEN, Map.of(chairs.id(), 25));

            assertThat(availability.resources("MAIN", BookingWindow.of(TEN, TWELVE), null, manager,
                    SourceChannel.WEB))
                    .singleElement()
                    .satisfies(entry -> {
                        assertThat(entry.committed()).isEqualTo(25);
                        assertThat(entry.free()).isEqualTo(15);
                    });
        }
    }

    // =============================================================================================

    @Nested
    @DisplayName("Authorisation")
    class Authorisation {

        @Test
        void a_requester_reads_only_the_bookings_they_requested() {
            Booking mine = book(requester, hall, BookingPurpose.LECTURE, NINE, TEN);
            book(otherRequester, meetingRoom, BookingPurpose.MEETING, NINE, TEN);

            assertThat(bookings.search(query("MAIN"), requester, SourceChannel.WEB))
                    .extracting(Booking::id)
                    .containsExactly(mine.id());
        }

        @Test
        void a_requester_asking_for_somebody_elses_booking_is_refused_and_the_denial_is_audited() {
            Booking theirs = book(otherRequester, hall, BookingPurpose.LECTURE, NINE, TEN);
            audit.clear();

            assertThatThrownBy(() -> bookings.findById(theirs.id(), requester, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
            assertThat(audit.recorded(AuditAction.AUTHORIZATION_DENIED)).isTrue();
        }

        @Test
        void a_facilities_manager_reads_the_whole_site_diary() {
            book(requester, hall, BookingPurpose.LECTURE, NINE, TEN);
            book(otherRequester, meetingRoom, BookingPurpose.MEETING, NINE, TEN);

            assertThat(bookings.search(query("MAIN"), manager, SourceChannel.WEB)).hasSize(2);
        }

        @Test
        void cancelling_your_own_booking_needs_no_special_permission() {
            Booking mine = book(requester, hall, BookingPurpose.LECTURE, NINE, TEN);

            assertThat(bookings.cancel(new BookingCommands.CancelBooking(mine.id(), "Class cancelled", null,
                    requester, SourceChannel.WEB)).status()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        void cancelling_somebody_elses_booking_needs_the_permission_to_do_so() {
            Booking theirs = book(otherRequester, hall, BookingPurpose.LECTURE, NINE, TEN);

            assertThatThrownBy(() -> bookings.cancel(new BookingCommands.CancelBooking(theirs.id(),
                    "I want the hall", null, requester, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
            assertThat(bookings.cancel(new BookingCommands.CancelBooking(theirs.id(), "Hall needed for a resit",
                    null, manager, SourceChannel.WEB)).status()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        void an_actor_sees_only_the_sites_they_hold() {
            book(requester, hall, BookingPurpose.LECTURE, NINE, TEN);
            ActorContext kumasi = TestDoubles.actor("kumasi.manager", Set.of(SflRole.FACILITIES_MANAGER),
                    "KUMASI");

            assertThat(bookings.search(query(null), kumasi, SourceChannel.WEB)).isEmpty();
            assertThat(bookings.search(query(null), manager, SourceChannel.WEB)).hasSize(1);
        }

        @Test
        void a_requester_cannot_register_a_bookable_resource() {
            assertThatThrownBy(() -> resources.register(new BookingCommands.RegisterResource("MAIN",
                    "PROJ-01", "Projector", ResourceCategory.PROJECTOR, null, 1, null, null, false,
                    requester, SourceChannel.WEB, null, null)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        void a_requester_cannot_approve_a_booking() {
            Booking requested = book(otherRequester, hall, BookingPurpose.EXAMINATION, TEN, TWELVE);

            assertThatThrownBy(() -> decide(requester, requested, true, null))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }

    // =============================================================================================

    @Nested
    @DisplayName("The database and the domain state one rule")
    class DatabaseAgreesWithTheDomain {

        /**
         * The exclusion constraint's {@code WHERE} clause and {@link BookingStatus#holdsTheSpace()} are
         * two expressions of one rule, in two languages, with no compiler between them. Adding a
         * holding state in Java and forgetting the migration would let that state be double-booked,
         * and every unit test would still pass.
         */
        @Test
        void the_exclusion_constraint_covers_exactly_the_statuses_that_hold_the_space() throws Exception {
            Matcher matcher = Pattern
                    .compile("ux_bookings_no_double_booking[\\s\\S]*?WHERE \\(status IN \\(([^)]*)\\)\\)")
                    .matcher(migration());
            assertThat(matcher.find()).as("the exclusion constraint is present in V10").isTrue();

            Set<String> inTheDatabase = Arrays.stream(matcher.group(1).split(","))
                    .map(value -> value.strip().replace("'", ""))
                    .collect(Collectors.toSet());
            Set<String> inTheDomain = Arrays.stream(BookingStatus.values())
                    .filter(BookingStatus::holdsTheSpace)
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            assertThat(inTheDatabase).isEqualTo(inTheDomain);
        }

        /** Half-open, written explicitly rather than left to a default somebody could change. */
        @Test
        void both_exclusion_constraints_use_a_half_open_range() throws Exception {
            assertThat(migration().split(Pattern.quote("tstzrange(occupied_from, occupied_to, '[)')"), -1))
                    .as("one half-open range per exclusion constraint")
                    .hasSize(3);
        }

        @Test
        void the_resource_constraint_only_covers_resources_there_is_one_of() throws Exception {
            assertThat(migration()).contains("WHERE (released_with_booking = FALSE AND is_exclusive = TRUE)");
        }

        private String migration() throws Exception {
            try (var stream = getClass()
                    .getResourceAsStream("/db/migration/V10__room_and_resource_booking.sql")) {
                assertThat(stream).as("V10 is on the classpath").isNotNull();
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    // =============================================================================================
    // Fixtures
    // =============================================================================================

    private Booking book(ActorContext actor, FacilityRoom room, BookingPurpose purpose, Instant from,
            Instant to) {
        return book(actor, room, purpose, from, to, Map.of());
    }

    private Booking book(ActorContext actor, FacilityRoom room, BookingPurpose purpose, Instant from,
            Instant to, Map<UUID, Integer> resourceRequest) {
        return bookings.request(new BookingCommands.RequestBooking(room.id(), purpose, "Contract law", null,
                from, to, null, null, 40, null, resourceRequest, null, actor, SourceChannel.WEB, null,
                null));
    }

    private Booking requestAndApprove(BookingPurpose purpose, Instant from, Instant to) {
        return decide(manager, book(requester, hall, purpose, from, to), true, null);
    }

    private Booking decide(ActorContext actor, Booking booking, boolean approve, String reason) {
        return bookings.decide(new BookingCommands.DecideBooking(booking.id(), approve, reason, null, actor,
                SourceChannel.WEB));
    }

    private Booking start(Booking booking) {
        return bookings.transition(new BookingCommands.TransitionBooking(booking.id(),
                BookingCommands.TransitionBooking.Transition.START, null, null, requester,
                SourceChannel.WEB));
    }

    private Booking complete(Booking booking) {
        return bookings.transition(new BookingCommands.TransitionBooking(booking.id(),
                BookingCommands.TransitionBooking.Transition.COMPLETE, "Ran to time", null, requester,
                SourceChannel.WEB));
    }

    private BookableResource registerResource(String code, int quantity, boolean requiresSetup) {
        return resources.register(new BookingCommands.RegisterResource("MAIN", code, code,
                ResourceCategory.OTHER, null, quantity, null, null, requiresSetup, manager,
                SourceChannel.WEB, null, null));
    }

    private SetupTask raiseSetupTask() {
        BookableResource chairs = registerResource("CHAIRS", 40, true);
        Booking booking = book(requester, hall, BookingPurpose.LECTURE, TEN, ELEVEN,
                Map.of(chairs.id(), 40));
        return bookings.setupTasks(booking.id(), manager, SourceChannel.WEB).get(0);
    }

    private List<BookingAvailabilityService.SpaceAvailability> spaces(Instant from, Instant to) {
        return availability.spaces("MAIN", BookingWindow.of(from, to), BookingPurpose.LECTURE, null, null,
                manager, SourceChannel.WEB);
    }

    private static BookingRepository.BookingQuery query(String siteCode) {
        return new BookingRepository.BookingQuery(siteCode, null, null, null, null, null, null, null, null,
                50);
    }

    /** Applies a readiness outcome directly, standing in for an assessment the readiness module made. */
    private FacilityRoom readiness(FacilityRoom room, LocationReadinessStatus status) {
        return facilities.saveRoom(facilities.findRoom(room.id()).orElseThrow()
                .applyReadiness(status, "Assessed", "assessor", clock.instant(), SourceChannel.MOBILE,
                        null));
    }

    /** See {@code S153MandatoryScenariosTest} — half of this module is about time passing. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void moveTo(Instant target) {
            instant = target;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
