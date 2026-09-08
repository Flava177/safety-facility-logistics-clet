package gh.edu.clet.sfl.safetysecurity.incident.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport;
import gh.edu.clet.sfl.safetysecurity.incident.application.port.SecurityIncidentRepository;
import gh.edu.clet.sfl.safetysecurity.incident.application.service.CorrectiveActionService;
import gh.edu.clet.sfl.safetysecurity.incident.application.service.IncidentInvestigationService;
import gh.edu.clet.sfl.safetysecurity.incident.application.service.IncidentReportingService;
import gh.edu.clet.sfl.safetysecurity.incident.application.service.IncidentTriageService;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.CorrectiveAction;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Impact;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Likelihood;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RiskRating;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Probes the CAPA-closure TOCTOU window {@link
 * gh.edu.clet.sfl.safetysecurity.incident.infrastructure.persistence.CorrectiveActionJpaRepository#lockOpenMandatoryForClosure}
 * narrowed and {@code SERIALIZABLE} isolation (see {@code IncidentClosureService}'s class Javadoc)
 * closes: under default READ COMMITTED isolation, a plain {@code countOpenMandatory} read taken early
 * in the closing transaction cannot see a mandatory CAPA opened and committed by a different
 * transaction afterwards but before the closing transaction itself commits.
 *
 * <p>Same deterministic-overlap technique as {@code SecurityIncidentOptimisticLockingEndToEndTest}:
 * the closing transaction opens, takes its count read, and then - still uncommitted - drives a
 * second, {@code PROPAGATION_REQUIRES_NEW} transaction that opens a brand-new mandatory CAPA for the
 * same incident and commits it, before the closing transaction proceeds to decide and commit. The
 * closing logic is inlined here (rather than calling {@code IncidentClosureService.close} directly)
 * precisely so a second transaction can be driven to completion in the middle of it - exactly what
 * the real service method, running as one atomic {@code @Transactional} block, does not allow a test
 * to do from outside. Both the inlined closing transaction and the real {@code
 * CorrectiveActionService#open} call it drives are explicitly run at {@code SERIALIZABLE}: Postgres's
 * guarantee only holds across a set of transactions that are all serializable, and the inlined
 * transaction here is a {@code TransactionTemplate}, not the annotated method, so it does not inherit
 * an isolation level from anywhere.
 *
 * <p><b>What actually happens, and why either outcome is safe:</b> the closing transaction's read of
 * "open mandatory CAPAs for this incident" and the CAPA-open transaction's read of "this incident"
 * form a two-edge rw-dependency cycle once both have run concurrently, and Postgres aborts whichever
 * side completes the cycle with a serialization failure (SQLSTATE 40001, surfaced here as {@link
 * ConcurrencyFailureException}) - which side is implementation-defined, so this test does not assume
 * one. If the CAPA-open transaction is the one aborted, its exception propagates out through this
 * test's closing lambda and rolls that transaction back too, so neither change lands: the incident
 * stays open and no CAPA exists, and a caller retrying either request from a fresh read succeeds
 * normally. If the closing transaction is the one aborted instead, the CAPA-open committed and the
 * incident closure did not, so the incident is left open with a mandatory CAPA correctly blocking it.
 * Either way, the one outcome that must never occur - the incident closed while a mandatory CAPA it
 * never saw is left open against it - does not happen, which is what this test asserts.
 */
@SpringBootTest(properties = {"sfl.security.enabled=false"})
@EnabledIf(value = "gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available")
class IncidentClosureCapaGateConcurrencyEndToEndTest extends SafetySecurityPostgresSupport {

    @Autowired
    private IncidentReportingService reporting;
    @Autowired
    private IncidentTriageService triage;
    @Autowired
    private IncidentInvestigationService investigation;
    @Autowired
    private CorrectiveActionService correctiveActions;
    @Autowired
    private SecurityIncidentRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final String SITE = "E2E-HQ";

    private ActorContext actor(String id, SflRole role) {
        return new ActorContext(new SiteScopedPrincipal(id, id, Set.of(role), Set.of(SITE), false),
                "e2e-" + UUID.randomUUID());
    }

    private SecurityIncident investigatingIncidentWithNoCapaYet() {
        SecurityIncident reported = reporting.report(new IncidentReportingService.ReportIncident(SITE,
                IncidentSource.HSE, false, "reporter-e2e", "reporter@example.com",
                "CAPA-gate concurrency probe.", true, actor("hse-e2e", SflRole.HSE_MANAGER), SourceChannel.WEB));
        SecurityIncident triaged = triage.triage(new IncidentTriageService.Triage(reported.id(), Severity.HIGH,
                new RiskRating(Likelihood.LIKELY, Impact.MAJOR), false, null, reported.metadata().version(),
                actor("hse-e2e", SflRole.HSE_MANAGER), SourceChannel.WEB));
        return investigation.openOrUpdateInvestigation(new IncidentInvestigationService.OpenOrUpdateInvestigation(
                triaged.id(), "investigator-e2e", "Root cause under review.", triaged.metadata().version(),
                actor("investigator-e2e", SflRole.INCIDENT_INVESTIGATOR), SourceChannel.WEB));
    }

    @Test
    void a_mandatory_capa_opened_while_a_closure_is_mid_transaction_is_now_caught() {
        SecurityIncident investigating = investigatingIncidentWithNoCapaYet();

        TransactionTemplate txClose = new TransactionTemplate(transactionManager);
        txClose.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        txClose.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        TransactionTemplate txCapaOpen = new TransactionTemplate(transactionManager);
        txCapaOpen.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // CorrectiveActionService#open is itself @Transactional(isolation = SERIALIZABLE), but this
        // template - not the annotated method - is what actually begins the physical transaction
        // (open() then just joins it under the default REQUIRED propagation), so the isolation has to
        // be requested here too for Postgres to actually start the transaction at that level.
        txCapaOpen.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);

        assertThatThrownBy(() -> txClose.executeWithoutResult(status -> {
            // Inlines what IncidentClosureService.close does, step for step, so a second transaction
            // can be driven to completion between the (row-locking) count read and this transaction's
            // own save/commit - which calling the service method as one atomic unit would not allow a
            // test to do.
            SecurityIncident current = repository.findIncident(investigating.id()).orElseThrow();
            long openMandatory = repository.countOpenMandatoryCorrectiveActions(current.id());
            assertThat(openMandatory).isZero();

            // A concurrent transaction opens a brand-new mandatory CAPA for the same incident and
            // commits it in full, while the closing transaction above is still open. If Postgres
            // aborts this side of the cycle, the exception propagates from here, through this lambda,
            // and rolls the closing transaction back too - both sides fail together, which is safe.
            CorrectiveAction openedConcurrently = txCapaOpen.execute(statusCapa -> correctiveActions.open(
                    new CorrectiveActionService.OpenCorrectiveAction(current.id(),
                            "Opened concurrently with a racing closure attempt.", "supervisor-e2e",
                            LocalDate.now().plusDays(14), true,
                            actor("investigator-e2e", SflRole.INCIDENT_INVESTIGATOR), SourceChannel.WEB)));
            assertThat(openedConcurrently.blocksClosure()).isTrue();

            SecurityIncident closed = current.close("Closing on the count read before the concurrent CAPA existed.",
                    openMandatory, "investigator-e2e", Instant.now(), SourceChannel.WEB, "corr-close");
            repository.saveIncident(closed);
            // If this side of the cycle is the one Postgres aborts instead, it happens at commit -
            // after this lambda returns normally - so TransactionTemplate.executeWithoutResult still
            // throws, just from the commit rather than from a line inside here.
        })).isInstanceOf(ConcurrencyFailureException.class);

        TransactionTemplate txRead = new TransactionTemplate(transactionManager);
        SecurityIncident finalState = txRead.execute(status -> repository.findIncident(investigating.id())
                .orElseThrow());
        // countOpenMandatoryCorrectiveActions acquires a row lock (see
        // CorrectiveActionJpaRepository#lockOpenMandatoryForClosure), which Hibernate requires an
        // active transaction for even on a read - unlike the plain COUNT query this replaced.
        long stillOpenMandatory = txRead.execute(status -> repository.countOpenMandatoryCorrectiveActions(
                investigating.id()));

        // The one outcome that must never occur, regardless of which side Postgres chose to abort: a
        // closed incident with a mandatory CAPA it never saw still open against it.
        boolean closedWithAnOpenMandatoryCapaItMissed = "CLOSED".equals(finalState.status().name())
                && stillOpenMandatory > 0;
        assertThat(closedWithAnOpenMandatoryCapaItMissed)
                .as("incident status=%s, open mandatory CAPAs=%d", finalState.status(), stillOpenMandatory)
                .isFalse();
    }
}
