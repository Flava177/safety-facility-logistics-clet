package gh.edu.clet.sfl.facilities.booking.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.ReadinessHoldReason;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.booking.domain.policy.ReadinessHoldPolicy;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "What is free?" — SRS-SFL-S159-02.
 *
 * <h2>Why unavailable spaces are returned rather than filtered out</h2>
 *
 * The obvious implementation returns only the free rooms. It produces a screen that is worse to use,
 * because the question behind "what is free at ten?" is usually "can I have Hall A at ten?", and a
 * hall that is simply absent from the list tells the asker nothing about why.
 *
 * <p>So every candidate space comes back with a verdict attached: free, held by a named booking, or
 * refused by readiness with the reason. The caller decides what to show. It also means the one query
 * serves both the availability picker and the refusal message a request produces, so the two cannot
 * disagree about whether a hall was available.
 *
 * <h2>What this deliberately does not do</h2>
 *
 * It does not reserve anything. Two people can both be told Hall A is free and both request it; the
 * first wins and the second is refused by the exclusion constraint. Holding a space during a
 * five-minute browse would mean the estate's diary was mostly locked by people who wandered off.
 */
@Service
public class BookingAvailabilityService {

    /** One space, and whether this window can have it. */
    public record SpaceAvailability(
            FacilityRoom room,
            boolean free,
            ReadinessHoldReason readinessIssue,
            String readinessDetail,
            List<Booking> heldBy) {

        /** {@code true} when the space could be booked by somebody holding the override permission. */
        public boolean availableWithOverride() {
            return heldBy.isEmpty() && readinessIssue != null;
        }
    }

    /** One resource, and how much of it this window can have. */
    public record ResourceAvailability(BookableResource resource, int committed, int free) {
    }

    private final BookingRepository bookings;
    private final FacilitiesRepository facilities;
    private final FacilitiesAuthorization authorization;

    public BookingAvailabilityService(BookingRepository bookings, FacilitiesRepository facilities,
            FacilitiesAuthorization authorization) {
        this.bookings = bookings;
        this.facilities = facilities;
        this.authorization = authorization;
    }

    /**
     * Every candidate space at a site for a window, with a verdict.
     *
     * @param minimumCapacity filters out rooms too small to hold the party; a room with no recorded
     *        capacity is kept, because "unknown" is not "too small"
     */
    @Transactional(readOnly = true)
    public List<SpaceAvailability> spaces(String siteCode, BookingWindow window, BookingPurpose purpose,
            SpaceType spaceType, Integer minimumCapacity, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_BOOKING_READ, siteCode, channel, "Booking",
                "availability");
        BookingWindow occupied = window.occupied();

        List<FacilityRoom> candidates = facilities.searchRooms(new FacilitiesRepository.RoomQuery(
                siteCode, null, null, spaceType, null, Boolean.TRUE, null, 0, 500)).items();
        Set<UUID> held = new HashSet<>(bookings.findHeldRoomIds(siteCode, occupied.start(), occupied.end()));

        List<SpaceAvailability> availability = new ArrayList<>();
        for (FacilityRoom room : candidates) {
            if (minimumCapacity != null && room.capacity() != null && room.capacity() < minimumCapacity) {
                continue;
            }
            ReadinessHoldReason issue = ReadinessHoldPolicy.holdFor(purpose, room.readinessStatus(),
                    room.bookable(), room.examinationCapable(), room.lifecycleStatus().isOperational(),
                    room.readinessLocked());
            List<Booking> holders = held.contains(room.id())
                    ? bookings.findHoldingBookings(room.id(), occupied.start(), occupied.end(), null)
                    : List.of();
            availability.add(new SpaceAvailability(room, issue == null && holders.isEmpty(), issue,
                    issue == null ? null : ReadinessHoldPolicy.explain(issue, room.roomCode()), holders));
        }
        return List.copyOf(availability);
    }

    /** Every bookable resource at a site, with how much of it is already committed for a window. */
    @Transactional(readOnly = true)
    public List<ResourceAvailability> resources(String siteCode, BookingWindow window,
            ResourceCategory category, ActorContext actor, SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_RESOURCE_READ, siteCode, channel,
                "BookableResource", "availability");
        BookingWindow occupied = window.occupied();

        List<BookableResource> candidates = bookings.findResources(siteCode, category).stream()
                .filter(BookableResource::isAllocatable)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<ResourceAllocation> live = bookings.findLiveAllocations(
                candidates.stream().map(BookableResource::id).toList(), occupied.start(), occupied.end());

        List<ResourceAvailability> availability = new ArrayList<>();
        for (BookableResource resource : candidates) {
            int committed = live.stream()
                    .filter(ResourceAllocation::isLive)
                    .filter(allocation -> allocation.resourceId().equals(resource.id()))
                    .filter(allocation -> occupied.overlaps(allocation.window().occupied()))
                    .mapToInt(ResourceAllocation::quantity)
                    .sum();
            // Clamped at zero. A quantity reduced below what is already allocated is a real situation
            // — see BookableResourceService.update — and reporting "-5 free" helps nobody.
            availability.add(new ResourceAvailability(resource, committed,
                    Math.max(0, resource.quantity() - committed)));
        }
        return List.copyOf(availability);
    }

    /** Everything holding one space between two instants — the room diary. */
    @Transactional(readOnly = true)
    public List<Booking> calendar(UUID roomId, BookingWindow window, ActorContext actor,
            SourceChannel channel) {
        FacilityRoom room = facilities.findRoom(roomId)
                .orElseThrow(() -> new gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException
                        .RecordNotFoundException("Space", roomId));
        authorization.require(actor, SflPermission.FACILITIES_BOOKING_READ, room.siteCode(), channel,
                "Booking", "calendar");
        return bookings.findHoldingBookings(roomId, window.start(), window.end(), null);
    }
}
