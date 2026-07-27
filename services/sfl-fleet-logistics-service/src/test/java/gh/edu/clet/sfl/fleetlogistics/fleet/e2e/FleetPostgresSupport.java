package gh.edu.clet.sfl.fleetlogistics.fleet.e2e;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Resolves a real PostgreSQL for the end-to-end suite.
 *
 * <p>Testcontainers' Docker auto-detection is not reliable everywhere — Docker Desktop 29.x rejects the
 * API version docker-java negotiates, so {@code disabledWithoutDocker} silently skips even when Docker is
 * running and healthy. A suite that proves the Flyway schema, the exclusion constraints and the audit
 * triggers is too important to be switched off by a client-library quirk, so the database is resolved in
 * three steps:
 *
 * <ol>
 *   <li><strong>An externally supplied database</strong> — {@code SFL_FLEET_LOGISTICS_TEST_DB_URL}
 *       (preferred) or {@code SFL_TEST_DB_URL} (env var or system property). This is what CI service
 *       containers provide, and what a developer can point at a {@code docker run postgres} started by
 *       the Docker CLI.</li>
 *   <li><strong>Testcontainers</strong> — used when Docker auto-detection succeeds.</li>
 *   <li><strong>Skip</strong> — with a message naming both escape hatches, so a skipped run is never
 *       mistaken for a passing one.</li>
 * </ol>
 *
 * @see <a href="file:../../../../../../../../docs/fleet/S166_Operations_And_Verification_Guide.md">
 *      the operations guide for the exact commands</a>
 */
public abstract class FleetPostgresSupport {

    static final String URL_PROPERTY = "SFL_FLEET_LOGISTICS_TEST_DB_URL";
    static final String FALLBACK_URL_PROPERTY = "SFL_TEST_DB_URL";
    static final String USERNAME_PROPERTY = "SFL_TEST_DB_USERNAME";
    static final String PASSWORD_PROPERTY = "SFL_TEST_DB_PASSWORD";
    private static final String IMAGE = "postgres:16-bookworm";

    private static final ResolvedDatabase DATABASE = resolve();

    /**
     * Whether a PostgreSQL is reachable. Referenced by {@code @EnabledIf} on the suites so the whole
     * class is skipped — with a reason — rather than failing one assertion at a time.
     */
    public static boolean databaseAvailable() {
        return DATABASE != null;
    }

    /** Explains, for a skipped run, what to do about it. */
    public static String unavailableReason() {
        return "No PostgreSQL available. Either set " + URL_PROPERTY
                + " (for example jdbc:postgresql://localhost:55443/sfl__fleet_vehicle_service_e2e), set "
                + FALLBACK_URL_PROPERTY + ", or make Docker reachable to Testcontainers. "
                + "See docs/fleet/S166_Operations_And_Verification_Guide.md.";
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (DATABASE == null) {
            return;
        }
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    private static ResolvedDatabase resolve() {
        ResolvedDatabase external = fromEnvironment();
        if (external != null) {
            return external;
        }
        return fromTestcontainers();
    }

    private static ResolvedDatabase fromEnvironment() {
        String url = property(URL_PROPERTY);
        if (url == null || url.isBlank()) {
            url = property(FALLBACK_URL_PROPERTY);
        }
        if (url == null || url.isBlank()) {
            return null;
        }
        String username = property(USERNAME_PROPERTY);
        String password = property(PASSWORD_PROPERTY);
        return new ResolvedDatabase(url, username == null ? "sfl" : username,
                password == null ? "sfl" : password);
    }

    private static ResolvedDatabase fromTestcontainers() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return null;
            }
            @SuppressWarnings("resource")
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName("sfl__fleet_vehicle_service_e2e")
                    .withUsername("sfl")
                    .withPassword("sfl");
            container.start();
            // Deliberately not stopped here: Testcontainers' Ryuk reaps it when the JVM exits, and the
            // container is shared by every scenario class in the suite.
            return new ResolvedDatabase(container.getJdbcUrl(), container.getUsername(),
                    container.getPassword());
        } catch (RuntimeException | LinkageError exception) {
            // Auto-detection failures must degrade to "skip", never to a broken static initialiser that
            // takes the whole test class down with an obscure error.
            return null;
        }
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value != null && !value.isBlank() ? value : System.getenv(name);
    }

    private record ResolvedDatabase(String url, String username, String password) {
    }

    /**
     * A clock the scenarios can wind forward.
     *
     * <p>SLA breach and dashboard staleness are both "what happens once time passes" behaviours. Driving
     * them from a mutable clock tests the real code path, where sleeping would make the suite slow and
     * flaky and mutating {@code sla_due_at} directly would test the fixture rather than the rule.
     */
    @TestConfiguration
    public static class MutableClockConfiguration {

        @Bean
        public MutableClock clock() {
            return new MutableClock(Instant.parse("2026-07-21T08:00:00Z"));
        }
    }

    /** A {@link Clock} whose instant the test controls. */
    public static final class MutableClock extends Clock {

        private Instant instant;

        public MutableClock(Instant instant) {
            this.instant = instant;
        }

        public void advanceBy(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        public void set(Instant newInstant) {
            this.instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
