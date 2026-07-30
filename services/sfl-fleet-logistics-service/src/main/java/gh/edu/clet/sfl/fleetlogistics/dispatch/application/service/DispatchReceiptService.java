package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchEvidencePort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchReceipt;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.ReceiptVariancePolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S171-03: destination receipt confirmation and variance handling. A receipt verifies seal integrity,
 * item count and recipient signature against the manifest. A clean receipt completes the destination leg;
 * any variance opens a {@code RECEIPT_VARIANCE} exception (seal/tamper variants are security-relevant and
 * surface to SSEMP) and blocks closure. Edge-captured receipts replay idempotently by
 * {@code (dispatchId, captureCorrelationId)} so an offline capture reconciles without double-apply.
 */
@Service
public class DispatchReceiptService {

    private final DispatchRepository repository;
    private final DispatchAccessPolicy access;
    private final DispatchEvidencePort evidence;
    private final DispatchExceptionService exceptions;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public DispatchReceiptService(DispatchRepository repository, DispatchAccessPolicy access,
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

    public record ConfirmReceipt(UUID dispatchId, SealState sealState, boolean sealVerified, Integer expectedCount,
            int verifiedCount, String recipientName, String expectedRecipient, EvidenceMeta signature,
            String captureCorrelationId, boolean edgeCaptured, Instant capturedAt, ActorContext actor,
            SourceChannel channel) {}

    @Transactional
    public DispatchReceipt confirmReceipt(ConfirmReceipt c) {
        var dispatch = requireDispatch(c.dispatchId());
        access.require(c.actor(), SflPermission.DISPATCH_RECEIPT_CONFIRM, dispatch.siteCode().value(),
                "DispatchReceipt", c.dispatchId().toString());
        String captureCorrelationId = c.captureCorrelationId() == null || c.captureCorrelationId().isBlank()
                ? "RCP-" + c.dispatchId() : c.captureCorrelationId().strip();
        // Idempotent edge capture: a replayed offline receipt returns the original result, never double-applies.
        var existing = repository.findReceiptByCapture(c.dispatchId(), captureCorrelationId);
        if (existing.isPresent()) {
            return existing.get();
        }
        int expected = c.expectedCount() == null ? dispatch.itemCount() : c.expectedCount();
        Instant now = clock.instant();
        UUID signatureEvidenceId = DispatchEvidenceSupport.registerIfPresent(evidence, dispatch.siteCode(),
                "DispatchReceipt", c.dispatchId().toString(), "RECEIPT_SIGNATURE", c.signature(), c.actor(),
                c.channel());
        var evaluation = ReceiptVariancePolicy.evaluate(c.sealState(), expected, c.verifiedCount(), c.recipientName(),
                c.expectedRecipient(), signatureEvidenceId != null);
        var meta = RecordMetadata.createdBy(c.actor().actorId(), now, c.channel(), c.actor().correlationId());
        var receipt = new DispatchReceipt(UUID.randomUUID(), c.dispatchId(), dispatch.siteCode(), c.sealState(),
                c.sealVerified(), expected, c.verifiedCount(), c.recipientName(), signatureEvidenceId,
                evaluation.outcome(), evaluation.type(), c.capturedAt() == null ? now : c.capturedAt(),
                c.edgeCaptured(), captureCorrelationId, c.edgeCaptured() ? now : null, meta);
        var saved = repository.saveReceipt(receipt);
        markDispatchReceived(dispatch, now, c.actor(), c.channel());
        audit.record(c.actor(), c.channel(), dispatch.siteCode(), AuditAction.CREATE, "DispatchReceipt",
                saved.id().toString(), null, saved);
        if (saved.clean()) {
            events.publish(FleetEventType.DISPATCH_RECEIVED, "DispatchReceipt", saved.id().toString(),
                    dispatch.siteCode(), c.actor(), Map.of("dispatchId", c.dispatchId(), "receiptId", saved.id(),
                            "verifiedCount", saved.verifiedCount()));
        } else {
            events.publish(FleetEventType.DISPATCH_RECEIPT_VARIANCE, "DispatchReceipt", saved.id().toString(),
                    dispatch.siteCode(), c.actor(), Map.of("dispatchId", c.dispatchId(), "receiptId", saved.id(),
                            "varianceType", saved.varianceType(), "securityRelevant", saved.securityRelevant()));
            exceptions.openCase(new DispatchExceptionService.OpenCase(dispatch.siteCode().value(),
                    DispatchExceptionCase.Type.RECEIPT_VARIANCE,
                    saved.securityRelevant() ? DispatchExceptionCase.Severity.HIGH
                            : DispatchExceptionCase.Severity.MEDIUM,
                    saved.securityRelevant(), "RECEIPT_VARIANCE:" + saved.id(), null, c.dispatchId(), null,
                    saved.id(), dispatch.tripId(), evaluation.reasons(), c.actor(), c.channel()));
        }
        return saved;
    }

    private void markDispatchReceived(Dispatch dispatch, Instant now, ActorContext actor, SourceChannel channel) {
        if (dispatch.status() != Dispatch.Status.DISPATCHED && dispatch.status() != Dispatch.Status.IN_TRANSIT) {
            return;
        }
        var meta = dispatch.metadata().modifiedBy(actor.actorId(), now, channel, actor.correlationId());
        repository.saveDispatch(dispatch.received(now, meta));
    }

    public DispatchReceipt receipt(UUID id, ActorContext actor) {
        var receipt = repository.findReceipt(id).orElseThrow(() -> RecordNotFoundException.of("DispatchReceipt", id));
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, receipt.siteCode().value(), "DispatchReceipt",
                id.toString());
        return receipt;
    }

    public List<DispatchReceipt> receipts(UUID dispatchId, ActorContext actor) {
        var dispatch = requireDispatch(dispatchId);
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, dispatch.siteCode().value(), "DispatchReceipt",
                dispatchId.toString());
        return repository.findReceipts(dispatchId);
    }

    /**
     * Receipts across a site's consignments.
     *
     * <p>Closes gap 7. "Every variance this month" was previously a manifest-by-manifest hunt.
     */
    public DispatchRepository.DispatchPage<DispatchReceipt> receipts(String site, UUID dispatchId,
            DispatchReceipt.ReceiptOutcome outcome, DispatchReceipt.VarianceType varianceType, String recipient,
            java.time.Instant from, java.time.Instant to, DispatchRepository.Paging paging, ActorContext actor) {
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, site, "DispatchReceipt", null);
        return repository.findReceipts(new DispatchRepository.ReceiptQuery(List.of(SiteCode.of(site).value()),
                dispatchId, outcome, varianceType, recipient, from, to, paging));
    }

    private Dispatch requireDispatch(UUID id) {
        return repository.findDispatch(id).orElseThrow(() -> RecordNotFoundException.of("Dispatch", id));
    }
}
