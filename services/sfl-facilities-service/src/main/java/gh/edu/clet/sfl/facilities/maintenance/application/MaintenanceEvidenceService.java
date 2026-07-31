package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.maintenance.application.ports.MaintenanceRepository;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceEvidence;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closure evidence and its export — SRS-SFL-S153-03.
 *
 * <h2>What this service is careful about</h2>
 *
 * <ul>
 *   <li><strong>It never holds a file.</strong> Evidence is a reference and a hash; the bytes live in
 *       the document/object-storage service. That is the architecture standard's "evidence by
 *       reference" rule and it is also what makes this service's backups small enough to be real.</li>
 *   <li><strong>Export is an act, not a read.</strong> The SRS requires "role permission,
 *       justification and audit logging" for export, so it takes its own permission, refuses without
 *       a reason, records the recipient, and writes an audit entry <em>before</em> handing anything
 *       back. A reason recorded after a successful export is a reason that is missing exactly when
 *       the export failed halfway.</li>
 *   <li><strong>A legal hold suspends disposal, not classification.</strong> Setting one leaves the
 *       retention class alone so the original decision survives the hold being lifted.</li>
 * </ul>
 */
@Service
public class MaintenanceEvidenceService {

    private final MaintenanceRepository maintenance;
    private final WorkOrderApplicationService workOrders;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public MaintenanceEvidenceService(MaintenanceRepository maintenance,
            WorkOrderApplicationService workOrders, FacilitiesAuthorization authorization, AuditPort audit,
            IdempotencyPort idempotency, ServiceOutbox outbox, Clock clock) {
        this.maintenance = maintenance;
        this.workOrders = workOrders;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public MaintenanceEvidence attach(MaintenanceCommands.AttachEvidence command) {
        ActorContext actor = command.actor();
        // Reuses the work-order read, which carries the vendor assignment check: a contractor may
        // attach evidence to their own job and to nobody else's.
        WorkOrder order = workOrders.findById(command.workOrderId(), actor, command.channel());
        authorization.require(actor, SflPermission.FACILITIES_EVIDENCE_ATTACH, order.siteCode(),
                command.channel(), "MaintenanceEvidence", "new");

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<MaintenanceEvidence> replayed = idempotency
                    .findExistingResult("attach-maintenance-evidence", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(maintenance::findEvidence);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        if (!order.status().isOpen()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Evidence cannot be attached to a " + order.status() + " work order.");
        }

        MaintenanceEvidence evidence = maintenance.saveEvidence(MaintenanceEvidence.attach(UUID.randomUUID(),
                order.id(), order.siteCode(), command.evidenceType(), command.fileReference(),
                command.fileName(), command.mediaType(), command.sizeBytes(), command.contentHash(),
                command.retentionClass(), command.notes(), actor.actorId(), now()));

        audit.record(actor, command.channel(), AuditAction.EVIDENCE_ATTACHED, "MaintenanceEvidence",
                evidence.id().toString(), evidence.siteCode(), null, evidence);
        outbox.record("ifimp.maintenance-evidence.attached", 1, "MaintenanceEvidence", evidence.id(),
                evidence.siteCode(), actor.correlationId(), actor.actorId(), evidence);
        idempotency.recordResult("attach-maintenance-evidence", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), evidence.id(), evidence.siteCode(),
                actor.actorId());
        return evidence;
    }

    /**
     * Records an approved export and returns the reference the caller may fetch.
     *
     * <p>This service does not perform the export — it authorises it and writes the evidence that it
     * happened. The caller takes the returned reference to object storage. Splitting it that way is
     * what lets the audit entry be written inside the same transaction as the authorisation decision.
     */
    @Transactional
    public ExportGrant export(MaintenanceCommands.ExportEvidence command) {
        ActorContext actor = command.actor();
        MaintenanceEvidence evidence = requireEvidence(command.evidenceId());
        authorization.require(actor, SflPermission.FACILITIES_EVIDENCE_EXPORT, evidence.siteCode(),
                command.channel(), "MaintenanceEvidence", evidence.id().toString());

        if (command.reason() == null || command.reason().isBlank()) {
            audit.recordDenial(actor, command.channel(), "MaintenanceEvidence", evidence.id().toString(),
                    evidence.siteCode(), "Export attempted without a recorded reason");
            throw new FacilitiesException.ExportNotApprovedException(
                    "Evidence export requires a recorded reason.");
        }
        if (command.recipient() == null || command.recipient().isBlank()) {
            throw new FacilitiesException.ExportNotApprovedException(
                    "Evidence export requires a named recipient.");
        }

        Instant at = now();
        ExportGrant grant = new ExportGrant(evidence.id(), evidence.fileReference(), evidence.contentHash(),
                evidence.retentionClass(), command.recipient().strip(), command.reason().strip(),
                actor.actorId(), at);
        audit.record(actor, command.channel(), AuditAction.EVIDENCE_EXPORTED, "MaintenanceEvidence",
                evidence.id().toString(), evidence.siteCode(), null, grant);
        outbox.record("ifimp.maintenance-evidence.exported", 1, "MaintenanceEvidence", evidence.id(),
                evidence.siteCode(), actor.correlationId(), actor.actorId(), grant);
        return grant;
    }

    @Transactional
    public MaintenanceEvidence setLegalHold(MaintenanceCommands.SetLegalHold command) {
        ActorContext actor = command.actor();
        MaintenanceEvidence evidence = requireEvidence(command.evidenceId());
        // A hold suspends disposal, which is a compliance act rather than an operational one, so it
        // takes the export permission — the same one held only by reviewers.
        authorization.require(actor, SflPermission.FACILITIES_EVIDENCE_EXPORT, evidence.siteCode(),
                command.channel(), "MaintenanceEvidence", evidence.id().toString());
        if (command.reason() == null || command.reason().isBlank()) {
            throw new FacilitiesException.ValidationFailedException(
                    "A reason is required when placing or lifting a legal hold.");
        }

        MaintenanceEvidence held = maintenance.saveEvidence(evidence.withLegalHold(command.legalHold()));
        audit.record(actor, command.channel(), AuditAction.EVIDENCE_LEGAL_HOLD_CHANGED, "MaintenanceEvidence",
                held.id().toString(), held.siteCode(), evidence, held);
        return held;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceEvidence> forWorkOrder(UUID workOrderId, ActorContext actor, SourceChannel channel) {
        WorkOrder order = workOrders.findById(workOrderId, actor, channel);
        authorization.require(actor, SflPermission.FACILITIES_EVIDENCE_READ, order.siteCode(), channel,
                "MaintenanceEvidence", workOrderId.toString());
        return maintenance.findEvidenceFor(order.id());
    }

    @Transactional(readOnly = true)
    public MaintenanceEvidence findById(UUID id, ActorContext actor, SourceChannel channel) {
        MaintenanceEvidence evidence = requireEvidence(id);
        authorization.require(actor, SflPermission.FACILITIES_EVIDENCE_READ, evidence.siteCode(), channel,
                "MaintenanceEvidence", id.toString());
        // Re-reads the work order so a contractor cannot reach evidence on somebody else's job by id.
        workOrders.findById(evidence.workOrderId(), actor, channel);
        return evidence;
    }

    private MaintenanceEvidence requireEvidence(UUID id) {
        return maintenance.findEvidence(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Maintenance evidence", id));
    }

    private Instant now() {
        return clock.instant();
    }

    /** What an approved export hands back, and what the audit trail records that it handed back. */
    public record ExportGrant(
            UUID evidenceId,
            String fileReference,
            String contentHash,
            gh.edu.clet.sfl.facilities.maintenance.domain.RetentionClass retentionClass,
            String recipient,
            String reason,
            String approvedBy,
            Instant approvedAt) {
    }
}
