package gh.edu.clet.sfl.facilities;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Proves V14 (row-level security) applies cleanly to a database that already has production-shaped
 * rows in it, not only to the empty schema {@link FacilitiesMigrationIntegrationTest} proves V1..V14
 * against.
 *
 * <p>Every prior migration suite in this repository asserts a fresh install; none had ever asserted an
 * <em>upgrade</em> - applying the newest migration to a database that already went through the previous
 * ones and already has real data in it - which was the review's own gap (see
 * {@code docs/architecture/2026-09-10-final-issues-verification.md}, "Migration validation, upgrade
 * database"). V14 was picked deliberately: it is the one migration in this schema that touches every
 * existing table (enabling row-level security and creating a policy on each), so it is the migration
 * most likely to behave differently against a populated table than an empty one.
 *
 * <p>Uses Flyway's own Java API directly, twice, against one database: {@code target("13")} first, to
 * land exactly where a real deployment sits today, then unrestricted, to apply V14 the way a real
 * upgrade would. No Spring context is started for either step - this suite is about Flyway and the SQL
 * files, not the application wiring, which {@link FacilitiesMigrationIntegrationTest} already covers.
 *
 * <p>Reuses {@code SFL_FACILITIES_MIGRATION_TEST_DB_URL} (same database, same credentials, same
 * {@code *_migration_test} naming safeguard as {@link FacilitiesMigrationIntegrationTest}) so it never
 * touches the shared e2e database; skipped, not failed, when that variable is absent or unreachable.
 */
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesUpgradeMigrationIntegrationTest#databaseAvailable",
        disabledReason = "No PostgreSQL available; set SFL_FACILITIES_MIGRATION_TEST_DB_URL "
                + "(see FacilitiesPostgresSupport.unavailableReason()).")
class FacilitiesUpgradeMigrationIntegrationTest {

    private static final String MIGRATION_URL_PROPERTY = "SFL_FACILITIES_MIGRATION_TEST_DB_URL";
    private static final String USERNAME_PROPERTY = "SFL_TEST_DB_USERNAME";
    private static final String PASSWORD_PROPERTY = "SFL_TEST_DB_PASSWORD";
    private static final String MIGRATION_DATABASE_SUFFIX = "_migration_test";

    static boolean databaseAvailable() {
        String url = property(MIGRATION_URL_PROPERTY);
        if (url == null || url.isBlank() || !FacilitiesPostgresSupport.databaseName(url)
                .endsWith(MIGRATION_DATABASE_SUFFIX)) {
            return false;
        }
        try (Connection connection = DriverManager.getConnection(url, username(), password())) {
            return connection != null;
        } catch (SQLException unreachable) {
            return false;
        }
    }

