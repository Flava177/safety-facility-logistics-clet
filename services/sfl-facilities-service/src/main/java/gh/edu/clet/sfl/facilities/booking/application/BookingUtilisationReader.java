package gh.edu.clet.sfl.facilities.booking.application;

import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.shared.application.port.RepositoryPage;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How the site's rooms were actually used over a period - the S159 side of SRS-SFL-S158-03.
 *
 * <p>S158 space planning pulls this on a schedule and compares it with what was planned. The contract
 * is deliberately <strong>read-only</strong>, and that is the validation rule the requirement states in
 * so many words: "S158 never writes back into S159's booking records". There is no method here that
 * could, so the rule does not depend on anybody remembering it.
 *
 * <h2>No authorisation on this class, and why that is not a gap</h2>
 *
 * <p>This is an in-process contract, called by the S158 scheduler and S158's own application service
 * after they have authorised their own caller. It returns counts and minutes per room - no requester,
 * no title, no attendee - so there is nothing in it a planning officer holding
 * {@code FACILITIES_SPACE_PLAN_READ} should not see. Row-level security still narrows it to the
 * transaction's site scope underneath.
 *
 * <h2>What counts as use</h2>
 *
 * <p>A booking that was confirmed and then taken up ({@code IN_USE} or {@code COMPLETED}) is use. A
 * {@code NO_SHOW} is counted separately rather than dropped: a room booked every morning and never
 * entered is the single most useful planning signal S159 has, and folding it into "booked" would hide
 * it. Requests, rejections and cancellations never held the space for anyone and are excluded.
 */
@Service
public class BookingUtilisationReader {

    /** Paged rather than read in one statement - a term's diary for a large site is thousands of rows. */
    private static final int PAGE_SIZE = 200;

    private final BookingRepository bookings;

    public BookingUtilisationReader(BookingRepository bookings) {
        this.bookings = bookings;
    }

    /**
     * Utilisation per room at one site over {@code [from, to)}.
     *
     * @return one entry per room that had any booking in the period. A room with none is absent, and
     *         the caller - which knows the full room list from S152 - reads absence as zero use.
     */
    @Transactional(readOnly = true)
    public List<RoomUtilisation> utilisation(String siteCode, Instant from, Instant to) {
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(to, "to is required");
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("The utilisation period must end after it starts.");
        }

        Map<UUID, Accumulator> byRoom = new LinkedHashMap<>();
        int page = 0;
        while (true) {
            RepositoryPage<Booking> found = bookings.findBookings(new BookingRepository.BookingQuery(
                    siteCode, null, null, null, null, from, to, null, null, page, PAGE_SIZE));
            for (Booking booking : found.items()) {
                if (!countsTowardsUse(booking.status())) {
                    continue;
                }
                byRoom.computeIfAbsent(booking.roomId(), id -> new Accumulator(booking.roomCode()))
                        .add(booking, from, to);
            }
            if (found.items().size() < PAGE_SIZE || (long) (page + 1) * PAGE_SIZE >= found.totalElements()) {
                break;
            }
            page++;
        }
        return byRoom.entrySet().stream()
                .map(entry -> entry.getValue().toUtilisation(entry.getKey(), siteCode, from, to))
                .toList();
    }

    private static boolean countsTowardsUse(BookingStatus status) {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.IN_USE
                || status == BookingStatus.COMPLETED || status == BookingStatus.NO_SHOW;
    }

    /**
     * One room's use over the period.
     *
     * @param bookedMinutes minutes of the period covered by bookings that held the room, clipped to the
     *        period - a booking straddling the boundary counts only its inside part
     * @param usedMinutes the part of {@code bookedMinutes} whose booking was actually taken up
     * @param peakExpectedAttendees the largest single booking's expected attendance, for comparing with
     *        the room's capacity
     * @param totalExpectedAttendees summed over taken-up bookings, for an average against capacity
     */
    public record RoomUtilisation(
            UUID roomId,
            String roomCode,
            String siteCode,
            Instant periodStart,
            Instant periodEnd,
            int bookingCount,
            int takenUpCount,
            int noShowCount,
            long bookedMinutes,
            long usedMinutes,
            int peakExpectedAttendees,
            long totalExpectedAttendees) {
    }

    private static final class Accumulator {
        private final String roomCode;
        private int bookingCount;
        private int takenUp;
        private int noShows;
        private long bookedMinutes;
        private long usedMinutes;
        private int peakAttendees;
        private long totalAttendees;

        Accumulator(String roomCode) {
            this.roomCode = roomCode;
        }

        void add(Booking booking, Instant from, Instant to) {
            Instant start = booking.window().start().isBefore(from) ? from : booking.window().start();
            Instant end = booking.window().end().isAfter(to) ? to : booking.window().end();
            long minutes = end.isAfter(start) ? Duration.between(start, end).toMinutes() : 0;
            bookingCount++;
            bookedMinutes += minutes;
            if (booking.status() == BookingStatus.NO_SHOW) {
                noShows++;
                return;
            }
            if (booking.status() == BookingStatus.IN_USE || booking.status() == BookingStatus.COMPLETED) {
                takenUp++;
                usedMinutes += minutes;
                totalAttendees += booking.expectedAttendees();
            }
            peakAttendees = Math.max(peakAttendees, booking.expectedAttendees());
        }

        RoomUtilisation toUtilisation(UUID roomId, String siteCode, Instant from, Instant to) {
            return new RoomUtilisation(roomId, roomCode, siteCode, from, to, bookingCount, takenUp, noShows,
                    bookedMinutes, usedMinutes, peakAttendees, totalAttendees);
        }
    }
}
