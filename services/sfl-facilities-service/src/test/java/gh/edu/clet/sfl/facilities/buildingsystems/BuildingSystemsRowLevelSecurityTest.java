package gh.edu.clet.sfl.facilities.buildingsystems;

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
 * Row-level security for {@code bms_devices} - ADR 0007, V16, mirroring {@code FacilitiesRowLevelSecurityTest}.
 *
 * <p>Connects as {@code sfl_app} directly, for the reason that test's Javadoc gives: the application
 * connects as the schema owner, which bypasses RLS, so a test run as the owner would pass while proving
 * nothing. S156 is a Phase 2 module and CORR-06/NFR-SEC3 require RLS from its first migration - this is
 * that module's own proof, not a re-run of the platform-wide one.
 */
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.maintenance.scheduling.enabled=false",
        "sfl.booking.scheduling.enabled=false",
        "sfl.buildingsystems.scheduling.enabled=false",
})
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
class BuildingSystemsRowLevelSecurityTest {

    private static final String PASSWORD = "rls-test-s156";

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

        siteA = "BSA" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        siteB = "BSB" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        insertDevice(siteA, "AHU-A");
        insertDevice(siteB, "AHU-B");
    }

    @Test
    @DisplayName("an unscoped session sees no S156 device - the policies fail closed")
    void unset_scope_sees_nothing() throws SQLException {
        assertThat(deviceCodesVisibleTo(null)).isEmpty();
    }

    @Test
    @DisplayName("a scoped session sees only its own site's devices")
    void one_scope_sees_one_site() throws SQLException {
        assertThat(deviceCodesVisibleTo(siteA)).contains("AHU-A").doesNotContain("AHU-B");
    }

    @Test
    @DisplayName("the cross-site scope sees across every site's devices")
    void star_sees_everything() throws SQLException {
        assertThat(deviceCodesVisibleTo("*")).contains("AHU-A", "AHU-B");
    }

    @Test
    @DisplayName("a write outside the scope is refused by WITH CHECK, SQLSTATE 42501")
    void a_write_outside_scope_is_refused() throws SQLException {
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL app.site_scopes = '" + siteA + "'");
                statement.executeUpdate(insertDeviceSql(UUID.randomUUID(), "RLS-FORBIDDEN", "AHU-X"));
                throw new AssertionError("a device at a site outside the caller's scope must not be insertable");
            } catch (SQLException expected) {
                assertThat(expected.getSQLState()).isEqualTo("42501");
            } finally {
                connection.rollback();
            }
        }
    }

    private void insertDevice(String siteCode, String deviceCode) {
        jdbc.update(insertDeviceSql(UUID.randomUUID(), siteCode, deviceCode));
    }

    private static String insertDeviceSql(UUID id, String siteCode, String deviceCode) {
        // The AVAMP asset id is unique across the whole table (ux_bms_devices_avamp is global, not
        // per-site - "one device per AVAMP asset id, ever"), so it is derived from the device's own id
        // rather than its code, which two sites in this test intentionally share.
        return "INSERT INTO facilities.bms_devices (id, site_code, device_code, avamp_asset_id, name, system_type,"
                + " device_kind, building_code, expected_interval_seconds, status, created_by, created_at,"
                + " last_modified_by, last_modified_at, source_channel, record_version)"
                + " VALUES ('" + id + "', '" + siteCode + "', '" + deviceCode + "', 'AVAMP-" + id + "',"
                + " '" + deviceCode + "', 'HVAC', 'SENSOR', 'LAW', 300, 'ACTIVE', 'rls-test', now(),"
                + " 'rls-test', now(), 'SYSTEM', 0)";
    }

    private java.util.List<String> deviceCodesVisibleTo(String scopes) throws SQLException {
        java.util.List<String> found = new java.util.ArrayList<>();
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                if (scopes != null) {
                    statement.execute("SET LOCAL app.site_scopes = '" + scopes + "'");
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT device_code FROM facilities.bms_devices WHERE device_code LIKE 'AHU-%'")) {
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
