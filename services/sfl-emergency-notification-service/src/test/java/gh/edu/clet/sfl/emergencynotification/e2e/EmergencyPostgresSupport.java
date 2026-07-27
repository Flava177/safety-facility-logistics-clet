package gh.edu.clet.sfl.emergencynotification.e2e;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Resolves a real PostgreSQL for the end-to-end suite: an externally supplied database
 * ({@code SFL_TEST_DB_URL}) takes precedence, else Testcontainers, else the class is skipped with a reason.
 */
public abstract class EmergencyPostgresSupport {

    static final String URL_PROPERTY = "SFL_TEST_DB_URL";
    static final String USERNAME_PROPERTY = "SFL_TEST_DB_USERNAME";
    static final String PASSWORD_PROPERTY = "SFL_TEST_DB_PASSWORD";
    private static final String IMAGE = "postgres:16-bookworm";

    private static final ResolvedDatabase DATABASE = resolve();

    public static boolean databaseAvailable() {
        return DATABASE != null;
    }

    public static String unavailableReason() {
        return "No PostgreSQL available. Set " + URL_PROPERTY
                + " (e.g. jdbc:postgresql://localhost:55432/sfl_emergency_e2e_db) or make Docker reachable.";
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
        return external != null ? external : fromTestcontainers();
    }

    private static ResolvedDatabase fromEnvironment() {
        String url = property(URL_PROPERTY);
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
                    .withDatabaseName("sfl_emergency_e2e_db").withUsername("sfl").withPassword("sfl");
            container.start();
            return new ResolvedDatabase(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        } catch (RuntimeException | LinkageError exception) {
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
