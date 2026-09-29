package gh.edu.clet.sfl.facilities.construction;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Row-level security for {@code facilities.construction_projects}, proved against {@code sfl_app} -
 * ADR 0007, V21 - following the pattern {@code FacilitiesRowLevelSecurityTest} established.
 *
 * <p>Why this reproves what the shared {@code Phase2RowLevelSecurityCoverageTest} already checks:
 * that test confirms the policy exists on the catalogue; this one confirms it actually filters rows
 * for the role the application connects as, which is the failure ADR 0007 records Phase 1 shipping
 * twice without a test catching it.
 */
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.maintenance.scheduling.enabled=false",
        "sfl.booking.scheduling.enabled=false",
        "sfl.construction.scheduling.enabled=false",
})
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
class ConstructionRowLevelSecurityTest {

    private static final String PASSWORD = "rls-test-construction";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private String siteA;
    private String siteB;

    @BeforeEach
    void seedAsOwner() throws SQLException {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER ROLE sfl_app LOGIN PASSWORD '" + PASSWORD + "'");

        siteA = "CRLSA" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        siteB = "CRLSB" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        insertProject(siteA);
        insertProject(siteB);
    }

    @Test
    @DisplayName("an unscoped session sees no construction project - the policy fails closed")
    void unset_scope_sees_nothing() throws SQLException {
        assertThat(projectsVisibleTo(null)).isZero();
    }

    @Test
    @DisplayName("a scoped session sees its own site's projects and not the other's")
    void one_scope_sees_one_site() throws SQLException {
        assertThat(referencesVisibleTo(siteA)).contains(siteA).doesNotContain(siteB);
    }

    @Test
    @DisplayName("the cross-site scope sees across every site")
    void star_sees_everything() throws SQLException {
        assertThat(referencesVisibleTo("*")).contains(siteA, siteB);
    }

    @Test
    @DisplayName("a write outside the scope is refused by WITH CHECK, not silently dropped")
    void a_write_outside_scope_is_refused() throws SQLException {
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL app.site_scopes = '" + siteA + "'");
                statement.executeUpdate(insertProjectSql("CRLS-FORBIDDEN"));
                throw new AssertionError("a site outside the caller's scope must not be insertable");
            } catch (SQLException expected) {
                assertThat(expected.getSQLState()).isEqualTo("42501");
            } finally {
                connection.rollback();
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private void insertProject(String siteCode) {
        jdbc.update(insertProjectSql(siteCode));
    }

    private static String insertProjectSql(String siteCode) {
        return "INSERT INTO facilities.construction_projects (id, project_reference, site_code, title, scope,"
                + " status, origin, registered_by, registered_at, created_by, created_at, last_modified_by,"
                + " last_modified_at, source_channel, record_version)"
                + " VALUES ('" + UUID.randomUUID() + "', '" + siteCode + "-REF', '" + siteCode + "', 'x', 'x',"
                + " 'PROPOSED', 'DIRECT', 'rls-test', now(), 'rls-test', now(), 'rls-test', now(), 'SYSTEM', 0)";
    }

    private int projectsVisibleTo(String scopes) throws SQLException {
        return referencesVisibleTo(scopes).size();
    }

    private java.util.List<String> referencesVisibleTo(String scopes) throws SQLException {
        java.util.List<String> found = new java.util.ArrayList<>();
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                if (scopes != null) {
                    statement.execute("SET LOCAL app.site_scopes = '" + scopes + "'");
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT site_code FROM facilities.construction_projects WHERE site_code LIKE 'CRLS%'")) {
                    while (rows.next()) {
                        found.add(rows.getString(1));
                    }
                }
            }
            connection.rollback();
        }
        return found;
    }

    private Connection asApplicationRole() throws SQLException {
        String url;
        try (Connection owner = dataSource.getConnection()) {
            url = owner.getMetaData().getURL();
        }
        return DriverManager.getConnection(url, "sfl_app", PASSWORD);
    }
}
