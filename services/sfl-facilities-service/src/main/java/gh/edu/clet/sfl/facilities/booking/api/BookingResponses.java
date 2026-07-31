package gh.edu.clet.sfl.facilities.booking.api;

import gh.edu.clet.sfl.facilities.booking.application.BookingAvailabilityService;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.ApprovalDecision;
import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingApproval;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.booking.domain.NoShowRecord;
import gh.edu.clet.sfl.facilities.booking.domain.ReadinessHoldReason;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTask;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTaskStatus;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.Metadata;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The S159 wire types.
 *
 * <p>Each carries the derived facts a client would otherwise recompute — whether a booking holds its
 * space, whether it is on a readiness hold, when the room is actually occupied from. Those are
 * decisions this service has already made, and a client recomputing {@code occupiedFrom} from the
 * buffers is a client that will eventually disagree with the exclusion constraint about whether two
 * bookings clash.
 */
public final class BookingResponses {

    private BookingResponses() {
    }

    public record BookingResponse(
            UUID id,
            String bookingReference,
            String siteCode,
            UUID roomId,
            String roomCode,
            BookingPurpose purpose,
            String title,
            String description,
            Instant startsAt,
            Instant endsAt,
            int setupMinutes,
            int teardownMinutes,
            /** The booked window widened by the buffers. What conflict is actually tested on. */
            Instant occupiedFrom,
            Instant occupiedTo,
            BookingStatus status,
            boolean holdsTheSpace,
            int expectedAttendees,
            String requestedBy,
            String requestedFor,
            Instant requestedAt,
            boolean approvalRequired,
            UUID approvalId,
            Instant confirmedAt,
            Instant startedAt,
            Instant completedAt,
            String closureReason,
            ReadinessHoldReason readinessHoldReason,
            Instant readinessHeldAt,
            boolean overridden,
            String overrideReason,
            RecordLifecycleStatus lifecycleStatus,
            Metadata metadata) {

        public static BookingResponse from(Booking booking, Clock clock) {
            return new BookingResponse(booking.id(), booking.bookingReference(), booking.siteCode(),
                    booking.roomId(), booking.roomCode(), booking.purpose(), booking.title(),
                    booking.description(), booking.window().start(), booking.window().end(),
                    booking.window().setupMinutes(), booking.window().teardownMinutes(),
                    booking.window().occupied().start(), booking.window().occupied().end(),
                    booking.status(), booking.holdsTheSpace(), booking.expectedAttendees(),
                    booking.requestedBy(), booking.requestedFor(), booking.requestedAt(),
                    booking.approvalRequired(), booking.approvalId(), booking.confirmedAt(),
                    booking.startedAt(), booking.completedAt(), booking.closureReason(),
                    booking.readinessHoldReason(), booking.readinessHeldAt(), booking.wasOverridden(),
                    booking.overrideReason(), booking.lifecycleStatus(),
                    Metadata.from(booking.metadata()));
        }
    }

    public record ApprovalResponse(
            UUID id,
            UUID bookingId,
            ApprovalDecision decision,
            String reason,
            String decidedBy,
            Instant decidedAt) {

        public static ApprovalResponse from(BookingApproval approval) {
            return new ApprovalResponse(approval.id(), approval.bookingId(), approval.decision(),
                    approval.reason(), approval.decidedBy(), approval.decidedAt());
        }
    }

    public record AllocationResponse(
            UUID id,
            UUID bookingId,
            UUID resourceId,
            String resourceCode,
            Instant startsAt,
            Instant endsAt,
            Instant occupiedFrom,
            Instant occupiedTo,
            int quantity,
            boolean exclusive,
            boolean released,
            String allocatedBy,
            Instant allocatedAt) {

        public static AllocationResponse from(ResourceAllocation allocation) {
            return new AllocationResponse(allocation.id(), allocation.bookingId(), allocation.resourceId(),
                    allocation.resourceCode(), allocation.window().start(), allocation.window().end(),
                    allocation.window().occupied().start(), allocation.window().occupied().end(),
                    allocation.quantity(), allocation.exclusive(), allocation.releasedWithBooking(),
                    allocation.allocatedBy(), allocation.allocatedAt());
        }
    }

