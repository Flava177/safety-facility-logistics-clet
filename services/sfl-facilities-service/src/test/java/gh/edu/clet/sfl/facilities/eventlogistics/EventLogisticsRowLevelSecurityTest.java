package gh.edu.clet.sfl.facilities.eventlogistics;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesMasterDataService;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 * Row-level security for {@code event_setup_tasks} (V20) - ADR 0007, SRS 2026/002 CORR-06.
 *
 * <p>Copies {@code FacilitiesRowLevelSecurityTest}'s shape: a real {@code sfl_app} connection, seeded
 * data written as the schema owner (who bypasses RLS), then the three questions that matter - does an
 * unscoped session see nothing, does a scoped one see only its own site, and is a write outside scope
 * refused by {@code WITH CHECK} rather than silently dropped. The estate (site, building, floor, room)
 * is seeded through the real {@link FacilitiesMasterDataService} rather than hand-written SQL, since a
 * set-up task's {@code room_id} is a real foreign key into S152's tables and the room needs every
 * column that service already knows how to fill in correctly.
 */
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.maintenance.scheduling.enabled=false",
        "sfl.booking.scheduling.enabled=false",
        "sfl.event-logistics.scheduling.enabled=false",
})
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
class EventLogisticsRowLevelSecurityTest {

    private static final String PASSWORD = "rls-test-eventlogistics";
    private static final SiteScopedPrincipal SEED = new SiteScopedPrincipal("rls-seed", "RLS seed",
            Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);
    private static final ActorContext SEED_ACTOR = new ActorContext(SEED, "rls-seed");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FacilitiesMasterDataService estate;

    private JdbcTemplate jdbc;
    private String siteA;
    private String siteB;
    private UUID roomB;

    @BeforeEach
    void seedAsOwner() throws SQLException {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER ROLE sfl_app LOGIN PASSWORD '" + PASSWORD + "'");

        siteA = "RLA" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        siteB = "RLB" + Math.abs(UUID.randomUUID().hashCode() % 100000);
        UUID roomA = seedRoom(siteA);
        roomB = seedRoom(siteB);

        insertTask(siteA, roomA, "RLS-S078-" + siteA);
        insertTask(siteB, roomB, "RLS-S078-" + siteB);
    }

    @Test
    @DisplayName("an unscoped session sees no event set-up task")
    void unscoped_session_sees_nothing() throws SQLException {
        assertThat(referencesVisibleTo(null)).isEmpty();
    }

    @Test
    @DisplayName("a scoped session sees only its own site's set-up tasks")
    void scoped_session_sees_only_its_site() throws SQLException {
        List<String> visible = referencesVisibleTo(siteA);
        assertThat(visible).contains("RLS-S078-" + siteA);
        assertThat(visible).doesNotContain("RLS-S078-" + siteB);
    }

    @Test
    @DisplayName("the cross-site scope sees every site's set-up tasks")
    void star_scope_sees_every_site() throws SQLException {
        List<String> visible = referencesVisibleTo("*");
        assertThat(visible).contains("RLS-S078-" + siteA, "RLS-S078-" + siteB);
    }

    @Test
    @DisplayName("a write outside the caller's scope is refused with 42501, not silently dropped")
    void write_outside_scope_is_refused() throws SQLException {
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL app.site_scopes = '" + siteA + "'");
                statement.executeUpdate(insertTaskSql(siteB, roomB, "RLS-FORBIDDEN"));
                throw new AssertionError("a task for a site outside the caller's scope must not be insertable");
            } catch (SQLException expected) {
                assertThat(expected.getSQLState()).isEqualTo("42501");
            } finally {
                connection.rollback();
            }
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private UUID seedRoom(String siteCode) {
        var site = estate.createSite(new FacilitiesCommands.CreateSite(siteCode, siteCode, null, SEED_ACTOR,
                SourceChannel.WEB, null));
        var building = estate.createBuilding(new FacilitiesCommands.CreateBuilding(site.id(), "B1", "Block 1", null,
                SEED_ACTOR, SourceChannel.WEB, null));
        var floor = estate.createFloor(new FacilitiesCommands.CreateFloor(building.id(), "GF", "Ground floor", 0,
                SEED_ACTOR, SourceChannel.WEB, null));
        FacilityRoom room = estate.createRoom(new FacilitiesCommands.CreateRoom(floor.id(), "R1", "Room 1",
                SpaceType.MEETING_ROOM, 12, null, null, true, false, SEED_ACTOR, SourceChannel.WEB, null));
        return room.id();
    }

    private void insertTask(String siteCode, UUID roomId, String reference) {
        jdbc.update(insertTaskSql(siteCode, roomId, reference));
    }

    private static String insertTaskSql(String siteCode, UUID roomId, String reference) {
        Instant now = Instant.now();
        Instant starts = now.plusSeconds(3600);
        Instant ends = now.plusSeconds(7200);
        return "INSERT INTO facilities.event_setup_tasks (id, site_code, task_reference, s078_event_reference,"
                + " title, event_category, starts_at, ends_at, room_id, room_code, expected_attendance,"
                + " external_contractors, temporary_structures, status, handoff_count, last_handoff_at,"
                + " created_by, created_at, last_modified_by, last_modified_at, source_channel, record_version)"
                + " VALUES ('" + UUID.randomUUID() + "', '" + siteCode + "', 'EV-" + siteCode + "-"
                + Math.abs(reference.hashCode() % 100000) + "', '" + reference + "', 'Test event', 'MOOT', '"
                + starts + "', '" + ends + "', '" + roomId + "', 'R1', 10, false, false, 'OPEN', 1, '" + now
                + "', 'rls-test', '" + now + "', 'rls-test', '" + now + "', 'SYSTEM', 0)";
    }

    private List<String> referencesVisibleTo(String scopes) throws SQLException {
        List<String> found = new ArrayList<>();
        try (Connection connection = asApplicationRole()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                if (scopes != null) {
                    statement.execute("SET LOCAL app.site_scopes = '" + scopes + "'");
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT s078_event_reference FROM facilities.event_setup_tasks"
                                + " WHERE s078_event_reference LIKE 'RLS-S078-%'")) {
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
