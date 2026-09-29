package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.application.BookingCommands;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.CleaningRequirement;
import gh.edu.clet.sfl.facilities.cleaning.application.BookingCleaningService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningCapacityService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningCommands;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningConfiguration;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningDashboardService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningFeedbackService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningRefusalRecorder;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningScheduleService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningSupport;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningTaskService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningVendorService;
import gh.edu.clet.sfl.facilities.cleaning.application.SlaEvaluator;
import gh.edu.clet.sfl.facilities.cleaning.application.contract.EventCleaningCapacity;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.BookingDirectoryPort;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFrequency;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningSchedule;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.FlagSubjectType;
import gh.edu.clet.sfl.facilities.cleaning.domain.LowRatingFlag;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskChecklistItem;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorSlaTerms;
import gh.edu.clet.sfl.facilities.cleaning.infrastructure.integration.BookingCleaningObserver;
import gh.edu.clet.sfl.facilities.cleaning.infrastructure.integration.S159BookingDirectoryAdapter;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.support.IfimpTestHarness;
import gh.edu.clet.sfl.facilities.support.InMemoryCleaningRepository;
import gh.edu.clet.sfl.facilities.support.InMemoryCleaningVendorMasterPort;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The S169 acceptance criteria, end to end through the application services - one {@code @Nested} class
 * per requirement, one test per acceptance criterion, validation rule and error state, following the
 * convention {@code S159MandatoryScenariosTest} established.
 */
class S169MandatoryScenariosTest {

    private IfimpTestHarness harness;
    private InMemoryCleaningRepository repository;
    private InMemoryCleaningVendorMasterPort vendorMaster;
    private CleaningSupport support;
    private CleaningScheduleService schedules;
    private CleaningTaskService tasks;
    private CleaningFeedbackService feedback;
    private CleaningVendorService vendors;
    private CleaningCapacityService capacity;
    private CleaningDashboardService dashboard;
    private BookingCleaningService bookingCleaning;
    private TestBookingDirectory bookingDirectory;

    @BeforeEach
    void setUp() {
        harness = new IfimpTestHarness();
        repository = new InMemoryCleaningRepository();
        vendorMaster = new InMemoryCleaningVendorMasterPort();
        CleaningConfiguration configuration = new CleaningConfiguration(harness.configuration);
        CleaningRefusalRecorder refusals = new CleaningRefusalRecorder(harness.audit);
        support = new CleaningSupport(repository, harness.facilities, configuration, harness.authorization,
                harness.audit, harness.outbox, refusals, harness.clock);
        bookingDirectory = new TestBookingDirectory();
        SlaEvaluator sla = new SlaEvaluator(support);
        tasks = new CleaningTaskService(support, bookingDirectory, sla, harness.idempotency);
        schedules = new CleaningScheduleService(support);
        feedback = new CleaningFeedbackService(support, sla);
        vendors = new CleaningVendorService(support, vendorMaster, sla);
        capacity = new CleaningCapacityService(support, tasks);
        dashboard = new CleaningDashboardService(support, vendors);
        bookingCleaning = new BookingCleaningService(support, tasks);
        harness.bookingObservers.add(new BookingCleaningObserver(bookingCleaning));
    }

    // =============================================================================================
    // Test infrastructure
    // =============================================================================================

    /**
     * Resolves a claimed booking through the real S159 service, exactly as the production adapter does -
     * so "unresolvable" in these tests means what it means in production: no such booking, or one that
     * does not hold its space.
     */
    private final class TestBookingDirectory implements BookingDirectoryPort {
        @Override
        public Optional<BookingSnapshot> resolve(UUID bookingId) {
            if (bookingId == null) {
                return Optional.empty();
            }
            try {
                Booking booking = harness.bookings().findById(bookingId, harness.system, SourceChannel.SYSTEM);
                return Optional.of(S159BookingDirectoryAdapter.snapshot(booking));
            } catch (FacilitiesException.RecordNotFoundException absent) {
                return Optional.empty();
            }
        }
    }

    private ActorContext actor(gh.edu.clet.sfl.common.security.SflRole role, String site) {
        return gh.edu.clet.sfl.facilities.support.TestDoubles.actor("actor." + role, Set.of(role), site);
    }

    private Booking confirmBooking(CleaningRequirement requirement) {
        Booking requested = harness.bookings().request(new BookingCommands.RequestBooking(harness.meetingRoom.id(),
                BookingPurpose.MEETING, "Board meeting", null, IfimpTestHarness.NOW.plus(Duration.ofDays(1)),
                IfimpTestHarness.NOW.plus(Duration.ofDays(1)).plus(Duration.ofHours(2)), 0, 0, 10, null, java.util.Map.of(),
                null, requirement, harness.manager, SourceChannel.WEB, null, null));
        return requested.status() == gh.edu.clet.sfl.facilities.booking.domain.BookingStatus.CONFIRMED ? requested
                : harness.bookings().decide(new BookingCommands.DecideBooking(requested.id(), true, null,
                        requested.metadata().version(), harness.director, SourceChannel.WEB));
    }

    private CleaningSchedule schedule(SpaceType type, CleaningFrequency frequency, Set<DayOfWeek> days,
            List<LocalTime> times) {
        return schedules.create(new CleaningCommands.CreateSchedule("MAIN", "Daily " + type, type, null, frequency,
                days, times, 30, harness.manager, SourceChannel.WEB));
    }

