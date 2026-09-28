package gh.edu.clet.sfl.facilities.phase2;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import java.util.List;
import java.util.Map;
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
 * SRS 2026/002 CORR-06 / NFR-SEC3, enforced by the catalogue rather than by review.
 *
 * <p>"Every new Phase 2 schema enforces PostgreSQL row-level security, fail-closed, from its first
 * migration." Phase 1 deferred RLS for four build passes and shipped two scope defects in the gap. V14's
 * loop protected the tables that existed the day it ran and nothing after; a table added in V16 without
 * a policy would have been readable across sites by {@code sfl_app}, and nothing would have said so.
 *
 * <p>This asks the catalogue directly: every base table in {@code facilities} carrying a
 * {@code site_code} or {@code site_scope} column has row security enabled and the
 * {@code site_scope_read} policy, except the two V14 exempts on purpose. A Phase 2 migration that
 * forgets {@code SELECT facilities.apply_site_scope_policies();} fails here, in the build, naming the
 * table.
 */
@SpringBootTest(properties = {
        "sfl.security.enabled=false",
        "sfl.facilities.messaging.drainer-enabled=false",
        "sfl.maintenance.scheduling.enabled=false",
        "sfl.booking.scheduling.enabled=false",
})
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see FacilitiesPostgresSupport.unavailableReason()")
class Phase2RowLevelSecurityCoverageTest {

    private static final List<String> EXEMPT = List.of("facility_audit_records", "facility_runtime_configuration");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("every site-scoped table has row security enabled and the site_scope_read policy")
    void every_site_scoped_table_is_protected() {
        List<Map<String, Object>> unprotected = jdbc.queryForList("""
                SELECT DISTINCT c.table_name, cls.relrowsecurity,
                       EXISTS (SELECT 1 FROM pg_policies p WHERE p.schemaname = 'facilities'
                                AND p.tablename = c.table_name AND p.policyname = 'site_scope_read') AS has_policy
                  FROM information_schema.columns c
                  JOIN information_schema.tables t
                    ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
                  JOIN pg_class cls ON cls.relname = c.table_name
                  JOIN pg_namespace ns ON ns.oid = cls.relnamespace AND ns.nspname = 'facilities'
                 WHERE c.table_schema = 'facilities' AND c.column_name IN ('site_code', 'site_scope')
                """).stream()
                .filter(row -> !EXEMPT.contains(String.valueOf(row.get("table_name"))))
                .filter(row -> !Boolean.TRUE.equals(row.get("relrowsecurity"))
                        || !Boolean.TRUE.equals(row.get("has_policy")))
                .toList();

        assertThat(unprotected)
                .as("site-scoped tables without RLS - end the migration that created them with "
                        + "SELECT facilities.apply_site_scope_policies();")
                .isEmpty();
    }

    @Test
    @DisplayName("sfl_app can use every sequence, so a reference-number draw does not fail in production")
    void every_sequence_is_granted() {
        List<String> ungranted = jdbc.queryForList("""
                SELECT sequence_name FROM information_schema.sequences
                 WHERE sequence_schema = 'facilities'
                   AND NOT has_sequence_privilege('sfl_app', 'facilities.' || sequence_name, 'USAGE')
                """, String.class);

        assertThat(ungranted).isEmpty();
    }

    @Test
    @DisplayName("the Phase 2 foundation tables exist and are covered")
    void foundation_tables_are_covered() {
        assertThat(jdbc.queryForObject("""
                SELECT relrowsecurity FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'facilities' AND c.relname = 'vendor_inbox_messages'
                """, Boolean.class)).isTrue();
    }
}
