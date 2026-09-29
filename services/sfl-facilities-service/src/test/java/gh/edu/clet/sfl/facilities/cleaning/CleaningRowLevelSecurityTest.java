package gh.edu.clet.sfl.facilities.cleaning;

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
 * Row-level security for {@code facilities.cleaning_tasks}, proved against {@code sfl_app} - the pattern
 * {@code FacilitiesRowLevelSecurityTest} established, see its Javadoc for why this connects for itself
 * rather than running as the schema owner.
 */
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.maintenance.scheduling.enabled=false",
        "sfl.booking.scheduling.enabled=false",
        "sfl.cleaning.scheduling.enabled=false",
})
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
class CleaningRowLevelSecurityTest {

    private static final String PASSWORD = "rls-test-cleaning";

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private String siteA;
    private String siteB;
    private UUID roomA;
    private UUID roomB;

    @BeforeEach
    void seedAsOwner() throws SQLException {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER ROLE sfl_app LOGIN PASSWORD '" + PASSWORD + "'");

        siteA = "CLA" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        siteB = "CLB" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        roomA = insertRoom(siteA);
        roomB = insertRoom(siteB);
        insertTask(siteA, roomA, "CT-" + siteA + "-000001");
        insertTask(siteB, roomB, "CT-" + siteB + "-000001");
    }

    @Test
    @DisplayName("an unscoped session sees no cleaning tasks - the policy fails closed")
    void unset_scope_sees_nothing() throws SQLException {
        assertThat(taskNumbersVisibleTo(null)).isEmpty();
    }

    @Test
    @DisplayName("a scoped session sees its own site's tasks and not the other's")
    void one_scope_sees_one_site() throws SQLException {
        assertThat(taskNumbersVisibleTo(siteA)).anyMatch(number -> number.startsWith("CT-" + siteA))
                .noneMatch(number -> number.startsWith("CT-" + siteB));
    }

    @Test
    @DisplayName("the cross-site scope sees across, matching every other Phase 2 table")
    void star_sees_everything() throws SQLException {
        List<String> visible = taskNumbersVisibleTo("*");
        assertThat(visible).anyMatch(number -> number.startsWith("CT-" + siteA))
                .anyMatch(number -> number.startsWith("CT-" + siteB));
    }

    @Test
    @DisplayName("a write outside the scope is refused by WITH CHECK, not silently dropped")
    void a_write_outside_scope_is_refused() throws SQLException {
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL app.site_scopes = '" + siteA + "'");
                statement.executeUpdate(insertTaskSql(siteB, roomB, "CT-" + siteB + "-999999"));
                throw new AssertionError("a task outside the caller's scope must not be insertable");
            } catch (SQLException expected) {
                assertThat(expected.getSQLState()).isEqualTo("42501");
            } finally {
                connection.rollback();
            }
        }
    }

    // ---- helpers -----------------------------------------------------------------------------

    private UUID insertRoom(String siteCode) {
        UUID siteId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        jdbc.update("INSERT INTO facilities.sites (id, site_code, name, lifecycle_status, operating_mode,"
                + " created_by, created_at, last_modified_by, last_modified_at, source_channel, record_version)"
                + " VALUES ('" + siteId + "', '" + siteCode + "', '" + siteCode + "', 'ACTIVE', 'ROUTINE',"
                + " 'rls-test', now(), 'rls-test', now(), 'SYSTEM', 0)");
        jdbc.update("INSERT INTO facilities.buildings (id, site_id, site_code, building_code, name,"
                + " lifecycle_status, created_by, created_at, last_modified_by, last_modified_at, source_channel,"
                + " record_version) VALUES ('" + buildingId + "', '" + siteId + "', '" + siteCode + "', 'BLD',"
                + " 'Block', 'ACTIVE', 'rls-test', now(), 'rls-test', now(), 'SYSTEM', 0)");
        jdbc.update("INSERT INTO facilities.facility_floors (id, building_id, site_code, floor_code, name,"
                + " level_number, lifecycle_status, created_by, created_at, last_modified_by, last_modified_at,"
                + " source_channel, record_version) VALUES ('" + floorId + "', '" + buildingId + "', '" + siteCode
                + "', 'GF', 'Ground', 0, 'ACTIVE', 'rls-test', now(), 'rls-test', now(), 'SYSTEM', 0)");
        jdbc.update("INSERT INTO facilities.facility_rooms (id, floor_id, site_code, room_code, name, space_type,"
                + " bookable, examination_capable, readiness_status, readiness_locked, lifecycle_status, created_by,"
                + " created_at, last_modified_by, last_modified_at, source_channel, record_version)"
                + " VALUES ('" + roomId + "', '" + floorId + "', '" + siteCode + "', 'RM-1', 'Room 1', 'OFFICE',"
                + " false, false, 'READY', false, 'ACTIVE', 'rls-test', now(), 'rls-test', now(), 'SYSTEM', 0)");
        return roomId;
    }

    private void insertTask(String siteCode, UUID roomId, String taskNumber) {
        jdbc.update(insertTaskSql(siteCode, roomId, taskNumber));
    }

    private static String insertTaskSql(String siteCode, UUID roomId, String taskNumber) {
        return "INSERT INTO facilities.cleaning_tasks (id, task_number, site_code, room_id, room_code, space_type,"
                + " origin, title, window_start, due_by, status, requested_by, requested_at, created_by, created_at,"
                + " last_modified_by, last_modified_at, source_channel, record_version)"
                + " VALUES ('" + UUID.randomUUID() + "', '" + taskNumber + "', '" + siteCode + "', '" + roomId
                + "', 'RM-1', 'OFFICE', 'ADHOC', 'Ad-hoc clean', now(), now() + interval '1 hour', 'OPEN',"
                + " 'rls-test', now(), 'rls-test', now(), 'rls-test', now(), 'SYSTEM', 0)";
    }

    private List<String> taskNumbersVisibleTo(String scopes) throws SQLException {
        List<String> found = new ArrayList<>();
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                if (scopes != null) {
                    statement.execute("SET LOCAL app.site_scopes = '" + scopes + "'");
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT task_number FROM facilities.cleaning_tasks WHERE task_number LIKE 'CT-CL%'")) {
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
