package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The migrations, applied to a real PostgreSQL (SRS-SFL-S152-01…05).
 *
 * <p>V6 does non-trivial work — it rewrites six live tables, backfills provenance, classifies existing
 * rows into space types and drops the {@code active} column — and V7 seeds a checklist per site inside
 * a PL/pgSQL loop. None of that is exercised by a unit test, and a migration that fails on first
 * deploy is the worst place to find out.
 *
 * <p>Skipped automatically when no Docker daemon is available, so a developer without Docker still
 * gets a green build. It is not skipped in CI, where Docker is present.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FacilitiesMigrationIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sfl_facilities_service")
            .withUsername("sfl")
            .withPassword("sfl");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    void every_migration_applies_and_hibernate_validates_against_it() {
        // Reaching this assertion at all means the context started, which means Flyway ran V1..V8 and
        // Hibernate's schema validation passed against every entity in the service.
        List<String> applied = jdbc().queryForList(
                "select script from facilities.flyway_schema_history where success order by installed_rank",
                String.class);

        assertThat(applied).contains("V1__service_foundation.sql", "V2__facilities_master_data.sql",
                "V3__facility_faults.sql", "V4__work_orders.sql", "V5__facilities_platform_foundation.sql",
                "V6__facilities_estate_model.sql", "V7__facilities_readiness.sql",
                "V8__facilities_dashboard_snapshots.sql");
    }

    @Test
    void the_s152_tables_exist_in_the_facilities_schema() {
        List<String> tables = jdbc().queryForList(
                "select table_name from information_schema.tables where table_schema = 'facilities'",
                String.class);

        assertThat(tables).contains(
                "sites", "buildings", "facility_floors", "facility_rooms", "zones", "device_references",
                "facility_assets", "facility_zone_memberships",
                "facility_audit_records", "facility_audit_chain_state", "facility_runtime_configuration",
                "facility_idempotency_keys",
                "facility_readiness_checklists", "facility_readiness_checklist_items",
                "facility_readiness_assessments", "facility_readiness_assessment_items",
                "facility_readiness_blockers",
                "facility_dashboard_snapshots", "facility_dashboard_snapshot_references");
    }

    @Test
    void the_record_metadata_columns_are_on_every_estate_table() {
        for (String table : List.of("sites", "buildings", "facility_floors", "facility_rooms", "zones",
                "device_references", "facility_assets")) {
            List<String> columns = jdbc().queryForList(
                    "select column_name from information_schema.columns "
                            + "where table_schema = 'facilities' and table_name = ?",
                    String.class, table);

            assertThat(columns)
                    .as("system-managed fields on facilities.%s (SRS-SFL-S152-01)", table)
                    .contains("lifecycle_status", "created_by", "created_at", "last_modified_by",
                            "last_modified_at", "record_version", "source_channel", "correlation_id");
        }
    }

    @Test
    void the_pre_s152_active_column_is_gone_and_its_meaning_moved_to_lifecycle_status() {
        List<String> siteColumns = jdbc().queryForList(
                "select column_name from information_schema.columns "
                        + "where table_schema = 'facilities' and table_name = 'sites'",
                String.class);

        assertThat(siteColumns).doesNotContain("active");
        assertThat(siteColumns).contains("lifecycle_status", "operating_mode", "operating_mode_changed_at",
                "operating_mode_changed_by");
    }

    @Test
    void the_audit_chain_head_is_seeded_at_genesis() {
        Map<String, Object> head = jdbc().queryForMap(
                "select head_hash, next_sequence from facilities.facility_audit_chain_state where id = 1");

        assertThat(head.get("head_hash")).isEqualTo("0".repeat(64));
        assertThat(((Number) head.get("next_sequence")).longValue()).isZero();
    }

    @Test
    void the_audit_table_refuses_updates_and_deletes() {
        JdbcTemplate jdbc = jdbc();
        jdbc.update("""
                insert into facilities.facility_audit_records
                    (id, sequence_no, site_scope, actor_id, actor_display_name, action, resource_type,
                     resource_id, correlation_id, source_channel, occurred_at, previous_hash, record_hash)
                values (gen_random_uuid(), 1, 'MAIN', 'tester', 'Tester', 'SITE_CREATED', 'Site', 's1',
                        'corr', 'SYSTEM', now(), repeat('0', 64), repeat('a', 64))
                """);

        assertThatThrownBy(() -> jdbc.update(
                "update facilities.facility_audit_records set actor_id = 'someone-else' where sequence_no = 1"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "delete from facilities.facility_audit_records where sequence_no = 1"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void the_runtime_configuration_defaults_are_seeded() {
        List<String> keys = jdbc().queryForList(
                "select config_key from facilities.facility_runtime_configuration where effective_to is null",
                String.class);

        assertThat(keys).contains("facilities.readiness.staleness-threshold",
                "facilities.readiness.examination-staleness-threshold",
                "facilities.dashboard.freshness-threshold",
                "facilities.blocker.critical-escalation-window",
                "facilities.asset.service-due-warning-window");
    }

    @Test
    void only_one_active_configuration_value_may_exist_per_key_and_scope() {
        JdbcTemplate jdbc = jdbc();

        assertThatThrownBy(() -> jdbc.update("""
                insert into facilities.facility_runtime_configuration
                    (id, config_key, site_code, config_value, value_type, effective_from, version, updated_by,
                     updated_at)
                values (gen_random_uuid(), 'facilities.readiness.staleness-threshold', null, 'P1D', 'DURATION',
                        now(), 0, 'tester', now())
                """))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void a_readiness_blocker_cannot_be_resolved_without_who_when_and_why() {
        JdbcTemplate jdbc = jdbc();
        String room = seedSpace(jdbc);

        assertThatThrownBy(() -> jdbc.update("""
                insert into facilities.facility_readiness_blockers
                    (id, room_id, site_code, source, severity, description, raised_by, raised_at, resolved)
                values (gen_random_uuid(), ?::uuid, 'MAIN', 'MANUAL', 'CRITICAL', 'Leak', 'tester', now(), true)
                """, room))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void a_space_cannot_be_locked_without_recording_who_locked_it() {
        JdbcTemplate jdbc = jdbc();
        String room = seedSpace(jdbc);

        assertThatThrownBy(() -> jdbc.update(
                "update facilities.facility_rooms set readiness_locked = true where id = ?::uuid", room))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void an_asset_code_is_unique_within_a_site() {
        JdbcTemplate jdbc = jdbc();
        seedSpace(jdbc);
        insertAsset(jdbc, "GEN-01");

        assertThatThrownBy(() -> insertAsset(jdbc, "GEN-01")).isInstanceOf(DataAccessException.class);
    }

    /**
     * The seeded checklists exist for the sites present when V7 ran.
     *
     * <p>A fresh database has no sites, so the loop seeds nothing — which is correct, and worth
     * asserting so nobody later "fixes" the loop into creating orphan checklists.
     */
    @Test
    void the_readiness_checklist_seed_matches_the_sites_that_existed_when_it_ran() {
        Integer siteCount = jdbc().queryForObject("select count(*) from facilities.sites", Integer.class);
        Integer checklistCount = jdbc().queryForObject(
                "select count(*) from facilities.facility_readiness_checklists", Integer.class);

        assertThat(checklistCount).isEqualTo(siteCount * 2);
    }

    private static String seedSpace(JdbcTemplate jdbc) {
        String siteId = "11111111-1111-1111-1111-111111111111";
        String buildingId = "22222222-2222-2222-2222-222222222222";
        String floorId = "33333333-3333-3333-3333-333333333333";
        String roomId = "44444444-4444-4444-4444-444444444444";
        Integer existing = jdbc.queryForObject("select count(*) from facilities.facility_rooms where id = ?::uuid",
                Integer.class, roomId);
        if (existing != null && existing > 0) {
            return roomId;
        }
        jdbc.update("""
                insert into facilities.sites (id, site_code, name, created_at, lifecycle_status, created_by,
                    last_modified_by, last_modified_at, record_version, source_channel, operating_mode)
                values (?::uuid, 'MAIN', 'Main', now(), 'ACTIVE', 'seed', 'seed', now(), 0, 'SYSTEM', 'ROUTINE')
                """, siteId);
        jdbc.update("""
                insert into facilities.buildings (id, site_id, site_code, building_code, name, created_at,
                    lifecycle_status, created_by, last_modified_by, last_modified_at, record_version,
                    source_channel)
                values (?::uuid, ?::uuid, 'MAIN', 'BLK-A', 'Block A', now(), 'ACTIVE', 'seed', 'seed', now(),
                        0, 'SYSTEM')
                """, buildingId, siteId);
        jdbc.update("""
                insert into facilities.facility_floors (id, building_id, site_code, floor_code, name,
                    created_at, lifecycle_status, created_by, last_modified_by, last_modified_at,
                    record_version, source_channel)
                values (?::uuid, ?::uuid, 'MAIN', 'GF', 'Ground', now(), 'ACTIVE', 'seed', 'seed', now(), 0,
                        'SYSTEM')
                """, floorId, buildingId);
        jdbc.update("""
                insert into facilities.facility_rooms (id, floor_id, site_code, room_code, name,
                    readiness_status, created_at, lifecycle_status, created_by, last_modified_by,
                    last_modified_at, record_version, source_channel, space_type, bookable,
                    examination_capable, readiness_locked)
                values (?::uuid, ?::uuid, 'MAIN', 'HALL-A', 'Hall A', 'UNKNOWN', now(), 'ACTIVE', 'seed',
                        'seed', now(), 0, 'SYSTEM', 'EXAMINATION_HALL', true, true, false)
                """, roomId, floorId);
        return roomId;
    }

    private static void insertAsset(JdbcTemplate jdbc, String assetCode) {
        jdbc.update("""
                insert into facilities.facility_assets (id, site_code, asset_code, name, category, criticality,
                    operational_status, lifecycle_status, created_by, created_at, last_modified_by,
                    last_modified_at, record_version, source_channel)
                values (gen_random_uuid(), 'MAIN', ?, 'Generator', 'GENERATOR', 'CRITICAL', 'OPERATIONAL',
                        'ACTIVE', 'seed', now(), 'seed', now(), 0, 'SYSTEM')
                """, assetCode);
    }
}
