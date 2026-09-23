package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvRepository;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvVendorGatewayPort;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.event.CctvEventType;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvErrorCode;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceAccessAction;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceAccessLog;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItem;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceRequest;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceRequestStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S161-02/03: the governed evidence-request workflow and evidence-by-reference recording.
 * "No footage is exported without an approved evidence request by an authorised security role" is
 * enforced by {@link #retrieve} requiring {@link EvidenceRequestStatus#APPROVED} before it will call
 * the vendor gateway at all - there is no path in this service that retrieves footage from a request
 * that is still {@code PENDING} or that was {@code REJECTED}.
 */
@Service
public class EvidenceRequestService {

    private final CctvRepository repository;
    private final CctvVendorGatewayPort vendorGateway;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final CctvAccessPolicy access;
    private final Clock clock;

    public EvidenceRequestService(CctvRepository repository, CctvVendorGatewayPort vendorGateway, AuditPort audit,
            IntegrationEventPublisher events, CctvAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.vendorGateway = vendorGateway;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    public record CreateRequest(String siteCode, List<String> cameraIds, String locationRef, Instant windowStart,
            Instant windowEnd, String purpose, UUID caseRef, ActorContext actor) {
    }

    public record Decide(UUID requestId, String decisionNotes, ActorContext actor) {
    }

    public record Retrieve(UUID requestId, String cameraId, ActorContext actor) {
    }

    public record RecordAccess(UUID evidenceItemId, EvidenceAccessAction action, ActorContext actor) {
    }

    @Transactional
    public EvidenceRequest create(CreateRequest command) {
        ActorContext actor = command.actor();
        access.require(actor, SflPermission.CCTV_EVIDENCE_REQUEST_CREATE, command.siteCode(), "EvidenceRequest",
                null);
        Instant now = clock.instant();
        EvidenceRequest request = EvidenceRequest.create(UUID.randomUUID(), command.siteCode(), command.cameraIds(),
                command.locationRef(), command.windowStart(), command.windowEnd(), command.purpose(),
                command.caseRef(), actor.actorId(), now, SourceChannel.WEB, actor.correlationId());
        EvidenceRequest saved = repository.saveEvidenceRequest(request);
        audit.record(actor, SourceChannel.WEB.name(), saved.siteCode(), "CCTV_EVIDENCE_REQUESTED", "EvidenceRequest",
                saved.id().toString(), null, saved, null);
        events.publish(CctvEventType.EVIDENCE_REQUESTED.eventType(), CctvEventType.EVIDENCE_REQUESTED.version(),
                "EvidenceRequest", saved.id().toString(), saved.siteCode(), actor,
                Map.of("cameraIds", saved.cameraIds(), "purpose", saved.purpose()));
        return saved;
    }

    /** SRS-SFL-S161-02: "requires approval by an authorised security role before any retrieval or
     * export from the VMS/NVR". */
    @Transactional
    public EvidenceRequest approve(Decide command) {
        ActorContext actor = command.actor();
        EvidenceRequest request = requireRequest(command.requestId());
        access.require(actor, SflPermission.CCTV_EVIDENCE_REQUEST_APPROVE, request.siteCode(), "EvidenceRequest",
                command.requestId().toString());
        EvidenceRequest approved = repository.saveEvidenceRequest(request.approve(actor.actorId(), clock.instant(),
                SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), approved.siteCode(), "CCTV_EVIDENCE_REQUEST_APPROVED",
                "EvidenceRequest", approved.id().toString(), request, approved, null);
        events.publish(CctvEventType.EVIDENCE_REQUEST_DECIDED.eventType(),
                CctvEventType.EVIDENCE_REQUEST_DECIDED.version(), "EvidenceRequest", approved.id().toString(),
                approved.siteCode(), actor, Map.of("status", approved.status().name()));
        return approved;
    }

    @Transactional
    public EvidenceRequest reject(Decide command) {
        ActorContext actor = command.actor();
        EvidenceRequest request = requireRequest(command.requestId());
        access.require(actor, SflPermission.CCTV_EVIDENCE_REQUEST_APPROVE, request.siteCode(), "EvidenceRequest",
                command.requestId().toString());
        EvidenceRequest rejected = repository.saveEvidenceRequest(request.reject(actor.actorId(),
                command.decisionNotes(), clock.instant(), SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), rejected.siteCode(), "CCTV_EVIDENCE_REQUEST_REJECTED",
                "EvidenceRequest", rejected.id().toString(), request, rejected, command.decisionNotes());
        events.publish(CctvEventType.EVIDENCE_REQUEST_DECIDED.eventType(),
                CctvEventType.EVIDENCE_REQUEST_DECIDED.version(), "EvidenceRequest", rejected.id().toString(),
                rejected.siteCode(), actor, Map.of("status", rejected.status().name()));
        return rejected;
    }

    /** SRS-SFL-S161-03: "recorded evidence exports by reference with a hash", attached to the request's
     * case. Only reachable from an {@link EvidenceRequestStatus#APPROVED} request - see the class javadoc. */
    @Transactional
    public EvidenceItem retrieve(Retrieve command) {
        ActorContext actor = command.actor();
        EvidenceRequest request = requireRequest(command.requestId());
        access.require(actor, SflPermission.CCTV_EVIDENCE_REQUEST_READ, request.siteCode(), "EvidenceRequest",
                command.requestId().toString());
        if (request.status() != EvidenceRequestStatus.APPROVED) {
            throw CctvException.of(CctvErrorCode.CCTV_EVIDENCE_REQUEST_NOT_APPROVED);
        }
        if (!request.cameraIds().contains(command.cameraId())) {
            throw new CctvException(CctvErrorCode.CCTV_VALIDATION_FAILED,
                    Map.of("cameraId", command.cameraId(), "reason", "not part of the approved request"));
        }
        var retrieval = vendorGateway.retrieve(request, command.cameraId(), request.windowStart(),
                request.windowEnd(), actor);
        Instant now = clock.instant();
        EvidenceItem item = EvidenceItem.record(UUID.randomUUID(), request.id(), request.siteCode(),
                command.cameraId(), request.windowStart(), request.windowEnd(), retrieval.exportHandle(),
                retrieval.hash(), retrieval.provenance(), request.caseRef(), actor.actorId(), now, SourceChannel.WEB,
                actor.correlationId());
        EvidenceItem saved = repository.saveEvidenceItem(item);
        audit.record(actor, SourceChannel.WEB.name(), saved.siteCode(), "CCTV_EVIDENCE_ITEM_RECORDED",
                "EvidenceItem", saved.id().toString(), null, saved, null);
        events.publish(CctvEventType.EVIDENCE_ITEM_RECORDED.eventType(),
                CctvEventType.EVIDENCE_ITEM_RECORDED.version(), "EvidenceItem", saved.id().toString(),
                saved.siteCode(), actor, Map.of("cameraId", saved.cameraId(), "hash", saved.hash()));
        recordAccess(new RecordAccess(saved.id(), EvidenceAccessAction.EXPORTED, actor));
        return saved;
    }

    /** SRS-SFL-S161-03: "every view, download and export ... logged with actor and timestamp as an
     * immutable, tamper-evident record" - each access is written to the queryable access log AND to
     * the shared, hash-chained {@code audit_log} (see {@link EvidenceAccessLog}'s javadoc). */
    @Transactional
    public EvidenceAccessLog recordAccess(RecordAccess command) {
        ActorContext actor = command.actor();
        EvidenceItem item = repository.findEvidenceItem(command.evidenceItemId())
                .orElseThrow(() -> CctvException.notFound("EvidenceItem", command.evidenceItemId()));
        access.require(actor, SflPermission.CCTV_EVIDENCE_ITEM_READ, item.siteCode(), "EvidenceItem",
                command.evidenceItemId().toString());
        Instant now = clock.instant();
        EvidenceAccessLog log = repository.saveAccessLog(EvidenceAccessLog.record(UUID.randomUUID(),
                command.evidenceItemId(), actor.actorId(), command.action(), now));
        audit.record(actor, SourceChannel.WEB.name(), item.siteCode(), "CCTV_EVIDENCE_ITEM_" + command.action(),
                "EvidenceItem", item.id().toString(), null, log, null);
        return log;
    }

    @Transactional(readOnly = true)
    public List<EvidenceAccessLog> accessLogFor(UUID evidenceItemId, ActorContext actor) {
        EvidenceItem item = repository.findEvidenceItem(evidenceItemId)
                .orElseThrow(() -> CctvException.notFound("EvidenceItem", evidenceItemId));
        access.require(actor, SflPermission.CCTV_EVIDENCE_ITEM_READ, item.siteCode(), "EvidenceItem",
                evidenceItemId.toString());
        return repository.findAccessLogsForItem(evidenceItemId);
    }

    @Transactional(readOnly = true)
    public List<EvidenceRequest> forSite(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.CCTV_EVIDENCE_REQUEST_READ, siteCode, "EvidenceRequest", null);
        return repository.findEvidenceRequestsBySite(siteCode);
    }

    @Transactional(readOnly = true)
    public List<EvidenceItem> itemsForRequest(UUID requestId, ActorContext actor) {
        EvidenceRequest request = requireRequest(requestId);
        access.require(actor, SflPermission.CCTV_EVIDENCE_ITEM_READ, request.siteCode(), "EvidenceRequest",
                requestId.toString());
        return repository.findEvidenceItemsByRequest(requestId);
    }

    private EvidenceRequest requireRequest(UUID id) {
        return repository.findEvidenceRequest(id).orElseThrow(() -> CctvException.notFound("EvidenceRequest", id));
    }
}
