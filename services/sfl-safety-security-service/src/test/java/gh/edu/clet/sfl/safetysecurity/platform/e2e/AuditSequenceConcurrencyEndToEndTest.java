package gh.edu.clet.sfl.safetysecurity.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the {@code audit_log_sequence_no_seq} fix (V13) for the bug {@link
 * gh.edu.clet.sfl.safetysecurity.platform.infrastructure.persistence.AuditAdapter} used to have: two
 * audit writes racing to compute {@code SELECT MAX(sequence_no)+1} before either committed could land
 * on the same "next" value, and {@code UNIQUE(sequence_no)} then failed whichever one inserted second
 * - aborting an otherwise-valid business transaction that had nothing to do with the audit write
 * itself.
 *
 * <p>Same deterministic-overlap technique as {@code SecurityIncidentOptimisticLockingEndToEndTest}:
 * writer B's transaction opens and writes its own audit record first, without committing. While B is
 * still open, writer A's entire transaction - its own audit write and commit - runs to completion on
 * a {@code PROPAGATION_REQUIRES_NEW} template. Only then does B commit. Because {@code nextval()} on
 * a Postgres sequence is not transactional, A's call still draws a distinct value even though B's
 * insert (using the value drawn just before it) has not committed yet - exactly the interleaving that
 * used to make both writers compute the same "next" value under the old {@code MAX+1} read.
 */
@SpringBootTest(properties = {"sfl.security.enabled=false"})
@EnabledIf(value = "gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available")
class AuditSequenceConcurrencyEndToEndTest extends SafetySecurityPostgresSupport {

    @Autowired
    private AuditPort audit;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void two_overlapping_audit_writes_from_different_subdomains_both_succeed_with_distinct_sequence_numbers() {
        TransactionTemplate txB = new TransactionTemplate(transactionManager);
        txB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate txA = new TransactionTemplate(transactionManager);
        txA.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        String resourceIdB = "VISITOR-" + UUID.randomUUID();
        String resourceIdA = "INCIDENT-" + UUID.randomUUID();

        assertThatCode(() -> txB.executeWithoutResult(statusB -> {
            // B (visitor check-in) writes its own audit record but has not committed yet.
            audit.record(actor("writer-b"), "API", "SITE-B", "VISITOR_CHECK_IN", "VisitorVisit", resourceIdB,
                    null, null, "concurrency probe B");

            // A (incident triage) runs its entire transaction - audit write and commit - independently
            // while B's transaction is still open and uncommitted.
            txA.executeWithoutResult(statusA -> audit.record(actor("writer-a"), "API", "SITE-A",
                    "INCIDENT_TRIAGED", "SecurityIncident", resourceIdA, null, null, "concurrency probe A"));

            // B resumes and commits after A has already committed with its own sequence number.
        })).doesNotThrowAnyException();

        Long sequenceA = jdbc.queryForObject(
                "SELECT sequence_no FROM safety_security.audit_log WHERE resource_id=?", Long.class, resourceIdA);
        Long sequenceB = jdbc.queryForObject(
                "SELECT sequence_no FROM safety_security.audit_log WHERE resource_id=?", Long.class, resourceIdB);

        assertThat(sequenceA).isNotNull();
        assertThat(sequenceB).isNotNull();
        assertThat(sequenceA).isNotEqualTo(sequenceB);
    }

    private ActorContext actor(String id) {
        return new ActorContext(
                new SiteScopedPrincipal(id, id, Set.of(SflRole.SFL_ADMIN), Set.of("SITE-A", "SITE-B"), false),
                "e2e-" + UUID.randomUUID());
    }
}
