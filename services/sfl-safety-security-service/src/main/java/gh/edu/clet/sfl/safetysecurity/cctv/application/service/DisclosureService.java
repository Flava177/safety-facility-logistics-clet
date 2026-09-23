package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvRepository;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.event.CctvEventType;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.Disclosure;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItem;
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
 * SRS-SFL-S161-05: governed disclosure of footage outside CLET. Approval is the release event itself
 * - see {@link Disclosure#approve}, which carries the evidence item's own hash onto the disclosure
 * record at the moment of approval.
 */
@Service
public class DisclosureService {

    private final CctvRepository repository;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final CctvAccessPolicy access;
    private final Clock clock;

    public DisclosureService(CctvRepository repository, AuditPort audit, IntegrationEventPublisher events,
            CctvAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    public record RequestDisclosure(UUID evidenceItemId, String purpose, String recipient, ActorContext actor) {
    }

    @Transactional
    public Disclosure request(RequestDisclosure command) {
        ActorContext actor = command.actor();
        EvidenceItem item = repository.findEvidenceItem(command.evidenceItemId())
                .orElseThrow(() -> CctvException.notFound("EvidenceItem", command.evidenceItemId()));
        access.require(actor, SflPermission.CCTV_DISCLOSURE_CREATE, item.siteCode(), "EvidenceItem",
                command.evidenceItemId().toString());
        Instant now = clock.instant();
        Disclosure disclosure = Disclosure.request(UUID.randomUUID(), command.evidenceItemId(), item.siteCode(),
                command.purpose(), command.recipient(), actor.actorId(), now, SourceChannel.WEB,
                actor.correlationId());
        Disclosure saved = repository.saveDisclosure(disclosure);
        audit.record(actor, SourceChannel.WEB.name(), saved.siteCode(), "CCTV_DISCLOSURE_REQUESTED", "Disclosure",
                saved.id().toString(), null, saved, null);
        return saved;
    }

    @Transactional
    public Disclosure approve(UUID id, ActorContext actor) {
        Disclosure disclosure = requireDisclosure(id);
        access.require(actor, SflPermission.CCTV_DISCLOSURE_APPROVE, disclosure.siteCode(), "Disclosure",
                id.toString());
        EvidenceItem item = repository.findEvidenceItem(disclosure.evidenceItemId())
                .orElseThrow(() -> CctvException.notFound("EvidenceItem", disclosure.evidenceItemId()));
        Disclosure approved = repository.saveDisclosure(disclosure.approve(actor.actorId(), item.hash(),
                clock.instant(), SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), approved.siteCode(), "CCTV_DISCLOSURE_APPROVED", "Disclosure",
                approved.id().toString(), disclosure, approved, null);
        events.publish(CctvEventType.DISCLOSURE_DECIDED.eventType(), CctvEventType.DISCLOSURE_DECIDED.version(),
                "Disclosure", approved.id().toString(), approved.siteCode(), actor,
                Map.of("status", approved.status().name(), "recipient", approved.recipient()));
        return approved;
    }

    @Transactional
    public Disclosure reject(UUID id, ActorContext actor) {
        Disclosure disclosure = requireDisclosure(id);
        access.require(actor, SflPermission.CCTV_DISCLOSURE_APPROVE, disclosure.siteCode(), "Disclosure",
                id.toString());
        Disclosure rejected = repository.saveDisclosure(disclosure.reject(actor.actorId(), clock.instant(),
                SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), rejected.siteCode(), "CCTV_DISCLOSURE_REJECTED", "Disclosure",
                rejected.id().toString(), disclosure, rejected, null);
        events.publish(CctvEventType.DISCLOSURE_DECIDED.eventType(), CctvEventType.DISCLOSURE_DECIDED.version(),
                "Disclosure", rejected.id().toString(), rejected.siteCode(), actor,
                Map.of("status", rejected.status().name()));
        return rejected;
    }

    @Transactional(readOnly = true)
    public List<Disclosure> forSite(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.CCTV_DISCLOSURE_READ, siteCode, "Disclosure", null);
        return repository.findDisclosuresBySite(siteCode);
    }

    private Disclosure requireDisclosure(UUID id) {
        return repository.findDisclosure(id).orElseThrow(() -> CctvException.notFound("Disclosure", id));
    }
}
