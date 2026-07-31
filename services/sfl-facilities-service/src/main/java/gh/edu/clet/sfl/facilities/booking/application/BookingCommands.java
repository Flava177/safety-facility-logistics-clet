package gh.edu.clet.sfl.facilities.booking.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTaskStatus;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The booking module's use cases, as data.
 *
 * <p>Commands rather than long argument lists, for the reason S153 found the hard way: a service
 * method taking eleven parameters is one where two of the same type can be swapped at a call site and
 * nothing complains. Each record carries its actor, its channel and — where the operation creates
 * state — its idempotency key.
 *
 * <p>{@code expectedVersion} is nullable throughout. A caller that supplies it gets optimistic-lock
 * semantics; one that does not accepts last-write-wins. Forcing it on every command would break the
 * simple screens for no gain, and a caller that cares can always opt in.
 */
public final class BookingCommands {

    private BookingCommands() {
    }

    /**
     * Request a booking.
     *
     * @param setupMinutes null means "use the site's default for this purpose"
     * @param resources resource id to quantity, allocated in the same transaction so a booking never
     *        exists briefly without the projector it was made for
     * @param overrideReason required, and only honoured, when the space's readiness would otherwise
     *        refuse the booking and the actor holds {@code FACILITIES_BOOKING_OVERRIDE}
     */
    public record RequestBooking(
            UUID roomId,
            BookingPurpose purpose,
            String title,
            String description,
            Instant startsAt,
            Instant endsAt,
            Integer setupMinutes,
            Integer teardownMinutes,
            int expectedAttendees,
            String requestedFor,
            Map<UUID, Integer> resources,
            String overrideReason,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey,
            Object idempotencyPayload) {
    }

    /** Approve or reject. The reason is mandatory on a rejection and optional on an approval. */
    public record DecideBooking(
            UUID bookingId,
            boolean approve,
            String reason,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    /** Move a booking. Its allocations move with it, in the same transaction. */
    public record RescheduleBooking(
            UUID bookingId,
            Instant startsAt,
            Instant endsAt,
            Integer setupMinutes,
            Integer teardownMinutes,
            String overrideReason,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    /** Start or complete. One command because the guards are identical. */
    public record TransitionBooking(
            UUID bookingId,
            Transition transition,
            String notes,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {

        public enum Transition {
            /** Somebody arrived and took the room. */
            START,
            /** It ran and finished. */
            COMPLETE
        }
    }

    public record CancelBooking(
            UUID bookingId,
            String reason,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- resources ------------------------------------------------------------------------------

    public record RegisterResource(
            String siteCode,
            String resourceCode,
            String name,
            ResourceCategory category,
            String description,
            int quantity,
            UUID homeRoomId,
            UUID assetId,
            boolean requiresSetup,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey,
            Object idempotencyPayload) {
    }

    public record UpdateResource(
            UUID resourceId,
            String name,
            String description,
            Integer quantity,
            UUID homeRoomId,
            Boolean requiresSetup,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ChangeResourceLifecycle(
            UUID resourceId,
            RecordLifecycleStatus target,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    /** Add resources to a booking that already exists. */
    public record AllocateResources(
            UUID bookingId,
            Map<UUID, Integer> resources,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ReleaseAllocation(
            UUID bookingId,
            UUID allocationId,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- setup tasks ----------------------------------------------------------------------------

    public record CreateSetupTasks(
            UUID bookingId,
            List<NewSetupTask> tasks,
            ActorContext actor,
            SourceChannel channel) {

        public record NewSetupTask(String description, Instant dueBy, String assignedTo) {
        }
    }

    public record ResolveSetupTask(
            UUID taskId,
            SetupTaskStatus outcome,
            String notes,
            ActorContext actor,
            SourceChannel channel) {
    }
}
