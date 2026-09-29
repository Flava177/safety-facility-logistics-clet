package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.BookingDirectoryPort;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking-triggered cleaning - SRS-SFL-S169-01: "a Room &amp; Resource Booking (S159) request that
 * specifies a setup/teardown cleaning requirement automatically raises a task against this schedule
 * rather than requiring a separate manual request."
 *
 * <p>Reached only from the S159 lifecycle observer adapter, inside the booking's own transaction (see
 * {@code BookingLifecycleObserver} for why that is the right trade). So:
 *
 * <ul>
 *   <li><strong>No permission check.</strong> The booking change was already authorised by S159; the
 *       clean is its consequence, not a second request. Refusing here would refuse the booking.</li>
 *   <li><strong>Idempotent.</strong> A second confirmation, or a reschedule of a booking whose clean was
 *       never raised, ends with exactly one live setup and one live teardown task per booking - the
 *       database's {@code ux_cleaning_tasks_booking_origin} holds the same line.</li>
 *   <li><strong>Quick, and no call back into booking.</strong> The snapshot carries everything needed.</li>
 * </ul>
 *
 * <p>The audit records carry the actor who changed the booking - the approver, the requester, or the
 * no-show sweep's system account - so "who raised this clean" reads as "whoever confirmed the booking".
 */
@Service
public class BookingCleaningService {

    private final CleaningSupport support;
    private final CleaningRepository repository;
    private final CleaningTaskService tasks;

    public BookingCleaningService(CleaningSupport support, CleaningTaskService tasks) {
        this.support = support;
        this.repository = support.repository();
        this.tasks = tasks;
    }

    /** The booking is confirmed: raise whatever cleaning it asked for, with its back-reference. */
    @Transactional
    public List<CleaningTask> bookingConfirmed(BookingDirectoryPort.BookingSnapshot booking, ActorContext actor) {
        if (!booking.beforeUse() && !booking.afterUse()) {
            return List.of();
        }
        FacilityRoom room = support.requireRoom(booking.roomId());
        List<CleaningTask> live = liveTasks(booking);
        List<CleaningTask> raised = new ArrayList<>();
        if (booking.beforeUse() && live.stream().noneMatch(task -> task.origin() == TaskOrigin.BOOKING_SETUP)) {
            raised.add(raise(room, TaskOrigin.BOOKING_SETUP, booking, actor));
        }
        if (booking.afterUse() && live.stream().noneMatch(task -> task.origin() == TaskOrigin.BOOKING_TEARDOWN)) {
            raised.add(raise(room, TaskOrigin.BOOKING_TEARDOWN, booking, actor));
        }
        return List.copyOf(raised);
    }

    /** The booking moved: its live cleans move with it. A clean already done stays where it happened. */
    @Transactional
    public List<CleaningTask> bookingRescheduled(BookingDirectoryPort.BookingSnapshot booking, ActorContext actor) {
        List<CleaningTask> moved = new ArrayList<>();
        for (CleaningTask task : liveTasks(booking)) {
            if (!task.origin().claimsBooking()) {
                continue;
            }
            BookingTaskWindows.Window window = BookingTaskWindows.of(task.origin(), booking,
                    support.configuration().bookingTaskLength(task.siteCode()));
            if (window.start().equals(task.windowStart()) && window.dueBy().equals(task.dueBy())) {
                continue;
            }
            CleaningTask after = repository.saveTask(task.moveWindow(window.start(), window.dueBy(), actor.actorId(),
                    support.now(), SourceChannel.SYSTEM, actor.correlationId()));
            support.audit().record(actor, SourceChannel.SYSTEM, AuditAction.CLEANING_TASK_RESCHEDULED,
                    "CleaningTask", after.id().toString(), after.siteCode(), task, after);
            moved.add(after);
        }
        // A booking confirmed before this module existed, or whose clean was cancelled by hand, gets its
        // clean now rather than silently staying without one after the move.
        moved.addAll(bookingConfirmed(booking, actor));
        return List.copyOf(moved);
    }

    /** The booking will not happen: its live cleans are cancelled, and the booking's reason recorded. */
    @Transactional
    public List<CleaningTask> bookingWithdrawn(BookingDirectoryPort.BookingSnapshot booking, String reason,
            ActorContext actor) {
        String recorded = "Booking " + booking.bookingReference() + " withdrawn"
                + (reason == null || reason.isBlank() ? "." : ": " + reason.strip());
        List<CleaningTask> cancelled = new ArrayList<>();
        for (CleaningTask task : liveTasks(booking)) {
            cancelled.add(tasks.cancelTask(task, recorded, actor, SourceChannel.SYSTEM));
        }
        return List.copyOf(cancelled);
    }

    private List<CleaningTask> liveTasks(BookingDirectoryPort.BookingSnapshot booking) {
        return repository.findTasksForBooking(booking.bookingId()).stream().filter(CleaningTask::isLive).toList();
    }

    private CleaningTask raise(FacilityRoom room, TaskOrigin origin, BookingDirectoryPort.BookingSnapshot booking,
            ActorContext actor) {
        BookingTaskWindows.Window window = BookingTaskWindows.of(origin, booking,
                support.configuration().bookingTaskLength(room.siteCode()));
        return support.raise(room, origin, BookingTaskWindows.title(origin, booking), null, null, null,
                booking.bookingId(), booking.bookingReference(), null, null, window.start(), window.dueBy(),
                actor.actorId(), actor, SourceChannel.SYSTEM);
    }
}
