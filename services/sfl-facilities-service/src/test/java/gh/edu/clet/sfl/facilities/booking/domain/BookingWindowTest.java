package gh.edu.clet.sfl.facilities.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The half-open interval rule, which the whole module turns on.
 *
 * <p>These are the cheapest tests in S159 and the ones most worth having. Every conflict decision in
 * the service, every availability answer, and the {@code tstzrange(..., '[)')} in the exclusion
 * constraint are three expressions of what this class does in four lines — and the two failure modes
 * are opposite and both bad. Treat the ends as closed and every back-to-back lecture reports a
 * phantom clash; treat them as open and the hall is double-booked on the hour.
 */
class BookingWindowTest {

    private static final Instant NINE = Instant.parse("2026-08-03T09:00:00Z");
    private static final Instant TEN = Instant.parse("2026-08-03T10:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-08-03T11:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-08-03T12:00:00Z");

    @Nested
    @DisplayName("Half-open overlap")
    class HalfOpen {

        @Test
        void back_to_back_bookings_do_not_overlap() {
            assertThat(BookingWindow.of(NINE, TEN).overlaps(BookingWindow.of(TEN, ELEVEN))).isFalse();
        }

        @Test
        void overlap_is_symmetric() {
            BookingWindow morning = BookingWindow.of(NINE, ELEVEN);
            BookingWindow late = BookingWindow.of(TEN, TWELVE);

            assertThat(morning.overlaps(late)).isTrue();
            assertThat(late.overlaps(morning)).isTrue();
        }

        @Test
        void a_booking_inside_another_overlaps_it() {
            assertThat(BookingWindow.of(NINE, TWELVE).overlaps(BookingWindow.of(TEN, ELEVEN))).isTrue();
        }

        @Test
        void a_window_overlaps_itself() {
            BookingWindow window = BookingWindow.of(NINE, ELEVEN);

            assertThat(window.overlaps(window)).isTrue();
        }

        @Test
        void a_booking_sharing_only_an_instant_at_the_far_end_does_not_overlap() {
            assertThat(BookingWindow.of(TEN, ELEVEN).overlaps(BookingWindow.of(NINE, TEN))).isFalse();
        }
    }

    @Nested
    @DisplayName("Setup and teardown widen the occupancy")
    class Buffers {

        @Test
        void the_occupied_window_includes_both_buffers() {
            BookingWindow examination = new BookingWindow(TEN, ELEVEN, 30, 30);

            assertThat(examination.occupied().start()).isEqualTo(Instant.parse("2026-08-03T09:30:00Z"));
            assertThat(examination.occupied().end()).isEqualTo(Instant.parse("2026-08-03T11:30:00Z"));
        }

        @Test
        void the_occupied_window_carries_no_buffers_of_its_own() {
            BookingWindow occupied = new BookingWindow(TEN, ELEVEN, 30, 30).occupied();

            assertThat(occupied.setupMinutes()).isZero();
            assertThat(occupied.teardownMinutes()).isZero();
            assertThat(occupied.occupied()).isEqualTo(occupied);
        }

        /**
         * The case the buffers exist for: two bookings that do not overlap as booked, and do once the
         * room has to be re-laid between them.
         */
        @Test
        void a_booking_that_looks_free_clashes_once_the_teardown_is_counted() {
            BookingWindow examination = new BookingWindow(NINE, TEN, 0, 30);
            BookingWindow lecture = BookingWindow.of(TEN, ELEVEN);

            assertThat(examination.overlaps(lecture)).isFalse();
            assertThat(examination.occupied().overlaps(lecture.occupied())).isTrue();
        }

        @Test
        void a_window_without_buffers_is_its_own_occupancy() {
            BookingWindow window = BookingWindow.of(NINE, TEN);

            assertThat(window.occupied()).isSameAs(window);
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void a_booking_must_end_after_it_starts() {
            assertThatThrownBy(() -> BookingWindow.of(TEN, NINE))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("must end after it starts");
        }

        /** A zero-length booking occupies nothing and cannot conflict, so it would sit there lying. */
        @Test
        void a_zero_length_booking_is_refused() {
            assertThatThrownBy(() -> BookingWindow.of(TEN, TEN))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class);
        }

        @Test
        void a_booking_longer_than_a_fortnight_is_a_data_entry_error() {
            assertThatThrownBy(() -> BookingWindow.of(NINE, NINE.plus(Duration.ofDays(15))))
                    .isInstanceOf(FacilitiesException.ValidationFailedException.class)
                    .hasMessageContaining("14 days");
        }

        @Test
        void negative_buffers_are_refused() {
            assertThatThrownBy(() -> new BookingWindow(NINE, TEN, -5, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Time relative to a window")
    class Timing {

        @Test
        void a_window_has_passed_at_its_own_end() {
            assertThat(BookingWindow.of(NINE, TEN).hasPassed(TEN)).isTrue();
            assertThat(BookingWindow.of(NINE, TEN).hasPassed(TEN.minusSeconds(1))).isFalse();
        }

        @Test
        void a_window_is_running_from_its_start_up_to_but_not_including_its_end() {
            BookingWindow window = BookingWindow.of(NINE, TEN);

            assertThat(window.isRunningAt(NINE)).isTrue();
            assertThat(window.isRunningAt(TEN.minusSeconds(1))).isTrue();
            assertThat(window.isRunningAt(TEN)).isFalse();
            assertThat(window.isRunningAt(NINE.minusSeconds(1))).isFalse();
        }
    }
}
