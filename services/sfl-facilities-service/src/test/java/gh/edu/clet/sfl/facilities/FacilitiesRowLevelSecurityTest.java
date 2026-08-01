package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Row-level security, proved against the role it actually applies to — ADR 0007, V14.
 *
 * <p><strong>Why this test connects for itself.</strong> The application connects as the schema
 * owner, and a table owner bypasses RLS. That is deliberate: FORCE would apply the policies to Flyway,
 * so a migration that backfills would silently write nothing, which is a worse failure than the one
 * being prevented. The policies are therefore carried by a separate {@code sfl_app} role, and a test
 * that ran as the owner would pass while proving nothing at all — the most dangerous kind of green.
 *
 * <p>So this opens its own connection as {@code sfl_app} and asks the three questions that matter:
 * does an unscoped session see anything, does a scoped one see only its own site, and does {@code *}
 * see across. The seeding is done as the owner, because the point is that data which exists is
 * nonetheless invisible.
 */
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.maintenance.scheduling.enabled=false",
        "sfl.booking.scheduling.enabled=false",
})
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
class FacilitiesRowLevelSecurityTest {

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
        // The migration creates the role NOLOGIN so no credential is invented in version control.
        // A test needs to become it, so it grants itself one here and nowhere else.
        jdbc.execute("ALTER ROLE sfl_app LOGIN PASSWORD '" + PASSWORD + "'");

        siteA = "RLSA" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        siteB = "RLSB" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        insertSite(siteA);
        insertSite(siteB);
    }

    @Test
    @DisplayName("an unscoped session sees nothing — the policies fail closed")
    void unset_scope_sees_nothing() throws SQLException {
        // The whole value of a second layer. A policy that opened up when the application forgot to
        // set the scope would protect exactly nothing.
        assertThat(sitesVisibleTo(null)).isZero();
    }

    @Test
    @DisplayName("an empty scope sees nothing either")
    void empty_scope_sees_nothing() throws SQLException {
        assertThat(sitesVisibleTo("")).isZero();
    }

    @Test
    @DisplayName("a scoped session sees its own site and not the other")
    void one_scope_sees_one_site() throws SQLException {
        assertThat(namesVisibleTo(siteA)).contains(siteA).doesNotContain(siteB);
    }

    @Test
    @DisplayName("two scopes see both, and still nothing else")
    void two_scopes_see_both() throws SQLException {
        assertThat(namesVisibleTo(siteA + "," + siteB)).contains(siteA, siteB);
    }

    @Test
    @DisplayName("the cross-site scope sees across, matching SiteScopeFilter.all()")
    void star_sees_everything() throws SQLException {
        assertThat(namesVisibleTo("*")).contains(siteA, siteB);
    }

    @Test
    @DisplayName("a write outside the scope is refused by WITH CHECK, not silently dropped")
    void a_write_outside_scope_is_refused() throws SQLException {
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL app.site_scopes = '" + siteA + "'");
                statement.executeUpdate(insertSiteSql("RLS-FORBIDDEN"));
                throw new AssertionError("a site outside the caller's scope must not be insertable");
            } catch (SQLException expected) {
                // 42501 insufficient_privilege — PostgreSQL's answer for a WITH CHECK violation.
                assertThat(expected.getSQLState()).isEqualTo("42501");
            } finally {
                connection.rollback();
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private void insertSite(String siteCode) {
        jdbc.update(insertSiteSql(siteCode));
    }

    private static String insertSiteSql(String siteCode) {
        return "INSERT INTO facilities.sites (id, site_code, name, lifecycle_status, operating_mode,"
                + " created_by, created_at, last_modified_by, last_modified_at, source_channel, record_version)"
                + " VALUES ('" + UUID.randomUUID() + "', '" + siteCode + "', '" + siteCode + "',"
                + " 'ACTIVE', 'ROUTINE', 'rls-test', now(), 'rls-test', now(), 'SYSTEM', 0)";
    }

    private int sitesVisibleTo(String scopes) throws SQLException {
        return namesVisibleTo(scopes).size();
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
                        "SELECT site_code FROM facilities.sites WHERE site_code LIKE 'RLS%'")) {
                    while (rows.next()) {
                        found.add(rows.getString(1));
                    }
                }
            }
            connection.rollback();
        }
        return found;
    }

    /** A connection as the role the policies apply to, rather than the owner that bypasses them. */
    private Connection asApplicationRole() throws SQLException {
        String url;
        try (Connection owner = dataSource.getConnection()) {
            url = owner.getMetaData().getURL();
        }
        return DriverManager.getConnection(url, "sfl_app", PASSWORD);
    }
}
