package gh.edu.clet.sfl.facilities.booking.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The conflict rules, as arithmetic, without a database or a service. */
class BookingConflictPolicyTest {

    private static final Instant NINE = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant TEN = Instant.parse("2026-08-03T10:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-08-03T11:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-08-03T12:00:00Z");

    private static final UUID HALL = UUID.randomUUID();
    private static final UUID OTHER_HALL = UUID.randomUUID();

    @Nested
    @DisplayName("Space conflicts")
    class Spaces {

        @Test
        void an_overlapping_booking_that_holds_the_space_is_a_conflict() {
            Booking existing = booking(HALL, BookingWindow.of(NINE, ELEVEN));

            List<BookingConflictPolicy.Conflict> conflicts = BookingConflictPolicy.spaceConflicts(
                    BookingWindow.of(TEN, TWELVE), HALL, "HALL-A", null, List.of(existing));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.get(0).kind()).isEqualTo(BookingConflictPolicy.ConflictKind.SPACE);
            // The message names the booking that has the hall, which is the requester's next action.
            assertThat(conflicts.get(0).describe())
                    .contains("HALL-A", existing.bookingReference(), "09:00", "11:00");
        }

        @Test
        void a_back_to_back_booking_is_not_a_conflict() {
            assertThat(BookingConflictPolicy.spaceConflicts(BookingWindow.of(TEN, ELEVEN), HALL, "HALL-A",
                    null, List.of(booking(HALL, BookingWindow.of(NINE, TEN))))).isEmpty();
        }

        @Test
        void a_booking_in_another_space_is_not_a_conflict() {
            assertThat(BookingConflictPolicy.spaceConflicts(BookingWindow.of(NINE, TWELVE), HALL, "HALL-A",
                    null, List.of(booking(OTHER_HALL, BookingWindow.of(NINE, TWELVE))))).isEmpty();
        }

        /** Moving a booking must not clash with where it currently is. */
        @Test
        void a_booking_does_not_conflict_with_itself() {
            Booking existing = booking(HALL, BookingWindow.of(NINE, ELEVEN));

            assertThat(BookingConflictPolicy.spaceConflicts(BookingWindow.of(TEN, TWELVE), HALL, "HALL-A",
                    existing.id(), List.of(existing))).isEmpty();
        }

        @Test
        void a_cancelled_booking_does_not_hold_the_space() {
            Booking cancelled = booking(HALL, BookingWindow.of(NINE, ELEVEN))
                    .cancel("Lecturer unwell", "manager", NINE, SourceChannel.WEB, null);

            assertThat(BookingConflictPolicy.spaceConflicts(BookingWindow.of(TEN, TWELVE), HALL, "HALL-A",
                    null, List.of(cancelled))).isEmpty();
        }

        /** The buffers are what conflict is tested on, and this is why. */
        @Test
        void a_teardown_buffer_creates_a_conflict_the_booked_windows_do_not_show() {
            Booking examination = booking(HALL, new BookingWindow(NINE, TEN, 0, 30));

            assertThat(BookingConflictPolicy.spaceConflicts(BookingWindow.of(TEN, ELEVEN), HALL, "HALL-A",
                    null, List.of(examination))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Resource conflicts")
    class Resources {

        @Test
        void an_exclusive_resource_committed_elsewhere_is_a_conflict_naming_it() {
            BookableResource projector = resource("PROJ-01", 1);
            ResourceAllocation held = allocation(projector, BookingWindow.of(NINE, ELEVEN), 1);

            List<BookingConflictPolicy.Conflict> conflicts = BookingConflictPolicy.resourceConflicts(
                    BookingWindow.of(TEN, TWELVE), null, Map.of(projector.id(), 1),
                    List.of(projector), List.of(held));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.get(0).describe()).contains("PROJ-01");
        }

        @Test
        void a_pool_with_enough_left_is_not_a_conflict() {
            BookableResource chairs = resource("CHAIRS", 40);
            ResourceAllocation held = allocation(chairs, BookingWindow.of(NINE, ELEVEN), 25);

            assertThat(BookingConflictPolicy.resourceConflicts(BookingWindow.of(TEN, TWELVE), null,
                    Map.of(chairs.id(), 15), List.of(chairs), List.of(held))).isEmpty();
        }

        @Test
        void a_pool_oversubscribed_reports_the_shortfall_rather_than_a_booking() {
            BookableResource chairs = resource("CHAIRS", 40);
            ResourceAllocation held = allocation(chairs, BookingWindow.of(NINE, ELEVEN), 25);

            List<BookingConflictPolicy.Conflict> conflicts = BookingConflictPolicy.resourceConflicts(
                    BookingWindow.of(TEN, TWELVE), null, Map.of(chairs.id(), 20),
                    List.of(chairs), List.of(held));

            assertThat(conflicts).hasSize(1);
            assertThat(conflicts.get(0).available()).isEqualTo(15);
            assertThat(conflicts.get(0).describe()).contains("Only 15", "20 were requested");
        }

        @Test
        void allocations_that_do_not_overlap_do_not_count_against_the_pool() {
            BookableResource chairs = resource("CHAIRS", 40);
            ResourceAllocation earlier = allocation(chairs, BookingWindow.of(NINE, TEN), 40);

            assertThat(BookingConflictPolicy.resourceConflicts(BookingWindow.of(TEN, TWELVE), null,
                    Map.of(chairs.id(), 40), List.of(chairs), List.of(earlier))).isEmpty();
        }

        @Test
        void a_released_allocation_frees_what_it_held() {
            BookableResource projector = resource("PROJ-01", 1);
            ResourceAllocation released = allocation(projector, BookingWindow.of(NINE, ELEVEN), 1).release();

            assertThat(BookingConflictPolicy.resourceConflicts(BookingWindow.of(TEN, TWELVE), null,
                    Map.of(projector.id(), 1), List.of(projector), List.of(released))).isEmpty();
        }

        @Test
        void a_bookings_own_allocations_do_not_block_it_being_moved() {
            BookableResource projector = resource("PROJ-01", 1);
            ResourceAllocation mine = allocation(projector, BookingWindow.of(NINE, ELEVEN), 1);

            assertThat(BookingConflictPolicy.resourceConflicts(BookingWindow.of(TEN, TWELVE),
                    mine.bookingId(), Map.of(projector.id(), 1), List.of(projector), List.of(mine)))
                    .isEmpty();
        }

        @Test
        void nothing_requested_means_nothing_to_conflict_with() {
            assertThat(BookingConflictPolicy.resourceConflicts(BookingWindow.of(TEN, TWELVE), null, Map.of(),
                    List.of(), List.of())).isEmpty();
        }
    }

    // ---- fixtures -----------------------------------------------------------------------------

    private static int reference = 0;

    private static Booking booking(UUID roomId, BookingWindow window) {
        reference++;
        return Booking.request(UUID.randomUUID(), String.format("BK-MAIN-%06d", reference), "MAIN", roomId,
                "HALL-A", BookingPurpose.LECTURE, "Contract law", null, window, 60, null, false, null,
                "lecturer", NINE, SourceChannel.WEB, null);
    }

    private static BookableResource resource(String code, int quantity) {
        return BookableResource.register(UUID.randomUUID(), "MAIN", code, code, ResourceCategory.OTHER,
                null, quantity, null, null, false, "manager", NINE, SourceChannel.WEB, null);
    }

    private static ResourceAllocation allocation(BookableResource resource, BookingWindow window,
            int quantity) {
        return ResourceAllocation.allocate(UUID.randomUUID(), booking(HALL, window), resource, quantity,
                "manager", NINE);
    }
}
