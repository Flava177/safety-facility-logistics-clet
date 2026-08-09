package gh.edu.clet.sfl.facilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Resolves the PostgreSQL the facilities integration tests run against.
 *
 * <p>Modelled on {@code FleetPostgresSupport}, and added for the same reason it exists there: the
 * migration suite used to be gated on {@code @Testcontainers(disabledWithoutDocker = true)}, which
 * asks whether the <em>Java</em> Docker client can reach the daemon. On Windows it frequently cannot
 * - the named-pipe transport fails even while {@code docker ps} works from a shell - so the twelve
 * tests that prove {@code V1..V10} apply and that Hibernate validates against them were skipped on
 * every run, in the one environment where they were most needed.
 *
 * <p>An external database is therefore tried first, so a developer or a CI job that already has a
 * PostgreSQL up can point at it:
 *
 * <pre>
 * SFL_FACILITIES_TEST_DB_URL=jdbc:postgresql://localhost:55441/sfl_facilities_service_e2e
 * </pre>
 *
 * <h2>The migration suite needs a different database from everybody else</h2>
 *
 * <p>{@link FacilitiesMigrationIntegrationTest} proves {@code V1..V23} apply to a <strong>virgin</strong>
 * schema and asserts absolute facts about the result - the audit chain sits at genesis, the seeded
 * configuration defaults are exactly what V5 wrote - and several of its cases insert rows at fixed
 * site codes. Every other suite in this module wants the opposite: a long-lived database it can add
 * to. One variable cannot serve both, and pointing the single {@link #URL_PROPERTY} at
 * {@code sfl_facilities_service_e2e} made the migration suite fail on every run of a green build,
 * reporting a database the caller could not have known it was reading.
 *
 * <p>So the migration suite has its own:
 *
 * <pre>
 * SFL_FACILITIES_MIGRATION_TEST_DB_URL=jdbc:postgresql://localhost:55441/sfl_facilities_migration_test
 * </pre>
 *
 * <p>Both {@code use-sfl-env.ps1} and {@code use-sfl-env.sh} set it. When it is set and reachable,
 * {@link #migrationDatasource(DynamicPropertyRegistry)} <strong>empties the schema before Flyway
 * runs</strong>, so the suite starts from nothing on every build with no manual step. When it is not
 * set, the suite falls back to the shared database and the precondition in the test refuses the run
 * with an explanation - exactly the behaviour that existed before.
 *
 * <h2>Why emptying is safe here and was not before</h2>
 *
 * <p>This class used to refuse to clean anything, and the reasoning was sound: a mistyped
 * {@link #URL_PROPERTY} would have destroyed whichever database it named, including the shared
 * {@code sfl_facilities_service_e2e}. What changed is that the emptying is gated on <em>the database
 * name itself</em> - it must end in {@value #MIGRATION_DATABASE_SUFFIX} - and on a variable that
 * exists for no other purpose. A typo now produces a refusal and a message, not a lost database.
 *
 * <p>Testcontainers remains the fallback, so nothing changes for an environment where it works. The
 * suite is skipped only when neither is available, and {@link #unavailableReason()} says what to do
 * about it rather than leaving a bare "disabled".
 */
public final class FacilitiesPostgresSupport {

    static final String URL_PROPERTY = "SFL_FACILITIES_TEST_DB_URL";
    static final String MIGRATION_URL_PROPERTY = "SFL_FACILITIES_MIGRATION_TEST_DB_URL";
    static final String FALLBACK_URL_PROPERTY = "SFL_TEST_DB_URL";
    static final String USERNAME_PROPERTY = "SFL_TEST_DB_USERNAME";
    static final String PASSWORD_PROPERTY = "SFL_TEST_DB_PASSWORD";

    /** The emptying in {@link #migrationDatasource} will only ever touch a database named like this. */
    static final String MIGRATION_DATABASE_SUFFIX = "_migration_test";

    private static final String IMAGE = "postgres:16-alpine";
    private static final String OWNED_SCHEMA = "facilities";

    private static final ResolvedDatabase DATABASE = resolve();
    private static final ResolvedDatabase MIGRATION_DATABASE = resolveMigration();

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
        register(registry, DATABASE);
    }

    /**
     * The migration suite's datasource: its own database, emptied before Flyway sees it.
     *
     * <p>Emptying happens here rather than in a {@code @BeforeAll} because Flyway runs while the
     * Spring context starts, which is before any test callback. Dropping the schema afterwards would
     * prove nothing: the migrations would already have decided they had nothing to do.
     */
    public static void migrationDatasource(DynamicPropertyRegistry registry) {
        if (MIGRATION_DATABASE == null) {
            datasource(registry);
            return;
        }
        emptyTheOwnedSchema(MIGRATION_DATABASE);
        register(registry, MIGRATION_DATABASE);
    }

    private static void register(DynamicPropertyRegistry registry, ResolvedDatabase database) {
        registry.add("spring.datasource.url", database::url);
        registry.add("spring.datasource.username", database::username);
        registry.add("spring.datasource.password", database::password);
    }

    /**
     * Drops {@code facilities} so Flyway rebuilds it from V1.
     *
     * <p>Refuses, loudly and without throwing, any database whose name does not end in
     * {@value #MIGRATION_DATABASE_SUFFIX}. The suite's own virgin-database precondition then fails
     * with the message that explains what to set - which is a better outcome than a stack trace here,
     * because the thing that needs fixing is a variable rather than a test.
     */
    private static void emptyTheOwnedSchema(ResolvedDatabase database) {
        String name = databaseName(database.url());
        if (!name.endsWith(MIGRATION_DATABASE_SUFFIX)) {
            System.err.println("Refusing to empty '" + name + "': " + MIGRATION_URL_PROPERTY
                    + " must name a database ending in '" + MIGRATION_DATABASE_SUFFIX + "'.");
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                        database.url(), database.username(), database.password());
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + OWNED_SCHEMA + " CASCADE");
        } catch (SQLException failure) {
            throw new IllegalStateException(
                    "Could not empty '" + name + "' before the migration suite ran", failure);
        }
    }

    /** The database a JDBC URL names, without any query parameters. */
    static String databaseName(String jdbcUrl) {
        String withoutParameters = jdbcUrl.split("[?;]", 2)[0];
        int lastSlash = withoutParameters.lastIndexOf('/');
        return lastSlash < 0 ? "" : withoutParameters.substring(lastSlash + 1);
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
        return credentialed(url);
    }

    /**
     * The migration suite's database, or {@code null} to fall back to the shared one.
     *
     * <p>Connectivity is checked here rather than left to the context, because an unreachable URL
     * would otherwise surface as a Spring startup failure several seconds later and name Hikari
     * rather than the variable that is wrong.
     */
    private static ResolvedDatabase resolveMigration() {
        String url = property(MIGRATION_URL_PROPERTY);
        if (url == null || url.isBlank()) {
            return null;
        }
        ResolvedDatabase candidate = credentialed(url);
        try (Connection reachable = DriverManager.getConnection(
                candidate.url(), candidate.username(), candidate.password())) {
            return reachable == null ? null : candidate;
        } catch (SQLException unreachable) {
            System.err.println(MIGRATION_URL_PROPERTY + "=" + url + " could not be reached ("
                    + unreachable.getMessage() + "). Falling back to " + URL_PROPERTY
                    + "; create the database with:"
                    + System.lineSeparator()
                    + "  docker exec sfl-facilities-e2e-postgres psql -U sfl -d postgres"
                    + " -c \"CREATE DATABASE " + databaseName(url) + " OWNER sfl;\"");
            return null;
        }
    }

    private static ResolvedDatabase credentialed(String url) {
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