    private gh.edu.clet.sfl.facilities.cleaning.domain.ChecklistTemplate template(SpaceType type, boolean photoOnSecondItem) {
        return schedules.createTemplate(new CleaningCommands.CreateTemplate("MAIN", type, "Standard " + type,
                List.of(new CleaningCommands.ChecklistItemSpec("FLOOR", "Floor swept and mopped", false),
                        new CleaningCommands.ChecklistItemSpec("BINS", "Bins emptied", photoOnSecondItem)),
                harness.manager, SourceChannel.WEB));
    }

    private CleaningVendor registerVendor(String reference) {
        vendorMaster.recordKnownReference("MAIN", reference, "Spotless Ltd", "seeded for test", "test", null, null);
        return vendors.register(new CleaningCommands.RegisterVendor("MAIN", reference, harness.manager,
                SourceChannel.WEB));
    }

    private VendorSlaTerms setTerms(UUID vendorId, int responseMinutes, int completionMinutes, String qualityFloor) {
        return vendors.setTerms(new CleaningCommands.SetSlaTerms(vendorId, responseMinutes, completionMinutes,
                new BigDecimal(qualityFloor), harness.manager, SourceChannel.WEB));
    }

    // =============================================================================================
    // S169-01. Cleaning Schedule Generation and Booking-Triggered Tasks
    // =============================================================================================

    @Nested
    @DisplayName("S169-01. Routine schedules and generation")
    class RoutineGeneration {

        @Test
        @DisplayName("a routine schedule is configurable by site, space type, room and frequency")
        void schedule_is_configurable() {
            CleaningSchedule bySpaceType = schedule(SpaceType.MEETING_ROOM, CleaningFrequency.DAILY, Set.of(),
                    List.of(LocalTime.of(7, 0)));
            assertThat(bySpaceType.roomId()).isNull();

            CleaningSchedule byRoom = schedules.create(new CleaningCommands.CreateSchedule("MAIN", "Hall deep clean",
                    SpaceType.EXAMINATION_HALL, harness.hall.id(), CleaningFrequency.WEEKLY, Set.of(DayOfWeek.FRIDAY),
                    List.of(LocalTime.of(18, 0)), 90, harness.manager, SourceChannel.WEB));
            assertThat(byRoom.roomId()).isEqualTo(harness.hall.id());
            assertThat(harness.audit.recorded(AuditAction.CLEANING_SCHEDULE_CREATED)).isTrue();
        }

