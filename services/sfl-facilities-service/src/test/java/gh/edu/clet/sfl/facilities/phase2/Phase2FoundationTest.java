package gh.edu.clet.sfl.facilities.phase2;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.booking.application.BookingCommands;
import gh.edu.clet.sfl.facilities.booking.application.BookingUtilisationReader;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingLifecycleObserver;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.CleaningRequirement;
import gh.edu.clet.sfl.facilities.maintenance.application.AutomatedWorkOrderIntake;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.support.IfimpTestHarness;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The shared pieces every Phase 2 IFIMP system stands on, proved once here so each system's own suite
 * can take them as given: the automated S153 intake, the S159 lifecycle observer and cleaning
 * requirement, and the read-only S159 utilisation reader.
 */
class Phase2FoundationTest {

    private IfimpTestHarness h;

    @BeforeEach
    void setUp() {
        h = new IfimpTestHarness();
    }

    @Nested
    @DisplayName("AutomatedWorkOrderIntake - the one door S156, S173 and S176 raise S153 work through")
    class Intake {

        @Test
        @DisplayName("raises a triaged fault and a work order carrying the origin and evidence reference")
        void raises_fault_and_work_order() {
            AutomatedWorkOrderIntake.RaisedWorkOrder raised = h.intake.raise(request("bms-alert:A1"));

            FacilityFault fault = h.maintenance.findFault(raised.faultId()).orElseThrow();
            assertThat(raised.workOrderNumber()).isNotBlank();
            assertThat(raised.isOpen()).isTrue();
            assertThat(fault.category()).isEqualTo("BMS_TELEMETRY");
            assertThat(fault.description()).contains("S156").contains("ALERT-1").contains("reading:R-99");
            assertThat(fault.reportedBy()).isEqualTo("system.ifimp-automation");
        }

        @Test
        @DisplayName("the same idempotency key returns the same work order rather than a second one")
        void replay_returns_the_same_work_order() {
            AutomatedWorkOrderIntake.RaisedWorkOrder first = h.intake.raise(request("bms-alert:A1"));
            AutomatedWorkOrderIntake.RaisedWorkOrder second = h.intake.raise(request("bms-alert:A1"));

            assertThat(second.workOrderId()).isEqualTo(first.workOrderId());
            assertThat(h.maintenance.findWorkOrderForFault(first.faultId())).isPresent();
        }

        @Test
        @DisplayName("find reports the live state of a work order it raised")
        void find_reports_state() {
            AutomatedWorkOrderIntake.RaisedWorkOrder raised = h.intake.raise(request("bms-alert:A2"));

            assertThat(h.intake.find(raised.workOrderId())).get()
                    .extracting(AutomatedWorkOrderIntake.RaisedWorkOrder::faultNumber)
                    .isEqualTo(raised.faultNumber());
        }

        private AutomatedWorkOrderIntake.AutomatedWorkOrderRequest request(String key) {
            return new AutomatedWorkOrderIntake.AutomatedWorkOrderRequest("MAIN", h.hall.id(), null, null,
                    "Hall A temperature above band", "Sustained breach for 15 minutes.", "BMS_TELEMETRY",
                    FaultPriority.HIGH, "S156", "ALERT-1", "reading:R-99", "engineer", null, null,
                    SourceChannel.SYSTEM, "corr-1", key);
        }
    }

    @Nested
    @DisplayName("S159 booking - the cleaning requirement and lifecycle observer S169 builds on")
    class BookingHooks {

        private final List<String> seen = new ArrayList<>();

        @BeforeEach
        void observe() {
            h.bookingObservers.add(new BookingLifecycleObserver() {
                @Override
                public void bookingConfirmed(Booking booking, ActorContext actor) {
                    seen.add("confirmed:" + booking.cleaningRequirement());
                }

                @Override
                public void bookingWithdrawn(Booking booking, String reason, ActorContext actor) {
                    seen.add("withdrawn:" + reason);
                }
            });
        }

        @Test
        @DisplayName("a confirmed booking carries its cleaning requirement to every observer")
        void confirmation_is_observed_with_the_requirement() {
            Booking booking = book(CleaningRequirement.TEARDOWN);

            assertThat(booking.cleaningRequirement()).isEqualTo(CleaningRequirement.TEARDOWN);
            assertThat(seen).containsExactly("confirmed:TEARDOWN");
        }

        @Test
        @DisplayName("a booking that says nothing about cleaning asked for none")
        void absent_requirement_is_none() {
            assertThat(book(null).cleaningRequirement()).isEqualTo(CleaningRequirement.NONE);
        }

        @Test
        @DisplayName("a cancellation is observed with the booking's own reason")
        void cancellation_is_observed() {
            Booking booking = book(CleaningRequirement.SETUP);
            h.bookings().cancel(new BookingCommands.CancelBooking(booking.id(), "Speaker unwell", null,
                    h.manager, SourceChannel.WEB));

            assertThat(seen).containsExactly("confirmed:SETUP", "withdrawn:Booking cancelled: Speaker unwell");
        }

        private Booking book(CleaningRequirement requirement) {
            Instant start = IfimpTestHarness.NOW.plus(Duration.ofDays(2));
            return h.bookings().request(new BookingCommands.RequestBooking(h.meetingRoom.id(),
                    BookingPurpose.MEETING, "Board prep", null, start, start.plus(Duration.ofHours(1)), 0, 0, 8,
                    null, Map.of(), null, requirement, h.manager, SourceChannel.WEB, null, null));
        }
    }

    @Nested
    @DisplayName("BookingUtilisationReader - S158-03's read-only view of S159")
    class Utilisation {

        @Test
        @DisplayName("counts confirmed bookings per room with minutes clipped to the period")
        void counts_and_clips() {
            Instant start = IfimpTestHarness.NOW.plus(Duration.ofDays(1));
            h.bookings().request(new BookingCommands.RequestBooking(h.meetingRoom.id(), BookingPurpose.MEETING,
                    "Stand-up", null, start, start.plus(Duration.ofHours(2)), 0, 0, 6, null, Map.of(), null, null,
                    h.manager, SourceChannel.WEB, null, null));

            List<BookingUtilisationReader.RoomUtilisation> found = h.utilisation.utilisation("MAIN", start,
                    start.plus(Duration.ofHours(1)));

            assertThat(found).singleElement().satisfies(room -> {
                assertThat(room.roomId()).isEqualTo(h.meetingRoom.id());
                assertThat(room.bookingCount()).isEqualTo(1);
                assertThat(room.bookedMinutes()).isEqualTo(60);
                assertThat(room.peakExpectedAttendees()).isEqualTo(6);
            });
        }

        @Test
        @DisplayName("a room nobody booked is absent, which the caller reads as zero use")
        void unbooked_room_is_absent() {
            assertThat(h.utilisation.utilisation("MAIN", IfimpTestHarness.NOW,
                    IfimpTestHarness.NOW.plus(Duration.ofDays(1)))).isEmpty();
        }
    }
}