    public record SetupTaskResponse(
            UUID id,
            UUID bookingId,
            UUID roomId,
            String siteCode,
            String description,
            Instant dueBy,
            SetupTaskStatus status,
            boolean overdue,
            String assignedTo,
            String completedBy,
            Instant completedAt,
            String notes) {

        public static SetupTaskResponse from(SetupTask task, Clock clock) {
            return new SetupTaskResponse(task.id(), task.bookingId(), task.roomId(), task.siteCode(),
                    task.description(), task.dueBy(), task.status(), task.isOverdue(clock.instant()),
                    task.assignedTo(), task.completedBy(), task.completedAt(), task.notes());
        }
    }

    public record ResourceResponse(
            UUID id,
            String siteCode,
            String resourceCode,
            String name,
            ResourceCategory category,
            String description,
            int quantity,
            /** True when there is only one, so the database can enforce its exclusivity. */
            boolean exclusive,
            UUID homeRoomId,
            UUID assetId,
            boolean requiresSetup,
            RecordLifecycleStatus lifecycleStatus,
            Metadata metadata) {

        public static ResourceResponse from(BookableResource resource) {
            return new ResourceResponse(resource.id(), resource.siteCode(), resource.resourceCode(),
                    resource.name(), resource.category(), resource.description(), resource.quantity(),
                    resource.isExclusive(), resource.homeRoomId(), resource.assetId(),
                    resource.requiresSetup(), resource.lifecycleStatus(),
                    Metadata.from(resource.metadata()));
        }
    }

    public record NoShowResponse(
            UUID id,
            UUID bookingId,
            String bookingReference,
            String siteCode,
            UUID roomId,
            String roomCode,
            BookingPurpose purpose,
            Instant windowStart,
            Instant windowEnd,
            long minutesHeldUnused,
            String requestedBy,
            Instant recordedAt) {

        public static NoShowResponse from(NoShowRecord record) {
            return new NoShowResponse(record.id(), record.bookingId(), record.bookingReference(),
                    record.siteCode(), record.roomId(), record.roomCode(), record.purpose(),
                    record.windowStart(), record.windowEnd(), record.minutesHeldUnused(),
                    record.requestedBy(), record.recordedAt());
        }
    }

    /**
     * One space and whether this window can have it.
     *
     * <p>Unavailable spaces are returned rather than filtered out, with the reason attached. The
     * question behind "what is free at ten?" is usually "can I have Hall A at ten?", and a hall simply
     * absent from the list answers neither.
     */
    public record SpaceAvailabilityResponse(
            UUID roomId,
            String roomCode,
            String name,
            Integer capacity,
            LocationReadinessStatus readinessStatus,
            boolean free,
            boolean availableWithOverride,
            ReadinessHoldReason readinessIssue,
            String readinessDetail,
            List<BookingResponse> heldBy) {

        public static SpaceAvailabilityResponse from(BookingAvailabilityService.SpaceAvailability
                availability, Clock clock) {
            return new SpaceAvailabilityResponse(availability.room().id(), availability.room().roomCode(),
                    availability.room().name(), availability.room().capacity(),
                    availability.room().readinessStatus(), availability.free(),
                    availability.availableWithOverride(), availability.readinessIssue(),
                    availability.readinessDetail(),
                    availability.heldBy().stream().map(booking -> BookingResponse.from(booking, clock))
                            .toList());
        }
    }

    public record ResourceAvailabilityResponse(
            UUID resourceId,
            String resourceCode,
            String name,
            ResourceCategory category,
            int quantity,
            int committed,
            int free) {

        public static ResourceAvailabilityResponse from(
                BookingAvailabilityService.ResourceAvailability availability) {
            return new ResourceAvailabilityResponse(availability.resource().id(),
                    availability.resource().resourceCode(), availability.resource().name(),
                    availability.resource().category(), availability.resource().quantity(),
                    availability.committed(), availability.free());
        }
    }

    public record BookingCountsResponse(
            int upcoming,
            int awaitingApproval,
            int onReadinessHold,
            int recentNoShows) {

        public static BookingCountsResponse from(BookingRepository.BookingCounts counts) {
            return new BookingCountsResponse(counts.upcoming(), counts.awaitingApproval(),
                    counts.onReadinessHold(), counts.recentNoShows());
        }
    }
}
