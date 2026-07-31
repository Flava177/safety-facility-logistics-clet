package gh.edu.clet.sfl.facilities;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Resolves the PostgreSQL the facilities integration tests run against.
 *
 * <p>Modelled on {@code FleetPostgresSupport}, and added for the same reason it exists there: the
 * migration suite used to be gated on {@code @Testcontainers(disabledWithoutDocker = true)}, which
 * asks whether the <em>Java</em> Docker client can reach the daemon. On Windows it frequently cannot
 * — the named-pipe transport fails even while {@code docker ps} works from a shell — so the twelve
 * tests that prove {@code V1..V10} apply and that Hibernate validates against them were skipped on
 * every run, in the one environment where they were most needed.
 *
 * <p>An external database is therefore tried first, so a developer or a CI job that already has a
 * PostgreSQL up can point at it:
 *
 * <pre>
 * SFL_FACILITIES_TEST_DB_URL=jdbc:postgresql://localhost:55441/sfl_facilities_migration_test
 * </pre>
 *
 * <p><strong>That database must be empty.</strong> The migration suite proves {@code V1..V10} apply
 * to a virgin schema and asserts absolute facts about the result — the audit chain sits at genesis,
 * the seeded configuration defaults are exactly what V5 wrote — and several of its cases insert rows
 * at fixed site codes. Pointed at a database that has already been used, it fails on duplicate keys
 * and on a chain that has moved, which says nothing about the migrations. Testcontainers handed it a
 * fresh database implicitly; an external one has to be recreated by the caller:
 *
 * <pre>
 * docker exec sfl-facilities-e2e-postgres psql -U sfl -d postgres \
 *   -c "DROP DATABASE IF EXISTS sfl_facilities_migration_test;" \
 *   -c "CREATE DATABASE sfl_facilities_migration_test OWNER sfl;"
 * </pre>
 *
 * <p>Do not point this at {@code sfl_facilities_service_e2e}: that database is shared with the
 * hand-driven verification runs and is never empty.
 *
 * <p>Testcontainers remains the fallback, so nothing changes for an environment where it works. The
 * suite is skipped only when neither is available, and {@link #unavailableReason()} says what to do
 * about it rather than leaving a bare "disabled".
 */
public final class FacilitiesPostgresSupport {

    static final String URL_PROPERTY = "SFL_FACILITIES_TEST_DB_URL";
    static final String FALLBACK_URL_PROPERTY = "SFL_TEST_DB_URL";
    static final String USERNAME_PROPERTY = "SFL_TEST_DB_USERNAME";
    static final String PASSWORD_PROPERTY = "SFL_TEST_DB_PASSWORD";
    private static final String IMAGE = "postgres:16-alpine";

    private static final ResolvedDatabase DATABASE = resolve();

    private FacilitiesPostgresSupport() {
    }

    /** Whether a PostgreSQL is reachable. Referenced by {@code @EnabledIf} so the class skips with a reason. */
    public static boolean databaseAvailable() {
        return DATABASE != null;
    }

    /** Explains, for a skipped run, what to do about it. */
    public static String unavailableReason() {
        return "No PostgreSQL available. Either set " + URL_PROPERTY
                + " (for example jdbc:postgresql://localhost:55441/sfl_facilities_service_e2e), set "
                + FALLBACK_URL_PROPERTY + ", or make Docker reachable to Testcontainers.";
    }

    /** Called from each suite's own {@code @DynamicPropertySource}, which is where Spring looks for it. */
    public static void datasource(DynamicPropertyRegistry registry) {
        if (DATABASE == null) {
            return;
        }
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    private static ResolvedDatabase resolve() {
        ResolvedDatabase external = fromEnvironment();
        return external != null ? external : fromTestcontainers();
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
        return new ResolvedDatabase(url, username == null ? "sfl" : username, password == null ? "sfl" : password);
    }

    private static ResolvedDatabase fromTestcontainers() {
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                return null;
            }
            @SuppressWarnings("resource")
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(IMAGE)
                    .withDatabaseName("sfl_facilities_service")
                    .withUsername("sfl")
                    .withPassword("sfl");
            container.start();
            // Deliberately not stopped: Ryuk reaps it when the JVM exits, and the container is shared.
            return new ResolvedDatabase(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        } catch (RuntimeException | LinkageError exception) {
            // Auto-detection failures degrade to "skip", never to a broken static initialiser that takes
            // the whole class down with an obscure error.
            return null;
        }
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value != null && !value.isBlank() ? value : System.getenv(name);
    }

    private record ResolvedDatabase(String url, String username, String password) {
    }
}
