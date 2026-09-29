package gh.edu.clet.sfl.facilities.spaceplanning;

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
 * Row-level security on the V18 tables, connected as {@code sfl_app} - the role the policies actually
 * apply to (see {@code FacilitiesRowLevelSecurityTest}'s class javadoc for why the application-owner
 * connection this test's own {@code @Autowired DataSource} uses cannot prove this).
 *
 * <p>{@code space_scenarios} stands in for the module's seven site-scoped tables; every one of them is
 * already covered generically by {@code Phase2RowLevelSecurityCoverageTest} (RLS enabled, policy present),
 * so this proves the third thing that test cannot: that the policy actually behaves as scoped, from the
 * one role real traffic uses.
 */
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.maintenance.scheduling.enabled=false",
        "sfl.booking.scheduling.enabled=false",
        "sfl.space-planning.scheduling.enabled=false",
})
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
class SpacePlanningRowLevelSecurityTest {

    private static final String PASSWORD = "rls-test";

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
        siteA = "SPA" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        siteB = "SPB" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        insertScenario(siteA);
        insertScenario(siteB);
    }

    @Test
    @DisplayName("an unscoped session sees no space_scenarios rows - the policy fails closed")
    void unscoped_sees_nothing() throws SQLException {
        assertThat(namesVisibleTo(null)).isEmpty();
    }

    @Test
    @DisplayName("a scoped session sees its own site's scenarios and not the other site's")
    void scoped_sees_own_site_only() throws SQLException {
        assertThat(namesVisibleTo(siteA)).contains(siteA).doesNotContain(siteB);
    }

    @Test
    @DisplayName("a write outside the caller's scope is refused with 42501, not silently dropped")
    void write_outside_scope_is_refused() throws SQLException {
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL app.site_scopes = '" + siteA + "'");
                statement.executeUpdate(insertScenarioSql("SP-FORBIDDEN-000001"));
                throw new AssertionError("a scenario outside the caller's scope must not be insertable");
            } catch (SQLException expected) {
                assertThat(expected.getSQLState()).isEqualTo("42501");
            } finally {
                connection.rollback();
            }
        }
    }

    private void insertScenario(String siteCode) {
        jdbc.update(insertScenarioSql(siteCode));
    }

    private static String insertScenarioSql(String siteCode) {
        return "INSERT INTO facilities.space_scenarios (id, site_code, plan_reference, version_number, name,"
                + " status, created_by, created_at, last_modified_by, last_modified_at, source_channel, record_version)"
                + " VALUES ('" + UUID.randomUUID() + "', '" + siteCode + "', 'SP-" + siteCode + "-000001', 1, "
                + "'RLS test plan', 'DRAFT', 'rls-test', now(), 'rls-test', now(), 'SYSTEM', 0)";
    }

    private java.util.List<String> namesVisibleTo(String scopes) throws SQLException {
        java.util.List<String> found = new java.util.ArrayList<>();
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                if (scopes != null) {
                    statement.execute("SET LOCAL app.site_scopes = '" + scopes + "'");
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT site_code FROM facilities.space_scenarios WHERE site_code LIKE 'SP%'")) {
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
