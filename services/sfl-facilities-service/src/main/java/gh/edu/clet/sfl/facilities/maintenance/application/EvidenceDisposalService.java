package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceEvidence;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disposes of closure evidence whose retention period has run out — SRS-SFL-S153-03.
 *
 * <p>Retention classes have been recorded and {@code disposalEligibleFrom} computed since S153 shipped,
 * and {@code ix_maintenance_evidence_retention} was added for exactly this query. Nothing ran it. The
 * gap report said, correctly, that a sweep which deletes evidence should not ship in the same round
 * that first defines what the retention classes mean. That round is past.
 *
 * <p><strong>It removes the reference, not the record.</strong> A retention policy has to prove two
 * different things — that a thing was destroyed when it should have been, and that it existed and was
 * destroyed for a stated reason — and deleting the row proves neither. What survives is the hash, the
 * retention class, who uploaded it and when, plus the disposal date and reason. An auditor asking what
 * happened to a closure photograph gets an answer instead of a silence indistinguishable from the
 * evidence never having been captured.
 *
 * <p><strong>A legal hold beats the clock.</strong> Held evidence is not a candidate, and the aggregate
 * refuses disposal outright rather than letting the sweep decide — two layers, because this is the one
 * rule here whose failure is not recoverable.
 *
 * <p><strong>It is deliberately conservative about batch size and it audits every single act.</strong>
 * Disposal is the only irreversible operation in this service. A sweep that quietly destroyed ten
 * thousand references because a retention class was mis-configured would be indistinguishable, in the
 * log, from one that correctly destroyed ten thousand references.
 */
@Service
public class EvidenceDisposalService {

    /**
     * A deliberately small bound. Every other sweep in this module uses 500; this one destroys things,
     * so a misconfiguration costs a hundred references per run rather than five hundred, and the next
     * run picks up the rest once somebody has had a chance to notice.
     */
    private static final int SWEEP_LIMIT = 100;

    private final MaintenanceRepository maintenance;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public EvidenceDisposalService(MaintenanceRepository maintenance, AuditPort audit,
            ServiceOutbox outbox, Clock clock) {
        this.maintenance = maintenance;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Disposes of everything past its retention date.
     *
     * @param systemActor the actor the disposals are recorded against — not a person, and the audit
     *        trail says so through {@link SourceChannel#SCHEDULER}
     * @return how many references were cleared, so an operator triggering this by hand sees the effect
     *         rather than inferring it from a log line
     */
    @Transactional
    public int sweep(ActorContext systemActor) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        int disposed = 0;

        for (MaintenanceEvidence candidate : maintenance.findDisposalCandidates(SWEEP_LIMIT)) {
            if (!candidate.isDisposalEligible(today)) {
                continue;
            }
            String reason = "Retention period elapsed for class " + candidate.retentionClass()
                    + "; eligible from " + candidate.disposalEligibleFrom();
            MaintenanceEvidence gone = maintenance.saveEvidence(candidate.disposed(now, reason));

            // Audited per item, never per batch. "The sweep ran" is not evidence that a particular
            // photograph was destroyed lawfully; this record is.
            audit.record(systemActor, SourceChannel.SCHEDULER, AuditAction.EVIDENCE_DISPOSED,
                    "MaintenanceEvidence", gone.id().toString(), gone.siteCode(), candidate, gone);
            outbox.record("sfl.ifimp.maintenance-evidence-disposed.v1", 1, "MaintenanceEvidence", gone.id(),
                    gone.siteCode(), systemActor.correlationId(), systemActor.actorId(), gone);
            disposed++;
        }
        return disposed;
    }
}
