package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchEvidencePort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchManifestItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.ReturnReconciliation;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.ReturnReconciliationPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S171-06: return-leg / reverse-logistics reconciliation. Returned items are reconciled against the
 * original dispatch manifest. A matched return completes custody; a shortfall, extra or broken seal opens
 * a {@code RETURN_DISCREPANCY} exception and blocks custody closure. Outstanding (not-yet-returned) items
 * are escalated by the scheduled sweep after their configurable window.
 */
@Service
public class DispatchReturnService {

    private final DispatchRepository repository;
    private final DispatchAccessPolicy access;
    private final DispatchEvidencePort evidence;
    private final DispatchExceptionService exceptions;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public DispatchReturnService(DispatchRepository repository, DispatchAccessPolicy access,
            DispatchEvidencePort evidence, DispatchExceptionService exceptions, AuditPort audit,
            IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.evidence = evidence;
        this.exceptions = exceptions;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record ReconcileReturn(UUID dispatchId, Integer expectedCount, int returnedCount, int brokenSeals,
            String notes, EvidenceMeta evidence, ActorContext actor, SourceChannel channel) {}

    @Transactional
    public ReturnReconciliation reconcile(ReconcileReturn c) {
        var dispatch = requireDispatch(c.dispatchId());
        access.require(c.actor(), SflPermission.DISPATCH_RETURN_RECONCILE, dispatch.siteCode().value(),
                "ReturnReconciliation", c.dispatchId().toString());
        int expected = c.expectedCount() == null ? dispatch.itemCount() : c.expectedCount();
        var evaluation = ReturnReconciliationPolicy.evaluate(expected, c.returnedCount(), c.brokenSeals());
        Instant now = clock.instant();
        UUID evidenceId = DispatchEvidenceSupport.registerIfPresent(evidence, dispatch.siteCode(),
                "ReturnReconciliation", c.dispatchId().toString(), "RETURN_RECONCILIATION", c.evidence(), c.actor(),
                c.channel());
        var meta = RecordMetadata.createdBy(c.actor().actorId(), now, c.channel(), c.actor().correlationId());
        var reconciliation = new ReturnReconciliation(UUID.randomUUID(), c.dispatchId(), dispatch.siteCode(), expected,
                c.returnedCount(), evaluation.shortfall(), evaluation.extras(), c.brokenSeals(), evaluation.outcome(),
                c.notes(), evidenceId, c.actor().actorId(), now, meta);
        var saved = repository.saveReturn(reconciliation);
        audit.record(c.actor(), c.channel(), dispatch.siteCode(), AuditAction.CREATE, "ReturnReconciliation",
                saved.id().toString(), null, saved);
        if (saved.matched()) {
            for (var manifestItem : repository.findManifestItems(c.dispatchId())) {
                if (manifestItem.returnStatus() != DispatchManifestItem.ReturnStatus.RETURNED) {
                    repository.saveManifestItem(manifestItem.markReturned(now, SealState.INTACT));
                }
            }
            markReconciled(dispatch, now, c.actor(), c.channel());
            events.publish(FleetEventType.DISPATCH_RETURN_RECONCILED, "ReturnReconciliation", saved.id().toString(),
                    dispatch.siteCode(), c.actor(), Map.of("dispatchId", c.dispatchId(), "returnedCount",
                            saved.returnedCount(), "expectedCount", saved.expectedCount()));
        } else {
            List<String> reasons = new ArrayList<>();
            if (saved.shortfall() > 0) reasons.add("SHORTFALL(" + saved.shortfall() + ")");
            if (saved.extras() > 0) reasons.add("EXTRAS(" + saved.extras() + ")");
            if (saved.brokenSeals() > 0) reasons.add("BROKEN_SEALS(" + saved.brokenSeals() + ")");
            events.publish(FleetEventType.DISPATCH_RETURN_DISCREPANCY, "ReturnReconciliation", saved.id().toString(),
                    dispatch.siteCode(), c.actor(), Map.of("dispatchId", c.dispatchId(), "shortfall", saved.shortfall(),
                            "extras", saved.extras(), "brokenSeals", saved.brokenSeals()));
            exceptions.openCase(new DispatchExceptionService.OpenCase(dispatch.siteCode().value(),
                    DispatchExceptionCase.Type.RETURN_DISCREPANCY,
                    saved.brokenSeals() > 0 ? DispatchExceptionCase.Severity.HIGH
                            : DispatchExceptionCase.Severity.MEDIUM,
                    saved.brokenSeals() > 0, "RETURN_DISCREPANCY:" + saved.id(), null, c.dispatchId(), null, null,
                    dispatch.tripId(), reasons, c.actor(), c.channel()));
        }
        return saved;
    }

    /** Scheduled/authorised escalation of an outstanding (not-yet-returned) item after its window. Idempotent. */
    @Transactional
    public void escalateOutstanding(UUID dispatchId, UUID manifestItemId, UUID courierItemId, ActorContext actor,
            SourceChannel channel) {
        var dispatch = repository.findDispatch(dispatchId).orElse(null);
        if (dispatch == null) return;
        repository.findManifestItems(dispatchId).stream()
                .filter(mi -> mi.id().equals(manifestItemId)
                        && mi.returnStatus() == DispatchManifestItem.ReturnStatus.PENDING)
                .findFirst()
                .ifPresent(mi -> repository.saveManifestItem(mi.markOutstanding()));
        exceptions.openCase(new DispatchExceptionService.OpenCase(dispatch.siteCode().value(),
                DispatchExceptionCase.Type.RETURN_DISCREPANCY, DispatchExceptionCase.Severity.MEDIUM, false,
                "OUTSTANDING_RETURN:" + manifestItemId, courierItemId, dispatchId, null, null, dispatch.tripId(),
                List.of("OUTSTANDING_RETURN_WINDOW_ELAPSED"), actor, channel));
    }

    private void markReconciled(Dispatch dispatch, Instant now, ActorContext actor, SourceChannel channel) {
        if (dispatch.status() != Dispatch.Status.RECEIVED && dispatch.status() != Dispatch.Status.RETURNED) {
            return;
        }
        var meta = dispatch.metadata().modifiedBy(actor.actorId(), now, channel, actor.correlationId());
        repository.saveDispatch(dispatch.reconciled(now, meta));
    }

    public ReturnReconciliation reconciliation(UUID id, ActorContext actor) {
        var reconciliation = repository.findReturn(id)
                .orElseThrow(() -> RecordNotFoundException.of("ReturnReconciliation", id));
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, reconciliation.siteCode().value(),
                "ReturnReconciliation", id.toString());
        return reconciliation;
    }

    public List<ReturnReconciliation> reconciliations(UUID dispatchId, ActorContext actor) {
        var dispatch = requireDispatch(dispatchId);
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, dispatch.siteCode().value(),
                "ReturnReconciliation", dispatchId.toString());
        return repository.findReturns(dispatchId);
    }

    private Dispatch requireDispatch(UUID id) {
        return repository.findDispatch(id).orElseThrow(() -> RecordNotFoundException.of("Dispatch", id));
    }
}
