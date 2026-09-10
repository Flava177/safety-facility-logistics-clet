package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The bounded {@code pg_advisory_xact_lock} wait, against a real PostgreSQL.
 *
 * <p>{@code lockSpace}/{@code lockResources} used to block indefinitely: a burst of concurrent
 * requests for the same popular room would each hold a HikariCP connection, queued forever behind
 * whichever request got there first, until the whole pool was exhausted and unrelated reads on the
 * same service started failing too. This pins the fix: a contending request gives up with the same
 * {@link FacilitiesException.BookingConflictException} / HTTP 409 a losing writer already gets, and the
 * connection it held comes back to the pool rather than staying wedged.
 */
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
@SpringBootTest(properties = {"sfl.security.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
        "sfl.facilities.booking.advisory-lock-timeout=PT0.3S"})
class AdvisoryLockTimeoutIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @Autowired private FacilitiesRepository facilities;
    @Autowired private BookingRepository bookings;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    private TransactionTemplate transactions;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        transactions = new TransactionTemplate(transactionManager);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String siteCode = "LK" + suffix;
        Instant now = Instant.now();
        Site site = facilities.saveSite(Site.create(UUID.randomUUID(), siteCode, "Advisory Lock Test Site", null,
                "tester", now, SourceChannel.WEB, "corr-lock-test"));
        Building building = facilities.saveBuilding(Building.create(UUID.randomUUID(), site.id(), siteCode,
                "B1", "Building 1", null, "tester", now, SourceChannel.WEB, "corr-lock-test"));
        FacilityFloor floor = facilities.saveFloor(FacilityFloor.create(UUID.randomUUID(), building.id(),
                siteCode, "F1", "Floor 1", 1, "tester", now, SourceChannel.WEB, "corr-lock-test"));
        FacilityRoom room = facilities.saveRoom(FacilityRoom.create(UUID.randomUUID(), floor.id(), siteCode,
                "R1", "Room 1", SpaceType.MEETING_ROOM, 20, null, null, true, false, "tester", now,
                SourceChannel.WEB, "corr-lock-test"));
        roomId = room.id();
    }

    @Test
    void a_contender_gives_up_with_a_conflict_instead_of_blocking_forever_and_the_pool_recovers()
            throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        Thread holder = new Thread(() -> transactions.execute(status -> {
            bookings.lockSpace(roomId);
            lockHeld.countDown();
            await(releaseLock);
            return null;
        }));
        holder.start();
        assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

        try {
            // Several concurrent contenders, not just one - proof this is a pool-recovery guarantee,
            // not a coincidence of a single lucky retry.
            ExecutorService contenders = Executors.newFixedThreadPool(3);
            try {
                Instant start = Instant.now();
                List<Future<Object>> attempts = contenders.invokeAll(List.of(
                        () -> attemptLock(),
                        () -> attemptLock(),
                        () -> attemptLock()));
                Duration elapsed = Duration.between(start, Instant.now());

                // Each must have failed with the domain conflict, not a raw driver/lock exception, and
                // all three must have given up within a small multiple of the 300ms bound rather than
                // waiting for the holder (which does not release for the whole test).
                for (Future<Object> attempt : attempts) {
                    assertThatThrownBy(attempt::get)
                            .hasCauseInstanceOf(FacilitiesException.BookingConflictException.class);
                }
                assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
            } finally {
                contenders.shutdownNow();
            }
        } finally {
            releaseLock.countDown();
            holder.join(TimeUnit.SECONDS.toMillis(5));
        }

        // The pool recovered: a fresh acquisition succeeds promptly now that the holder has committed,
        // proving none of the timed-out contenders left a connection stuck against this lock.
        transactions.execute(status -> {
            bookings.lockSpace(roomId);
            return null;
        });
    }

    private Object attemptLock() {
        return transactions.execute(status -> {
            bookings.lockSpace(roomId);
            return null;
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test latch was never released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
