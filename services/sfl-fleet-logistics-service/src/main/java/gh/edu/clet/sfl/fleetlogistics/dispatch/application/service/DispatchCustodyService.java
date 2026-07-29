package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchEvidencePort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHandover;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CustodyHop;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.SealState;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.CustodyChainPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S171-02: unbroken chain-of-custody handovers. Each hop is appended immutably; after recording, the
 * chain is re-checked for gaps (missing handover, broken seal, count mismatch, out-of-order hop) — any
 * gap opens a {@code CUSTODY_GAP} exception (security-relevant when the seal is compromised) and blocks
 * dispatch/custody closure.
 */
@Service
public class DispatchCustodyService {

    private final DispatchRepository repository;
    private final DispatchAccessPolicy access;
    private final DispatchEvidencePort evidence;
    private final DispatchExceptionService exceptions;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public DispatchCustodyService(DispatchRepository repository, DispatchAccessPolicy access,
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

    public record RecordHandover(UUID dispatchId, CustodyHop hop, String transferringCustodian,
            String receivingCustodian, Instant occurredAt, SealState sealState, Integer verifiedCount, String notes,
            EvidenceMeta evidence, ActorContext actor, SourceChannel channel) {}

    @Transactional
    public CustodyHandover recordHandover(RecordHandover c) {
        var dispatch = requireDispatch(c.dispatchId());
        access.require(c.actor(), SflPermission.DISPATCH_CUSTODY_RECORD, dispatch.siteCode().value(), "CustodyHandover",
                c.dispatchId().toString());
        Instant now = clock.instant();
        UUID evidenceId = DispatchEvidenceSupport.registerIfPresent(evidence, dispatch.siteCode(), "CustodyHandover",
                c.dispatchId().toString(), "CUSTODY_HANDOVER", c.evidence(), c.actor(), c.channel());
        var handover = new CustodyHandover(UUID.randomUUID(), c.dispatchId(), dispatch.siteCode(), c.hop(),
                repository.nextHandoverSequence(c.dispatchId()), c.transferringCustodian(), c.receivingCustodian(),
                c.occurredAt() == null ? now : c.occurredAt(), c.sealState(), c.verifiedCount(), c.notes(), evidenceId,
                c.actor().actorId(), now, c.channel(), c.actor().correlationId());
        var saved = repository.saveHandover(handover);
        audit.record(c.actor(), c.channel(), dispatch.siteCode(), AuditAction.CREATE, "CustodyHandover",
                saved.id().toString(), null, saved);
        events.publish(FleetEventType.CUSTODY_HANDOVER_RECORDED, "CustodyHandover", saved.id().toString(),
                dispatch.siteCode(), c.actor(), Map.of("dispatchId", c.dispatchId(), "hop", c.hop(),
                        "sequenceNo", saved.sequenceNo(), "sealState", c.sealState()));
        List<CustodyChainPolicy.Gap> gaps = CustodyChainPolicy.detectGaps(repository.findHandovers(c.dispatchId()),
                dispatch.itemCount());
        if (!gaps.isEmpty()) {
            boolean securityRelevant = c.sealState().isCompromised()
                    || gaps.stream().anyMatch(g -> g.reason() == CustodyChainPolicy.GapReason.BROKEN_SEAL);
            exceptions.openCase(new DispatchExceptionService.OpenCase(dispatch.siteCode().value(),
                    DispatchExceptionCase.Type.CUSTODY_GAP,
                    securityRelevant ? DispatchExceptionCase.Severity.HIGH : DispatchExceptionCase.Severity.MEDIUM,
                    securityRelevant, "CUSTODY_GAP:" + c.dispatchId(), null, c.dispatchId(), saved.id(), null,
                    dispatch.tripId(), gaps.stream().map(DispatchCustodyService::describe).toList(),
                    c.actor(), c.channel()));
        }
        return saved;
    }

    /**
     * A gap as one line of text, for the {@code detectedRules} list on an exception case.
     *
     * <p>The only place a gap is still flattened to a string. It is a case's own summary of why it
     * was raised, not a wire format anybody parses — the custody read returns the structured gaps.
     */
    private static String describe(CustodyChainPolicy.Gap gap) {
        StringBuilder text = new StringBuilder(gap.reason().name()).append(" at ").append(gap.hop());
        if (!gap.detail().isEmpty()) {
            text.append(' ').append(gap.detail());
        }
        return text.toString();
    }

    public List<CustodyHandover> handovers(UUID dispatchId, ActorContext actor) {
        requireRead(dispatchId, actor);
        return repository.findHandovers(dispatchId);
    }

    /**
     * Custody handovers across a site's consignments.
     *
     * <p>Closes gap 7. Custody was readable per consignment only, so "everything this custodian
     * handled last week" needed the manifests to be known first — which is the wrong way round when
     * the custodian is the reason for asking.
     */
    public DispatchRepository.DispatchPage<CustodyHandover> handovers(String site, UUID dispatchId, CustodyHop hop,
            String custodian, SealState sealState, java.time.Instant from, java.time.Instant to,
            DispatchRepository.Paging paging, ActorContext actor) {
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, site, "CustodyHandover", null);
        return repository.findHandovers(new DispatchRepository.CustodyQuery(List.of(SiteCode.of(site).value()),
                dispatchId, hop, custodian, sealState, from, to, paging));
    }

    /** Human-readable custody gap descriptions, plus any closure-required hops still missing. */
    public CustodyGaps gaps(UUID dispatchId, ActorContext actor) {
        var dispatch = requireRead(dispatchId, actor);
        var handovers = repository.findHandovers(dispatchId);
        return new CustodyGaps(CustodyChainPolicy.detectGaps(handovers, dispatch.itemCount()),
                CustodyChainPolicy.missingClosureHops(handovers).stream().map(Enum::name).toList(),
                CustodyChainPolicy.closable(handovers, dispatch.itemCount()));
    }

    public record CustodyGaps(List<CustodyChainPolicy.Gap> gaps, List<String> missingClosureHops,
            boolean closable) {}

    private Dispatch requireDispatch(UUID id) {
        return repository.findDispatch(id).orElseThrow(() -> RecordNotFoundException.of("Dispatch", id));
    }

    private Dispatch requireRead(UUID id, ActorContext actor) {
        var dispatch = requireDispatch(id);
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, dispatch.siteCode().value(), "CustodyHandover",
                id.toString());
        return dispatch;
    }
}