        @Test
        @DisplayName("a weekly schedule naming more than one day is refused")
        void weekly_needs_exactly_one_day() {
            assertThatThrownBy(() -> schedule(SpaceType.OFFICE, CleaningFrequency.WEEKLY,
                    Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), List.of(LocalTime.of(7, 0))))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class);
        }

        @Test
        @DisplayName("AC: the sweep materialises tasks a configured horizon ahead, one per occurrence")
        void generation_materialises_occurrences_to_the_horizon() {
            schedule(SpaceType.OFFICE, CleaningFrequency.DAILY, Set.of(), List.of(LocalTime.of(7, 0)));
            CleaningScheduleService.GenerationResult result = schedules.generate("MAIN", harness.system,
                    SourceChannel.SCHEDULER);
            // Default horizon is 7 days; OFF-101 is the only office in MAIN.
            assertThat(result.tasksCreated()).isEqualTo(7);
            List<CleaningTask> created = tasks.search(new CleaningRepository.TaskQuery("MAIN", harness.office.id(),
                    null, TaskOrigin.ROUTINE, null, null, null, null, null, 0, 50), harness.manager, SourceChannel.WEB)
                    .items();
            assertThat(created).hasSize(7);
        }

        @Test
        @DisplayName("validation: no duplicate task per schedule occurrence - a second sweep is a no-op")
        void generation_is_idempotent() {
            schedule(SpaceType.OFFICE, CleaningFrequency.DAILY, Set.of(), List.of(LocalTime.of(7, 0)));
            CleaningScheduleService.GenerationResult first = schedules.generate("MAIN", harness.system,
                    SourceChannel.SCHEDULER);
            CleaningScheduleService.GenerationResult second = schedules.generate("MAIN", harness.system,
                    SourceChannel.SCHEDULER);
            assertThat(first.tasksCreated()).isGreaterThan(0);
            assertThat(second.tasksCreated()).isZero();
            assertThat(second.alreadyPresent()).isEqualTo(first.tasksCreated());
        }

        @Test
        @DisplayName("AC: a booking specifying post-event cleaning, when confirmed, raises a task with no manual re-entry")
        void booking_confirmation_raises_cleaning_tasks_without_manual_reentry() {
            Booking booking = confirmBooking(CleaningRequirement.SETUP_AND_TEARDOWN);
            List<CleaningTask> forBooking = repository.findTasksForBooking(booking.id());
            assertThat(forBooking).hasSize(2);
            assertThat(forBooking).extracting(CleaningTask::origin)
                    .containsExactlyInAnyOrder(TaskOrigin.BOOKING_SETUP, TaskOrigin.BOOKING_TEARDOWN);
            forBooking.forEach(task -> assertThat(task.bookingReference()).isEqualTo(booking.bookingReference()));
            assertThat(harness.audit.recorded(AuditAction.CLEANING_TASK_CREATED)).isTrue();
        }

        @Test
        @DisplayName("validation: the setup task is due by the occupied-window start")
        void setup_task_is_due_by_occupied_start() {
            Booking booking = confirmBooking(CleaningRequirement.SETUP);
            CleaningTask setup = repository.findTasksForBooking(booking.id()).get(0);
            assertThat(setup.dueBy()).isEqualTo(booking.window().occupied().start());
        }

        @Test
        @DisplayName("a booking asking for nothing is covered by the routine schedule, not a booking task")
        void a_booking_with_no_cleaning_requirement_raises_nothing() {
            Booking booking = confirmBooking(CleaningRequirement.NONE);
            assertThat(repository.findTasksForBooking(booking.id())).isEmpty();
        }

        @Test
        @DisplayName("reschedule moves the booking's cleaning tasks with it")
        void reschedule_moves_the_tasks() {
            Booking booking = confirmBooking(CleaningRequirement.SETUP_AND_TEARDOWN);
            Instant originalSetupDue = repository.findTasksForBooking(booking.id()).stream()
                    .filter(task -> task.origin() == TaskOrigin.BOOKING_SETUP).findFirst().orElseThrow().dueBy();

            Booking moved = harness.bookings().reschedule(new BookingCommands.RescheduleBooking(booking.id(),
                    booking.window().start().plus(Duration.ofDays(1)), booking.window().end().plus(Duration.ofDays(1)),
                    null, null, null, booking.metadata().version(), harness.manager, SourceChannel.WEB));

            CleaningTask setup = repository.findTasksForBooking(booking.id()).stream()
                    .filter(task -> task.origin() == TaskOrigin.BOOKING_SETUP).findFirst().orElseThrow();
            assertThat(setup.dueBy()).isEqualTo(moved.window().occupied().start());
            assertThat(setup.dueBy()).isNotEqualTo(originalSetupDue);
            assertThat(harness.audit.recorded(AuditAction.CLEANING_TASK_RESCHEDULED)).isTrue();
        }

        @Test
        @DisplayName("cancel withdraws the booking's cleaning tasks with the booking's reason recorded")
        void cancel_withdraws_tasks_with_reason() {
            Booking booking = confirmBooking(CleaningRequirement.TEARDOWN);
            harness.bookings().cancel(new BookingCommands.CancelBooking(booking.id(), "Event postponed",
                    booking.metadata().version(), harness.manager, SourceChannel.WEB));
            CleaningTask teardown = repository.findTasksForBooking(booking.id()).get(0);
            assertThat(teardown.status()).isEqualTo(TaskStatus.CANCELLED);
            assertThat(teardown.cancellationReason()).contains("Event postponed");
        }

        @Test
        @DisplayName("error state: a task claiming an unresolvable booking is rejected at creation")
        void unresolvable_booking_reference_is_rejected() {
            UUID phantom = UUID.randomUUID();
            assertThatThrownBy(() -> tasks.raise(new CleaningCommands.RaiseTask(harness.meetingRoom.id(),
                    TaskOrigin.BOOKING_SETUP, null, null, phantom, null, null, harness.manager, SourceChannel.WEB,
                    null, null)))
                    .isInstanceOfSatisfying(FacilitiesException.class,
                            failure -> assertThat(failure.code()).isEqualTo(FacilitiesErrorCode.CLEANING_BOOKING_UNLINKED));
            assertThat(harness.audit.recorded(AuditAction.CLEANING_BOOKING_TASK_REJECTED)).isTrue();
        }

        @Test
        @DisplayName("error state: a cancelled booking no longer resolves for a new booking-origin task")
        void a_cancelled_booking_does_not_resolve() {
            Booking booking = confirmBooking(CleaningRequirement.NONE);
            harness.bookings().cancel(new BookingCommands.CancelBooking(booking.id(), "No longer needed",
                    booking.metadata().version(), harness.manager, SourceChannel.WEB));
            assertThatThrownBy(() -> tasks.raise(new CleaningCommands.RaiseTask(harness.meetingRoom.id(),
                    TaskOrigin.BOOKING_TEARDOWN, null, null, booking.id(), null, null, harness.manager,
                    SourceChannel.WEB, null, null)))
                    .isInstanceOfSatisfying(FacilitiesException.class,
                            failure -> assertThat(failure.code()).isEqualTo(FacilitiesErrorCode.CLEANING_BOOKING_UNLINKED));
        }

        @Test
        @DisplayName("a reactive request can be raised directly by any occupant")
        void reactive_request_raised_by_occupant() {
            CleaningTask task = tasks.request(new CleaningCommands.RaiseRequest(harness.office.id(), "Spill in office",
                    null, harness.requester, SourceChannel.WEB, null, null));
            assertThat(task.origin()).isEqualTo(TaskOrigin.REACTIVE);
            assertThat(task.requestedBy()).isEqualTo(harness.requester.actorId());
        }

        @Test
        @DisplayName("an IFIMP_REQUESTER sees only the reactive requests they raised")
        void requester_sees_only_their_own_requests() {
            tasks.request(new CleaningCommands.RaiseRequest(harness.office.id(), "Spill", null, harness.requester,
                    SourceChannel.WEB, null, null));
            ActorContext otherRequester = actor(gh.edu.clet.sfl.common.security.SflRole.IFIMP_REQUESTER, "MAIN");
            tasks.request(new CleaningCommands.RaiseRequest(harness.office.id(), "Another spill", null,
                    otherRequester, SourceChannel.WEB, null, null));

            var seenByFirst = tasks.search(new CleaningRepository.TaskQuery("MAIN", null, null, null, null, null,
                    null, null, null, 0, 50), harness.requester, SourceChannel.WEB);
            assertThat(seenByFirst.items()).hasSize(1);
            assertThat(seenByFirst.items().get(0).requestedBy()).isEqualTo(harness.requester.actorId());
        }
    }

    // =============================================================================================
    // S169-02. Checklist Execution with Photo Evidence and Occupant Feedback
    // =============================================================================================

    @Nested
    @DisplayName("S169-02. Checklist, photo evidence and feedback")
    class ChecklistAndFeedback {

        private CleaningTask raiseOfficeTask() {
            template(SpaceType.OFFICE, true);
            return tasks.raise(new CleaningCommands.RaiseTask(harness.office.id(), TaskOrigin.ADHOC, null, null, null,
                    IfimpTestHarness.NOW, IfimpTestHarness.NOW.plus(Duration.ofHours(1)), harness.manager,
                    SourceChannel.WEB, null, null));
        }

        @Test
        @DisplayName("each task carries the checklist for its space type, with a configurable photo-required subset")
        void task_carries_the_space_type_checklist() {
            CleaningTask task = raiseOfficeTask();
            List<TaskChecklistItem> items = tasks.checklist(task.id(), harness.manager, SourceChannel.WEB);
            assertThat(items).hasSize(2);
            assertThat(items).filteredOn(item -> item.itemCode().equals("BINS")).singleElement()
                    .satisfies(item -> assertThat(item.photoRequired()).isTrue());
            assertThat(items).filteredOn(item -> item.itemCode().equals("FLOOR")).singleElement()
                    .satisfies(item -> assertThat(item.photoRequired()).isFalse());
        }

        @Test
        @DisplayName("AC: a checklist item requiring photo evidence cannot be marked complete without it")
        void completion_refused_when_required_photo_missing() {
            CleaningTask task = raiseOfficeTask();
            ActorContext cleaner = reassignedTo(task);
            CleaningTask assigned = tasks.find(task.id(), harness.manager, SourceChannel.WEB);
            tasks.start(new CleaningCommands.StartTask(task.id(), assigned.metadata().version(), cleaner,
                    SourceChannel.WEB));
            List<TaskChecklistItem> items = tasks.checklist(task.id(), harness.manager, SourceChannel.WEB);
            TaskChecklistItem floor = items.stream().filter(item -> item.itemCode().equals("FLOOR")).findFirst()
                    .orElseThrow();
            tasks.recordItem(new CleaningCommands.RecordChecklistItem(task.id(), floor.id(), true, null, null, null,
                    cleaner, SourceChannel.WEB));
            // BINS left undone and unphotographed.
            long completionVersion = tasks.find(task.id(), harness.manager, SourceChannel.WEB).metadata().version();
            assertThatThrownBy(() -> tasks.complete(new CleaningCommands.CompleteTask(task.id(), "done", null,
                    completionVersion, cleaner, SourceChannel.WEB)))
                    .isInstanceOfSatisfying(FacilitiesException.class, failure -> {
                        assertThat(failure.code()).isEqualTo(FacilitiesErrorCode.CLEANING_CHECKLIST_INCOMPLETE);
                        assertThat(failure.getMessage()).contains("BINS");
                    });
            assertThat(harness.audit.recorded(AuditAction.CLEANING_TASK_COMPLETION_REFUSED)).isTrue();
        }

        /** Assignment must name the actor that will execute; the harness's technician is used throughout. */
        private ActorContext reassignedTo(CleaningTask task) {
            tasks.assign(new CleaningCommands.AssignTask(task.id(), AssigneeType.STAFF, harness.technician.actorId(),
                    null, tasks.find(task.id(), harness.manager, SourceChannel.WEB).metadata().version(),
                    harness.supervisor, SourceChannel.WEB));
            return harness.technician;
        }

        @Test
        @DisplayName("a task completes once every required item is done and every required photo is present")
        void completion_succeeds_once_checklist_is_addressed() {
            CleaningTask task = raiseOfficeTask();
            ActorContext cleaner = reassignedTo(task);
            CleaningTask assigned = tasks.find(task.id(), harness.manager, SourceChannel.WEB);
            tasks.start(new CleaningCommands.StartTask(task.id(), assigned.metadata().version(), cleaner,
                    SourceChannel.WEB));
            List<TaskChecklistItem> items = tasks.checklist(task.id(), harness.manager, SourceChannel.WEB);
            for (TaskChecklistItem item : items) {
                String hash = "a".repeat(64);
                tasks.recordItem(new CleaningCommands.RecordChecklistItem(task.id(), item.id(), true,
                        item.photoRequired() ? "evidence/office/bins-1.jpg" : null,
                        item.photoRequired() ? hash : null, null, cleaner, SourceChannel.WEB));
            }
            CleaningTask completed = tasks.complete(new CleaningCommands.CompleteTask(task.id(), "All done", null,
                    tasks.find(task.id(), harness.manager, SourceChannel.WEB).metadata().version(), cleaner,
                    SourceChannel.WEB));
            assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(harness.outbox.published("sfl.ifimp.cleaning-task-completed.v1")).isTrue();
        }

        @Test
        @DisplayName("a VENDOR_TECHNICIAN sees and acts only on tasks assigned to them")
        void vendor_technician_narrowed_to_own_assignments() {
            CleaningVendor vendor = registerVendor("VM-100");
            CleaningTask task = raiseOfficeTask();
            tasks.assign(new CleaningCommands.AssignTask(task.id(), AssigneeType.VENDOR,
                    harness.vendorTechnician.actorId(), vendor.id(), task.metadata().version(), harness.manager,
                    SourceChannel.WEB));
            CleaningTask otherTask = tasks.raise(new CleaningCommands.RaiseTask(harness.meetingRoom.id(),
                    TaskOrigin.ADHOC, null, null, null, IfimpTestHarness.NOW, IfimpTestHarness.NOW.plus(Duration.ofHours(1)),
                    harness.manager, SourceChannel.WEB, null, null));

            assertThat(tasks.find(task.id(), harness.vendorTechnician, SourceChannel.WEB).id()).isEqualTo(task.id());
            assertThatThrownBy(() -> tasks.find(otherTask.id(), harness.vendorTechnician, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);

            var visible = tasks.search(new CleaningRepository.TaskQuery("MAIN", null, null, null, null, null, null,
                    null, null, 0, 50), harness.vendorTechnician, SourceChannel.WEB);
            assertThat(visible.items()).extracting(CleaningTask::id).containsExactly(task.id());
        }

        @Test
        @DisplayName("occupant feedback is accepted only after completion")
        void feedback_only_after_completion() {
            CleaningTask task = raiseOfficeTask();
            assertThatThrownBy(() -> feedback.submit(new CleaningCommands.SubmitFeedback(task.id(), 4, null,
                    harness.requester, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("repeated low ratings for the same space surface a flag for supervisor review")
        void repeated_low_ratings_for_a_space_flag_for_review() {
            template(SpaceType.OFFICE, false);
            for (int i = 0; i < 3; i++) {
                CleaningTask task = tasks.raise(new CleaningCommands.RaiseTask(harness.office.id(), TaskOrigin.ADHOC,
                        null, null, null, IfimpTestHarness.NOW.plus(Duration.ofHours(i)),
                        IfimpTestHarness.NOW.plus(Duration.ofHours(i + 1)), harness.manager, SourceChannel.WEB, null,
                        null));
                ActorContext cleaner = reassignedTo(task);
                tasks.start(new CleaningCommands.StartTask(task.id(), tasks.find(task.id(), harness.manager,
                        SourceChannel.WEB).metadata().version(), cleaner, SourceChannel.WEB));
                for (TaskChecklistItem item : tasks.checklist(task.id(), harness.manager, SourceChannel.WEB)) {
                    tasks.recordItem(new CleaningCommands.RecordChecklistItem(task.id(), item.id(), true, null, null,
                            null, cleaner, SourceChannel.WEB));
                }
                tasks.complete(new CleaningCommands.CompleteTask(task.id(), null, null,
                        tasks.find(task.id(), harness.manager, SourceChannel.WEB).metadata().version(), cleaner,
                        SourceChannel.WEB));
                feedback.submit(new CleaningCommands.SubmitFeedback(task.id(), 1, "Not clean", harness.requester,
                        SourceChannel.WEB));
            }
            List<LowRatingFlag> openFlags = feedback.flags("MAIN", true, harness.manager, SourceChannel.WEB);
            assertThat(openFlags).hasSize(1);
            assertThat(openFlags.get(0).subjectType()).isEqualTo(FlagSubjectType.SPACE);
            assertThat(openFlags.get(0).lowRatingCount()).isEqualTo(3);
            assertThat(harness.audit.recorded(AuditAction.CLEANING_LOW_RATING_FLAGGED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.cleaning-low-rating-flagged.v1")).isTrue();

            LowRatingFlag reviewed = feedback.review(new CleaningCommands.ReviewFlag(openFlags.get(0).id(),
                    "Spoke to the crew", harness.manager, SourceChannel.WEB));
            assertThat(reviewed.open()).isFalse();
            assertThat(feedback.flags("MAIN", true, harness.manager, SourceChannel.WEB)).isEmpty();
        }
    }

    // =============================================================================================
    // S169-03. Vendor SLA Tracking
    // =============================================================================================

    @Nested
    @DisplayName("S169-03. Vendor SLA tracking")
    class VendorSla {

        @Test
        @DisplayName("vendor base is drawn from Vendor Master through the recorded stand-in adapter")
        void vendor_resolves_only_against_a_known_reference() {
            CleaningVendor vendor = registerVendor("VM-200");
            assertThat(vendor.vendorMasterReference()).isEqualTo("VM-200");
            assertThat(harness.audit.recorded(AuditAction.CLEANING_VENDOR_REGISTERED)).isTrue();
        }

        @Test
        @DisplayName("error state: an unregistered reference is CLEANING_VENDOR_NOT_FOUND")
        void unknown_reference_is_refused() {
            assertThatThrownBy(() -> vendors.register(new CleaningCommands.RegisterVendor("MAIN", "VM-UNKNOWN",
                    harness.manager, SourceChannel.WEB)))
                    .isInstanceOfSatisfying(FacilitiesException.class,
                            failure -> assertThat(failure.code()).isEqualTo(FacilitiesErrorCode.CLEANING_VENDOR_NOT_FOUND));
        }

        @Test
        @DisplayName("contracted SLA terms are versioned; a new version closes the one in force")
        void sla_terms_are_versioned() {
            CleaningVendor vendor = registerVendor("VM-300");
            VendorSlaTerms first = setTerms(vendor.id(), 60, 180, "3.00");
            VendorSlaTerms second = setTerms(vendor.id(), 30, 120, "4.00");
            List<VendorSlaTerms> history = vendors.termsHistory(vendor.id(), harness.manager, SourceChannel.WEB);
            assertThat(history).hasSize(2);
            assertThat(history.get(0).effectiveTo()).isNotNull();
            assertThat(history.get(1).id()).isEqualTo(second.id());
            assertThat(history.get(1).effectiveTo()).isNull();
        }

        private CleaningTask reactiveVendorTask(CleaningVendor vendor) {
            CleaningTask task = tasks.request(new CleaningCommands.RaiseRequest(harness.office.id(), "Spill",
                    null, harness.requester, SourceChannel.WEB, null, null));
            return tasks.assign(new CleaningCommands.AssignTask(task.id(), AssigneeType.VENDOR,
                    harness.vendorTechnician.actorId(), vendor.id(), task.metadata().version(), harness.manager,
                    SourceChannel.WEB));
        }

        @Test
        @DisplayName("AC: contracted response time exceeded on a reactive request breaches the vendor's scorecard")
        void response_breach_recorded_when_contracted_time_exceeded() {
            CleaningVendor vendor = registerVendor("VM-400");
            setTerms(vendor.id(), 30, 180, "3.00");
            CleaningTask assigned = reactiveVendorTask(vendor);
            harness.clock.advance(Duration.ofMinutes(45));
            tasks.start(new CleaningCommands.StartTask(assigned.id(), assigned.metadata().version(),
                    harness.vendorTechnician, SourceChannel.WEB));

            var scorecard = vendors.scorecard(vendor.id(), null, null, harness.manager, SourceChannel.WEB);
            assertThat(scorecard.responseBreaches()).isEqualTo(1);
            assertThat(harness.audit.recorded(AuditAction.CLEANING_SLA_BREACH_RECORDED)).isTrue();
            assertThat(harness.outbox.published("sfl.ifimp.cleaning-sla-breach-recorded.v1")).isTrue();
        }

        @Test
        @DisplayName("validation: a vendor-supplied completion time never overrides the task's own timestamps")
        void vendor_claimed_completion_does_not_override_recorded_completion() {
            template(SpaceType.OFFICE, false);
            CleaningVendor vendor = registerVendor("VM-500");
            setTerms(vendor.id(), 60, 30, "3.00");
            CleaningTask assigned = reactiveVendorTask(vendor);
            tasks.start(new CleaningCommands.StartTask(assigned.id(), assigned.metadata().version(),
                    harness.vendorTechnician, SourceChannel.WEB));
            for (TaskChecklistItem item : tasks.checklist(assigned.id(), harness.manager, SourceChannel.WEB)) {
                tasks.recordItem(new CleaningCommands.RecordChecklistItem(assigned.id(), item.id(), true, null, null,
                        null, harness.vendorTechnician, SourceChannel.WEB));
            }
            harness.clock.advance(Duration.ofMinutes(90));
            // The vendor claims it finished on time - well before the actual (recorded) completion.
            Instant vendorClaim = assigned.requestedAt().plus(Duration.ofMinutes(10));
            CleaningTask completed = tasks.complete(new CleaningCommands.CompleteTask(assigned.id(), "done",
                    vendorClaim, tasks.find(assigned.id(), harness.manager, SourceChannel.WEB).metadata().version(),
                    harness.vendorTechnician, SourceChannel.WEB));

            assertThat(completed.vendorReportedCompletedAt()).isEqualTo(vendorClaim);
            assertThat(completed.completedAt()).isNotEqualTo(vendorClaim);
            assertThat(completed.completionDiscrepancySeconds()).isNotNull();
            assertThat(harness.audit.recorded(AuditAction.CLEANING_COMPLETION_DISCREPANCY_RECORDED)).isTrue();

            var scorecard = vendors.scorecard(vendor.id(), null, null, harness.manager, SourceChannel.WEB);
            // Breach is computed from the recorded completion (90+ minutes), not the vendor's ten-minute claim.
            assertThat(scorecard.completionBreaches()).isEqualTo(1);
        }

        @Test
        @DisplayName("a rating below the contracted quality floor is a quality breach")
        void low_rating_below_floor_is_a_quality_breach() {
            template(SpaceType.OFFICE, false);
            CleaningVendor vendor = registerVendor("VM-600");
            setTerms(vendor.id(), 60, 180, "4.00");
            CleaningTask task = reactiveVendorTask(vendor);
            tasks.start(new CleaningCommands.StartTask(task.id(), task.metadata().version(), harness.vendorTechnician,
                    SourceChannel.WEB));
            for (TaskChecklistItem item : tasks.checklist(task.id(), harness.manager, SourceChannel.WEB)) {
                tasks.recordItem(new CleaningCommands.RecordChecklistItem(task.id(), item.id(), true, null, null, null,
                        harness.vendorTechnician, SourceChannel.WEB));
            }
            tasks.complete(new CleaningCommands.CompleteTask(task.id(), null, null,
                    tasks.find(task.id(), harness.manager, SourceChannel.WEB).metadata().version(),
                    harness.vendorTechnician, SourceChannel.WEB));
            feedback.submit(new CleaningCommands.SubmitFeedback(task.id(), 2, "Missed spots", harness.requester,
                    SourceChannel.WEB));

            // The scorecard's window is half-open on "now"; advance so the request raised at the
            // original instant falls strictly inside [from, now) rather than landing on its own boundary.
            harness.clock.advance(Duration.ofMinutes(1));
            var scorecard = vendors.scorecard(vendor.id(), null, null, harness.manager, SourceChannel.WEB);
            assertThat(scorecard.qualityBreaches()).isEqualTo(1);
        }
    }

    // =============================================================================================
    // S169-04. Cleaning Coverage Feed to Event Logistics
    // =============================================================================================

    @Nested
    @DisplayName("S169-04. Capacity for S173")
    class CapacityForEventLogistics {

        @Test
        @DisplayName("AC: reserve either reserves, creating the linked task, or returns a conflict naming the commitment")
        void reserve_or_conflict_naming_the_competing_commitment() {
            EventCleaningCapacity.ReservationRequest request = new EventCleaningCapacity.ReservationRequest("MAIN",
                    harness.hall.id(), null, IfimpTestHarness.NOW.plus(Duration.ofHours(2)),
                    IfimpTestHarness.NOW.plus(Duration.ofHours(3)), "Stage teardown", "EVT-100", "coordinator.kwame");
            EventCleaningCapacity.Reservation reserved = capacity.reserve(request);
            assertThat(reserved.status()).isEqualTo(EventCleaningCapacity.Status.RESERVED);
            assertThat(reserved.cleaningTaskId()).isNotNull();
            CleaningTask task = repository.findTask(reserved.cleaningTaskId()).orElseThrow();
            assertThat(task.reservationId()).isEqualTo(reserved.reservationId());
            assertThat(harness.audit.recorded(AuditAction.CLEANING_CAPACITY_RESERVED)).isTrue();

            EventCleaningCapacity.ReservationRequest overlapping = new EventCleaningCapacity.ReservationRequest("MAIN",
                    harness.hall.id(), null, IfimpTestHarness.NOW.plus(Duration.ofMinutes(150)),
                    IfimpTestHarness.NOW.plus(Duration.ofMinutes(210)), "Another event", "EVT-101", "coordinator.ama");
            EventCleaningCapacity.Reservation conflict = capacity.reserve(overlapping);
            assertThat(conflict.status()).isEqualTo(EventCleaningCapacity.Status.CONFLICT);
            assertThat(conflict.competingCommitmentId()).isEqualTo(task.id());
            // AC: the conflict names the competing commitment - task number, what/where/when and assignee.
            assertThat(conflict.competingCommitment()).contains(task.taskNumber()).contains("HALL-A");
            assertThat(harness.audit.recorded(AuditAction.CLEANING_CAPACITY_CONFLICT_RETURNED)).isTrue();
        }

        @Test
        @DisplayName("a room cannot have two overlapping cleaning commitments even from different sources")
        void room_cannot_have_two_overlapping_commitments() {
            tasks.raise(new CleaningCommands.RaiseTask(harness.hall.id(), TaskOrigin.ADHOC, null, null, null,
                    IfimpTestHarness.NOW.plus(Duration.ofHours(1)), IfimpTestHarness.NOW.plus(Duration.ofHours(2)),
                    harness.manager, SourceChannel.WEB, null, null));
            EventCleaningCapacity.Reservation result = capacity.reserve(new EventCleaningCapacity.ReservationRequest(
                    "MAIN", harness.hall.id(), null, IfimpTestHarness.NOW.plus(Duration.ofMinutes(90)),
                    IfimpTestHarness.NOW.plus(Duration.ofMinutes(150)), null, "EVT-200", "coordinator"));
            assertThat(result.status()).isEqualTo(EventCleaningCapacity.Status.CONFLICT);
        }

        @Test
        @DisplayName("release is idempotent")
        void release_is_idempotent() {
            EventCleaningCapacity.Reservation reserved = capacity.reserve(new EventCleaningCapacity.ReservationRequest(
                    "MAIN", harness.meetingRoom.id(), null, IfimpTestHarness.NOW.plus(Duration.ofHours(4)),
                    IfimpTestHarness.NOW.plus(Duration.ofHours(5)), null, "EVT-300", "coordinator"));
            capacity.release(reserved.reservationId(), "Event cancelled");
            capacity.release(reserved.reservationId(), "Event cancelled again");
            assertThat(capacity.find(reserved.reservationId()).orElseThrow().status())
                    .isEqualTo(EventCleaningCapacity.Status.RELEASED);
            assertThat(repository.findTask(reserved.cleaningTaskId()).orElseThrow().status())
                    .isEqualTo(TaskStatus.CANCELLED);
        }

        @Test
        @DisplayName("find reports RESERVED, FULFILLED once the task completes, and RELEASED")
        void find_reports_the_reservation_lifecycle() {
            template(SpaceType.MEETING_ROOM, false);
            EventCleaningCapacity.Reservation reserved = capacity.reserve(new EventCleaningCapacity.ReservationRequest(
                    "MAIN", harness.meetingRoom.id(), null, IfimpTestHarness.NOW.plus(Duration.ofHours(6)),
                    IfimpTestHarness.NOW.plus(Duration.ofHours(7)), null, "EVT-400", "coordinator"));
            assertThat(capacity.find(reserved.reservationId()).orElseThrow().status())
                    .isEqualTo(EventCleaningCapacity.Status.RESERVED);

            CleaningTask task = repository.findTask(reserved.cleaningTaskId()).orElseThrow();
            ActorContext cleaner = reassignedForTest(task);
            tasks.start(new CleaningCommands.StartTask(task.id(), tasks.find(task.id(), harness.manager,
                    SourceChannel.WEB).metadata().version(), cleaner, SourceChannel.WEB));
            for (TaskChecklistItem item : tasks.checklist(task.id(), harness.manager, SourceChannel.WEB)) {
                tasks.recordItem(new CleaningCommands.RecordChecklistItem(task.id(), item.id(), true, null, null, null,
                        cleaner, SourceChannel.WEB));
            }
            tasks.complete(new CleaningCommands.CompleteTask(task.id(), null, null,
                    tasks.find(task.id(), harness.manager, SourceChannel.WEB).metadata().version(), cleaner,
                    SourceChannel.WEB));
            assertThat(capacity.find(reserved.reservationId()).orElseThrow().status())
                    .isEqualTo(EventCleaningCapacity.Status.FULFILLED);
        }

        private ActorContext reassignedForTest(CleaningTask task) {
            tasks.assign(new CleaningCommands.AssignTask(task.id(), AssigneeType.STAFF, harness.technician.actorId(),
                    null, tasks.find(task.id(), harness.manager, SourceChannel.WEB).metadata().version(),
                    harness.supervisor, SourceChannel.WEB));
            return harness.technician;
        }

        @Test
        @DisplayName("the crews-per-site configuration limits how many commitments run at once, across rooms")
        void crews_per_site_limits_concurrent_commitments() {
            harness.configuration.set("cleaning.capacity.crews", "1");
            capacity.reserve(new EventCleaningCapacity.ReservationRequest("MAIN", harness.meetingRoom.id(), null,
                    IfimpTestHarness.NOW.plus(Duration.ofHours(8)), IfimpTestHarness.NOW.plus(Duration.ofHours(9)),
                    null, "EVT-500", "coordinator"));
            EventCleaningCapacity.Reservation second = capacity.reserve(new EventCleaningCapacity.ReservationRequest(
                    "MAIN", harness.office.id(), null, IfimpTestHarness.NOW.plus(Duration.ofMinutes(495)),
                    IfimpTestHarness.NOW.plus(Duration.ofMinutes(525)), null, "EVT-501", "coordinator"));
            assertThat(second.status()).isEqualTo(EventCleaningCapacity.Status.CONFLICT);
        }

        @Test
        @DisplayName("the schedule/capacity read endpoint reports the crews configured and what is committed")
        void capacity_view_reports_configured_crews_and_commitments() {
            capacity.reserve(new EventCleaningCapacity.ReservationRequest("MAIN", harness.hall.id(), null,
                    IfimpTestHarness.NOW.plus(Duration.ofHours(10)), IfimpTestHarness.NOW.plus(Duration.ofHours(11)),
                    null, "EVT-600", "coordinator"));
            var view = capacity.view("MAIN", IfimpTestHarness.NOW, IfimpTestHarness.NOW.plus(Duration.ofDays(1)),
                    harness.manager, SourceChannel.WEB);
            assertThat(view.crews()).isEqualTo(2);
            assertThat(view.commitments()).isNotEmpty();
        }
    }

    // =============================================================================================
    // Dashboard
    // =============================================================================================

    @Nested
    @DisplayName("Dashboard read")
    class Dashboard {

        @Test
        @DisplayName("scheduled vs completed by site and overdue reactive requests are reported")
        void dashboard_reports_scheduled_completed_and_overdue() {
            template(SpaceType.OFFICE, false);
            CleaningTask task = tasks.raise(new CleaningCommands.RaiseTask(harness.office.id(), TaskOrigin.ADHOC, null,
                    null, null, IfimpTestHarness.NOW, IfimpTestHarness.NOW.plus(Duration.ofHours(1)), harness.manager,
                    SourceChannel.WEB, null, null));
            tasks.request(new CleaningCommands.RaiseRequest(harness.meetingRoom.id(), "Overdue spill", null,
                    harness.requester, SourceChannel.WEB, null, null));
            harness.clock.advance(Duration.ofHours(6));

            var result = dashboard.dashboard("MAIN", IfimpTestHarness.NOW.minus(Duration.ofDays(1)),
                    harness.clock.instant().plus(Duration.ofDays(1)), harness.manager, SourceChannel.WEB);
            var site = result.sites().get(0);
            assertThat(site.scheduled()).isGreaterThanOrEqualTo(2);
            assertThat(site.overdueReactiveCount()).isEqualTo(1);
            assertThat(task).isNotNull();
        }
    }

    // =============================================================================================
    // Authorisation and refusals
    // =============================================================================================

    @Nested
    @DisplayName("Authorisation")
    class Authorisation {

        @Test
        @DisplayName("wrong role: an actor with no cleaning permission is refused, audited")
        void wrong_role_is_refused() {
            assertThatThrownBy(() -> schedules.create(new CleaningCommands.CreateSchedule("MAIN", "x",
                    SpaceType.OFFICE, null, CleaningFrequency.DAILY, Set.of(), List.of(LocalTime.of(7, 0)), 30,
                    harness.energyOfficer, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("other site: a manager at KSI cannot manage a MAIN schedule")
        void other_site_is_refused() {
            assertThatThrownBy(() -> schedules.create(new CleaningCommands.CreateSchedule("MAIN", "x",
                    SpaceType.OFFICE, null, CleaningFrequency.DAILY, Set.of(), List.of(LocalTime.of(7, 0)), 30,
                    harness.kumasiManager, SourceChannel.WEB)))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }

        @Test
        @DisplayName("per-record narrowing: a requester cannot read another occupant's reactive request by id")
        void requester_cannot_read_anothers_request() {
            CleaningTask task = tasks.request(new CleaningCommands.RaiseRequest(harness.office.id(), "Spill", null,
                    harness.requester, SourceChannel.WEB, null, null));
            ActorContext otherRequester = actor(gh.edu.clet.sfl.common.security.SflRole.IFIMP_REQUESTER, "MAIN");
            assertThatThrownBy(() -> tasks.find(task.id(), otherRequester, SourceChannel.WEB))
                    .isInstanceOf(FacilitiesException.UnauthorizedScopeException.class);
        }
    }
}
