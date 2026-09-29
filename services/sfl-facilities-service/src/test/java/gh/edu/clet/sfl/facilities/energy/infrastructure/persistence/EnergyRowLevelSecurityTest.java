package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
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
 * Row-level security on {@code facilities.energy_meters}, proved against {@code sfl_app} - ADR 0007, V17.
 *
 * <p>Copies {@code FacilitiesRowLevelSecurityTest}'s method exactly: the application connects as the
 * schema owner, which bypasses RLS, so a meaningful test opens its own connection as {@code sfl_app} and
 * asks whether an unscoped session sees nothing, a scoped one sees only its own site, and a write outside
 * scope is refused by {@code WITH CHECK} rather than silently dropped.
 */
// The exact property set FacilitiesRowLevelSecurityTest uses, deliberately - not merely for consistency
// but so Spring's test-context cache reuses that one already-running context and its connection pool
// instead of starting a second, identical one. The energy sweeps default to hourly with a five-minute
// initial delay, so leaving them at their default for the seconds this test runs never fires one.
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.maintenance.scheduling.enabled=false",
        "sfl.booking.scheduling.enabled=false",
})
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
class EnergyRowLevelSecurityTest {

    private static final String PASSWORD = "energy-rls-test";

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

        siteA = "ERLA" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        siteB = "ERLB" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        insertMeter(siteA, "MTR-A");
        insertMeter(siteB, "MTR-B");
    }

    @Test
    @DisplayName("an unscoped session sees no energy meters - the policy fails closed")
    void unscoped_sees_nothing() throws SQLException {
        assertThat(metersVisibleTo(null)).isEmpty();
    }

    @Test
    @DisplayName("a scoped session sees only its own site's meters")
    void scoped_sees_own_site_only() throws SQLException {
        assertThat(metersVisibleTo(siteA)).contains(siteA).doesNotContain(siteB);
    }

    @Test
    @DisplayName("the cross-site scope sees across every site")
    void star_sees_everything() throws SQLException {
        assertThat(metersVisibleTo("*")).contains(siteA, siteB);
    }

    @Test
    @DisplayName("a write outside the scope is refused with 42501, not silently dropped")
    void write_outside_scope_is_refused() throws SQLException {
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL app.site_scopes = '" + siteA + "'");
                statement.executeUpdate(insertMeterSql(siteB, "MTR-FORBIDDEN"));
                throw new AssertionError("a meter outside the caller's scope must not be insertable");
            } catch (SQLException expected) {
                assertThat(expected.getSQLState()).isEqualTo("42501");
            } finally {
                connection.rollback();
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private void insertMeter(String siteCode, String meterCode) {
        jdbc.update(insertMeterSql(siteCode, meterCode));
    }

    private static String insertMeterSql(String siteCode, String meterCode) {
        return "INSERT INTO facilities.energy_meters (id, site_code, building_code, meter_code, name, utility,"
                + " canonical_unit, source, expected_interval_minutes, status, created_by, created_at,"
                + " last_modified_by, last_modified_at, source_channel)"
                + " VALUES ('" + UUID.randomUUID() + "', '" + siteCode + "', 'B1', '" + meterCode + "', '" + meterCode
                + "', 'ELECTRICITY', 'kWh', 'MANUAL', 43200, 'ACTIVE', 'rls-test', now(), 'rls-test', now(), 'SYSTEM')";
    }

    private List<String> metersVisibleTo(String scopes) throws SQLException {
        List<String> found = new ArrayList<>();
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                if (scopes != null) {
                    statement.execute("SET LOCAL app.site_scopes = '" + scopes + "'");
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT site_code FROM facilities.energy_meters WHERE site_code LIKE 'ERL%'")) {
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
