package gh.edu.clet.sfl.facilities.booking.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTask;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Room turnaround — SRS-SFL-S159-02, "setup task".
 *
 * <p>Deliberately thin, and see {@link SetupTask} for why it is not an S153 work order: routing a
 * twenty-minute chair rearrangement through the CMMS would put it in the same queue as a failed
 * standby generator, with an escalation ladder and a closure-evidence gate, and the generator would
 * end up on page four.
 *
 * <p>The queue this exposes is ordered by when the room is needed rather than by when the task was
 * raised. A task for this afternoon matters more than one raised last week for next month, and a
 * created-at ordering gets that backwards every time.
 */
@Service
public class BookingSetupService {

    private final BookingRepository bookings;
    private final BookingApplicationService booking;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final Clock clock;

    public BookingSetupService(BookingRepository bookings, BookingApplicationService booking,
            FacilitiesAuthorization authorization, AuditPort audit, Clock clock) {
        this.bookings = bookings;
        this.booking = booking;
        this.authorization = authorization;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public List<SetupTask> create(BookingCommands.CreateSetupTasks command) {
        ActorContext actor = command.actor();
        Booking target = booking.requireBooking(command.bookingId());
        authorization.require(actor, SflPermission.FACILITIES_SETUP_TASK_MANAGE, target.siteCode(),
                command.channel(), "SetupTask", "new");
        if (!target.holdsTheSpace()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Setup work cannot be raised against a " + target.status() + " booking.");
        }

        Instant at = clock.instant();
        List<SetupTask> created = new ArrayList<>();
        for (BookingCommands.CreateSetupTasks.NewSetupTask requested : command.tasks()) {
            SetupTask task = bookings.saveSetupTask(SetupTask.create(UUID.randomUUID(), target,
                    requested.description(), requested.dueBy(), requested.assignedTo()));
            audit.record(actor, command.channel(), AuditAction.BOOKING_SETUP_TASK_CREATED, "SetupTask",
                    task.id().toString(), target.siteCode(), null, task);
            created.add(task);
        }
        return List.copyOf(created);
    }

    @Transactional
    public SetupTask resolve(BookingCommands.ResolveSetupTask command) {
        ActorContext actor = command.actor();
        SetupTask task = bookings.findSetupTask(command.taskId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Setup task",
                        command.taskId()));
        authorization.require(actor, SflPermission.FACILITIES_SETUP_TASK_MANAGE, task.siteCode(),
                command.channel(), "SetupTask", task.id().toString());

        SetupTask resolved = bookings.saveSetupTask(task.resolve(command.outcome(), command.notes(),
                actor.actorId(), clock.instant()));
        audit.record(actor, command.channel(), AuditAction.BOOKING_SETUP_TASK_RESOLVED, "SetupTask",
                resolved.id().toString(), resolved.siteCode(), task, resolved);
        return resolved;
    }

    /** The turnaround queue: everything still to do before a room is needed, most urgent first. */
    @Transactional(readOnly = true)
    public List<SetupTask> queue(String siteCode, Instant dueBefore, int limit, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BOOKING_READ, channel, "SetupTask", "list",
                siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "SetupTask");
        Instant horizon = dueBefore == null ? clock.instant().plus(java.time.Duration.ofDays(2)) : dueBefore;
        return authorization.filterBySite(actor,
                bookings.findPendingSetupTasks(siteCode, horizon, limit), SetupTask::siteCode);
    }
}
