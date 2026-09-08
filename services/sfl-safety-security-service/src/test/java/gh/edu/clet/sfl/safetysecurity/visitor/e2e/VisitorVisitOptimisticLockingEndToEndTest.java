package gh.edu.clet.sfl.safetysecurity.visitor.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitPurpose;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * The visitor-module counterpart to {@code SecurityIncidentOptimisticLockingEndToEndTest} - same
 * {@code record_version} {@code @Version} fix, same proof technique. See that class for why the
 * transactions are structured this way.
 */
@SpringBootTest(properties = {"sfl.security.enabled=false"})
@EnabledIf(value = "gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available")
class VisitorVisitOptimisticLockingEndToEndTest extends SafetySecurityPostgresSupport {

    @Autowired
    private VisitorRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final String SITE = "E2E-LOCK";

    @Test
    void two_writers_whose_transactions_both_read_the_row_do_not_both_succeed() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID id = tx.execute(status -> repository.saveVisit(freshlyPreRegistered()).id());

        TransactionTemplate txB = new TransactionTemplate(transactionManager);
        txB.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate txA = new TransactionTemplate(transactionManager);
        txA.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Instant at = Instant.now();
        assertThatThrownBy(() -> txB.executeWithoutResult(statusB -> {
            // B reads and caches the row (version 0) in its own, still-open persistence context.
            VisitorVisit loadedByB = repository.findVisit(id).orElseThrow();

            // A's entire transaction - its own read, write and commit - runs independently while B's
            // transaction is suspended, not finished. The database now holds version 1.
            txA.executeWithoutResult(statusA -> {
                VisitorVisit current = repository.findVisit(id).orElseThrow();
                VisitorVisit cancelled = current.cancel("Host unavailable.", "writer-a", at, SourceChannel.WEB,
                        "corr-a");
                repository.saveVisit(cancelled);
            });

            // B resumes and writes using the entity it cached before A ever ran.
            VisitorVisit staleFromB = loadedByB.cancel("Visitor withdrew.", "writer-b", at, SourceChannel.WEB,
                    "corr-b");
            repository.saveVisit(staleFromB);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        VisitorVisit finalState = tx.execute(status -> repository.findVisit(id).orElseThrow());
        assertThat(finalState.closureReason()).isEqualTo("Host unavailable.");
        assertThat(finalState.metadata().version()).isEqualTo(1L);
    }

    @Test
    void the_version_returned_after_a_successful_save_matches_what_is_committed() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        VisitorVisit created = tx.execute(status -> repository.saveVisit(freshlyPreRegistered()));
        assertThat(created.metadata().version()).isEqualTo(0L);

        VisitorVisit updated = tx.execute(status -> {
            VisitorVisit current = repository.findVisit(created.id()).orElseThrow();
            VisitorVisit cancelled = current.cancel("Host unavailable.", "writer-a", Instant.now(),
                    SourceChannel.WEB, "corr-a");
            return repository.saveVisit(cancelled);
        });
        assertThat(updated.metadata().version()).isEqualTo(1L);

        VisitorVisit reread = tx.execute(status -> repository.findVisit(created.id()).orElseThrow());
        assertThat(reread.metadata().version()).isEqualTo(1L);
        assertThat(reread.status()).isEqualTo(VisitStatus.CANCELLED);
    }

    private VisitorVisit freshlyPreRegistered() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return VisitorVisit.preRegister(id, SITE, "Visitor E2E", "Org", "visitor@example.com", "host-e2e",
                "Host E2E", VisitPurpose.DELIVERY, now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS),
                false, "reception-e2e", now, SourceChannel.WEB, "corr-" + id);
    }
}
