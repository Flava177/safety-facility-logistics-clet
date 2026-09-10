package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.EmergencyRepository;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.model.AudienceGroup;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.model.RecordLifecycle;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.model.SiteCode;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.model.SourceChannel;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@code findAudienceGroupsByIds} against a real PostgreSQL.
 *
 * <p>Replaces the emergency-activation fan-out's old per-audience-group {@code findAudienceGroup} loop
 * - one {@code SELECT} per id, sequentially, on the life-safety broadcast path - with a single
 * {@code IN}-shaped query. This pins the batch lookup's correctness (all requested groups returned,
 * a missing id simply absent rather than an error) independent of {@code ActivationService}'s own
 * state-machine plumbing, which is what actually calls it.
 */
@EnabledIf(value = "gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available; see SafetySecurityPostgresSupport.unavailableReason()")
@SpringBootTest(properties = {"sfl.security.enabled=false"})
class JdbcEmergencyRepositoryAudienceGroupBatchTest extends SafetySecurityPostgresSupport {

    /** Overrides the auto-configured {@code JdbcTemplate} with a spy, so the test can count real calls. */
    @TestConfiguration
    static class SpyingJdbcTemplate {
        @Bean
        @Primary
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return spy(new JdbcTemplate(dataSource));
        }
    }

    @Autowired private EmergencyRepository repository;
    @Autowired private JdbcTemplate jdbc;

    /**
     * Spring caches the {@code ApplicationContext} (and so this spy bean) across every test method in
     * this class, so invocations recorded by one test would otherwise still be on the spy's tally when
     * the next test's {@code verify(...)} runs. Reset before each test so a call count assertion only
     * ever reflects that test's own calls.
     */
    @BeforeEach
    void resetSpyInvocations() {
        clearInvocations(jdbc);
    }

    private static String site() {
        return "ES" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private AudienceGroup save(String siteCode, int recipientCount) {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        return repository.saveAudienceGroup(new AudienceGroup(id, "GRP-" + id.toString().substring(0, 8), SiteCode.of(siteCode),
                "Group " + id, null, recipientCount, RecordLifecycle.ACTIVE,
                RecordMetadata.createdBy("tester", now, SourceChannel.WEB, "corr-" + id)));
    }

    @Test
    void returns_every_requested_group_that_exists_and_silently_omits_the_rest() {
        String siteCode = site();
        AudienceGroup first = save(siteCode, 40);
        AudienceGroup second = save(siteCode, 15);
        AudienceGroup third = save(siteCode, 7);
        UUID missing = UUID.randomUUID();

        List<AudienceGroup> found = repository.findAudienceGroupsByIds(
                List.of(first.id(), second.id(), third.id(), missing));

        assertThat(found).extracting(AudienceGroup::id)
                .containsExactlyInAnyOrder(first.id(), second.id(), third.id());
        assertThat(found).extracting(AudienceGroup::recipientCount).containsExactlyInAnyOrder(40, 15, 7);
    }

    @Test
    void an_empty_request_is_a_no_op_rather_than_an_unbounded_query() {
        assertThat(repository.findAudienceGroupsByIds(List.of())).isEmpty();
    }

    @Test
    void several_audience_groups_cost_exactly_one_query_not_one_per_group() {
        String siteCode = site();
        List<UUID> ids = List.of(save(siteCode, 1).id(), save(siteCode, 2).id(), save(siteCode, 3).id(),
                save(siteCode, 4).id(), save(siteCode, 5).id());

        List<AudienceGroup> found = repository.findAudienceGroupsByIds(ids);

        assertThat(found).hasSize(5);
        // One call to this particular JdbcTemplate.query overload for all five ids together, not five
        // separate single-id lookups - the fix this test pins. saveAudienceGroup/findAudienceGroup use
        // jdbc.update/queryForObject instead, so they cannot inflate this count.
        verify(jdbc, times(1)).query(org.mockito.ArgumentMatchers.any(PreparedStatementCreator.class),
                org.mockito.ArgumentMatchers.<RowMapper<AudienceGroup>>any());
    }
}
