package gh.edu.clet.sfl.safetysecurity.incident.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport;
import gh.edu.clet.sfl.safetysecurity.incident.application.port.SecurityIncidentRepository;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the database-level compare-and-swap the {@code record_version} {@code @Version} field
 * exists for: two writers whose transactions both read the same row before either commits must not
 * both succeed, and the version handed back to a caller after a successful write must match what is
 * actually committed.
 *
 * <p>Two genuinely overlapping transactions are simulated deterministically, without threads: writer
 * B's transaction opens, reads the row (caching it, at version 0, in its own persistence context),
 * and then - still without committing - drives writer A's transaction to completion on a
 * {@code PROPAGATION_REQUIRES_NEW} template, which reads the same row independently, writes, and
 * commits (version 0 to 1). Only then does B attempt its own write and commit, using the entity it
 * cached back when the row was still at version 0. This is not a simulation of "B read late" - B's
 * persistence context genuinely still holds the pre-A snapshot, exactly as it would if a second
 * database connection had been reading and writing concurrently with A's transaction.
 */
@SpringBootTest(properties = {"sfl.security.enabled=false"})
@EnabledIf(value = "gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available")
class SecurityIncidentOptimisticLockingEndToEndTest extends SafetySecurityPostgresSupport {

    @Autowired
    private SecurityIncidentRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final String SITE = "E2E-LOCK";

    @Test
    void two_writers_whose_transactions_both_read_the_row_do_not_both_succeed() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID id = tx.execute(status -> repository.saveIncident(freshlyReported()).id());

        TransactionTemplate txB = new TransactionTemplate(transactionManager);
        txB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate txA = new TransactionTemplate(transactionManager);
        txA.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Instant at = Instant.now();
        assertThatThrownBy(() -> txB.executeWithoutResult(statusB -> {
            // B reads and caches the row (version 0) in its own, still-open persistence context.
            SecurityIncident loadedByB = repository.findIncident(id).orElseThrow();

            // A's entire transaction - its own read, write and commit - runs independently while B's
            // transaction is suspended, not finished. The database now holds version 1.
            txA.executeWithoutResult(statusA -> {
                SecurityIncident current = repository.findIncident(id).orElseThrow();
                SecurityIncident triaged = current.triage(
                        Severity.LOW, null, false, null,
                        "writer-a", at, SourceChannel.WEB, "corr-a");
                repository.saveIncident(triaged);
            });

            // B resumes and writes using the entity it cached before A ever ran.
            SecurityIncident staleFromB = loadedByB.triage(
                    Severity.HIGH, null, false, null,
                    "writer-b", at, SourceChannel.WEB, "corr-b");
            repository.saveIncident(staleFromB);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        SecurityIncident finalState = tx.execute(status -> repository.findIncident(id).orElseThrow());
        assertThat(finalState.severity()).isEqualTo(Severity.LOW);
        assertThat(finalState.metadata().version()).isEqualTo(1L);
    }

    @Test
    void the_version_returned_after_a_successful_save_matches_what_is_committed() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        SecurityIncident created = tx.execute(status -> repository.saveIncident(freshlyReported()));
        assertThat(created.metadata().version()).isEqualTo(0L);

        SecurityIncident updated = tx.execute(status -> {
            SecurityIncident current = repository.findIncident(created.id()).orElseThrow();
            SecurityIncident triaged = current.triage(Severity.LOW,
                    null, false, null, "writer-a", Instant.now(), SourceChannel.WEB, "corr-a");
            return repository.saveIncident(triaged);
        });
        assertThat(updated.metadata().version()).isEqualTo(1L);

        // A second read, in its own transaction, must see the same version a save() already returned -
        // proving the returned aggregate was not ahead of (or behind) what is actually persisted.
        SecurityIncident reread = tx.execute(status -> repository.findIncident(created.id()).orElseThrow());
        assertThat(reread.metadata().version()).isEqualTo(1L);
    }

    private SecurityIncident freshlyReported() {
        UUID id = UUID.randomUUID();
        return SecurityIncident.report(id, SITE, IncidentSource.HSE, "INC-" + id.toString().substring(0, 8), false,
                "reporter", "reporter@example.com", "Optimistic locking regression probe.", true, "reporter-actor",
                Instant.now(), SourceChannel.WEB, "corr-" + id);
    }
}
