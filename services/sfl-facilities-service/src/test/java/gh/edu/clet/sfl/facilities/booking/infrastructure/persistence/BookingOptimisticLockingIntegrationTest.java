package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.ReadinessHoldReason;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code Booking.withReadinessHold} against a real PostgreSQL.
 *
 * <p>It is the one mutator on a versioned aggregate that deliberately does not call
 * {@code RecordMetadata.modifiedBy} - see its own Javadoc - so the edit basis
 * {@link gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord#requireNotStale}
 * sees is the row's current version, unchanged, rather than one past it. A first version of that check
 * assumed every save was a one-past-current edit and rejected every readiness-hold placement and
 * clearing as stale; this pins the fix against the real adapter rather than trusting the reasoning.
 */
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
@SpringBootTest(properties = {"sfl.security.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
class BookingOptimisticLockingIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @Autowired
    private FacilitiesRepository facilities;
    @Autowired
    private BookingRepository bookings;

    private String siteCode;
    private UUID roomId;
    private String roomCode;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        siteCode = "BL" + suffix;
        Instant now = Instant.now();
        Site site = facilities.saveSite(Site.create(UUID.randomUUID(), siteCode, "Booking Lock Test Site", null,
                "tester", now, SourceChannel.WEB, "corr-booking-lock-test"));
        Building building = facilities.saveBuilding(Building.create(UUID.randomUUID(), site.id(), siteCode,
                "B1", "Building 1", null, "tester", now, SourceChannel.WEB, "corr-booking-lock-test"));
        FacilityFloor floor = facilities.saveFloor(FacilityFloor.create(UUID.randomUUID(), building.id(),
                siteCode, "F1", "Floor 1", 1, "tester", now, SourceChannel.WEB, "corr-booking-lock-test"));
        FacilityRoom room = facilities.saveRoom(FacilityRoom.create(UUID.randomUUID(), floor.id(), siteCode,
                "R1", "Room 1", SpaceType.MEETING_ROOM, 20, null, null, true, false, "tester", now,
                SourceChannel.WEB, "corr-booking-lock-test"));
        roomId = room.id();
        roomCode = room.roomCode();
    }

    @Test
    void placing_and_clearing_a_readiness_hold_never_collides_with_the_versioned_lock() {
        Instant now = Instant.now();
        BookingWindow window = BookingWindow.of(now.plus(Duration.ofDays(1)),
                now.plus(Duration.ofDays(1)).plus(Duration.ofHours(1)));
        String bookingReference = "BK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Booking requested = bookings.saveBooking(Booking.request(UUID.randomUUID(), bookingReference, siteCode,
                roomId, roomCode, BookingPurpose.MEETING, "Standing meeting", null, window, 5, null, false, null,
                "tester", now, SourceChannel.WEB, "corr-1"));
        assertThat(requested.metadata().version()).isZero();

        // The reconciliation sweep places a hold - never bumps the version by design. A failure here
        // is the regression this class exists to catch: requireNotStale once rejected every one of
        // these as if it were a stale write.
        Booking held = bookings.saveBooking(
                requested.withReadinessHold(ReadinessHoldReason.SPACE_BLOCKED, Instant.now()));
        assertThat(held.readinessHoldReason()).isEqualTo(ReadinessHoldReason.SPACE_BLOCKED);

        // And clearing it, read fresh, is the same shape again - not a special case.
        Booking cleared = bookings.saveBooking(held.withReadinessHold(null, Instant.now()));
        assertThat(cleared.readinessHoldReason()).isNull();

        // A real edit afterwards still bumps the version normally and is still protected: the
        // no-bump exception does not weaken the ordinary case.
        Booking cancelled = bookings.saveBooking(
                cleared.cancel("No longer needed", "tester", Instant.now(), SourceChannel.WEB, "corr-2"));
        assertThat(cancelled.metadata().version()).isEqualTo(cleared.metadata().version() + 1);
    }
}