    @Test
    void v14_row_level_security_applies_to_a_database_that_already_has_data_in_it() throws SQLException {
        String url = property(MIGRATION_URL_PROPERTY);
        String username = username();
        String password = password();

        try (Connection connection = DriverManager.getConnection(url, username, password);
                Statement statement = connection.createStatement()) {
            // sfl_app is a cluster-wide role, not scoped to this database - other databases on the same
            // Postgres instance (the shared e2e database, in particular) may already depend on it, so
            // it is never dropped here. V14 itself is idempotent about creating it (CREATE ROLE IF NOT
            // EXISTS), which is exactly what this test means to exercise.
            statement.execute("DROP SCHEMA IF EXISTS facilities CASCADE");
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        Flyway preUpgrade = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("facilities")
                .defaultSchema("facilities")
                .target("13")
                .load();
        preUpgrade.migrate();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedProductionShapedData(jdbc);
        long sitesBefore = countRows(jdbc, "facilities.sites");
        long assetsBefore = countRows(jdbc, "facilities.facility_assets");

        Flyway upgrade = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("facilities")
                .defaultSchema("facilities")
                .load();
        upgrade.migrate();

        MigrationInfo v14 = upgrade.info().applied()[upgrade.info().applied().length - 1];
        assertThat(v14.getScript()).isEqualTo("V14__row_level_security.sql");
        assertThat(v14.getState().isFailed()).isFalse();

        assertThat(countRows(jdbc, "facilities.sites"))
                .as("V14 must not touch existing row data, only privileges/RLS metadata")
                .isEqualTo(sitesBefore);
        assertThat(countRows(jdbc, "facilities.facility_assets")).isEqualTo(assetsBefore);

        Boolean sflAppExists = jdbc.queryForObject(
                "select exists(select 1 from pg_roles where rolname = 'sfl_app')", Boolean.class);
        assertThat(sflAppExists).isTrue();

        Boolean rlsEnabledOnSites = jdbc.queryForObject(
                "select relrowsecurity from pg_class where relname = 'sites' "
                        + "and relnamespace = 'facilities'::regnamespace",
                Boolean.class);
        assertThat(rlsEnabledOnSites).isTrue();

        Integer policyCount = jdbc.queryForObject(
                "select count(*) from pg_policies where schemaname = 'facilities' "
                        + "and tablename = 'sites' and policyname = 'site_scope_read'",
                Integer.class);
        assertThat(policyCount).isEqualTo(1);
    }

    private static void seedProductionShapedData(JdbcTemplate jdbc) {
        String siteId = "55555555-5555-5555-5555-555555555555";
        String buildingId = "66666666-6666-6666-6666-666666666666";
        String floorId = "77777777-7777-7777-7777-777777777777";
        String roomId = "88888888-8888-8888-8888-888888888888";
        jdbc.update("""
                insert into facilities.sites (id, site_code, name, created_at, lifecycle_status, created_by,
                    last_modified_by, last_modified_at, record_version, source_channel, operating_mode)
                values (?::uuid, 'UPGR', 'Upgrade Test Site', now(), 'ACTIVE', 'seed', 'seed', now(), 0,
                        'SYSTEM', 'ROUTINE')
                """, siteId);
        jdbc.update("""
                insert into facilities.buildings (id, site_id, site_code, building_code, name, created_at,
                    lifecycle_status, created_by, last_modified_by, last_modified_at, record_version,
                    source_channel)
                values (?::uuid, ?::uuid, 'UPGR', 'BLK-A', 'Block A', now(), 'ACTIVE', 'seed', 'seed', now(),
                        0, 'SYSTEM')
                """, buildingId, siteId);
        jdbc.update("""
                insert into facilities.facility_floors (id, building_id, site_code, floor_code, name,
                    created_at, lifecycle_status, created_by, last_modified_by, last_modified_at,
                    record_version, source_channel)
                values (?::uuid, ?::uuid, 'UPGR', 'GF', 'Ground', now(), 'ACTIVE', 'seed', 'seed', now(), 0,
                        'SYSTEM')
                """, floorId, buildingId);
        jdbc.update("""
                insert into facilities.facility_rooms (id, floor_id, site_code, room_code, name,
                    readiness_status, created_at, lifecycle_status, created_by, last_modified_by,
                    last_modified_at, record_version, source_channel, space_type, bookable,
                    examination_capable, readiness_locked)
                values (?::uuid, ?::uuid, 'UPGR', 'HALL-A', 'Hall A', 'UNKNOWN', now(), 'ACTIVE', 'seed',
                        'seed', now(), 0, 'SYSTEM', 'EXAMINATION_HALL', true, true, false)
                """, roomId, floorId);
        jdbc.update("""
                insert into facilities.facility_assets (id, site_code, asset_code, name, category,
                    criticality, operational_status, lifecycle_status, created_by, created_at,
                    last_modified_by, last_modified_at, record_version, source_channel)
                values (gen_random_uuid(), 'UPGR', 'GEN-01', 'Generator', 'GENERATOR', 'CRITICAL',
                        'OPERATIONAL', 'ACTIVE', 'seed', now(), 'seed', now(), 0, 'SYSTEM')
                """);
    }

    private static long countRows(JdbcTemplate jdbc, String table) {
        Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return count == null ? 0 : count;
    }

    private static String username() {
        String value = property(USERNAME_PROPERTY);
        return value == null || value.isBlank() ? "sfl" : value;
    }

    private static String password() {
        String value = property(PASSWORD_PROPERTY);
        return value == null || value.isBlank() ? "sfl" : value;
    }

    private static String property(String name) {
        String value = System.getProperty(name);
        return value != null && !value.isBlank() ? value : System.getenv(name);
    }
}
