package gh.edu.clet.sfl.safetysecurity.visitor.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.WatchlistCheckPort;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.event.VisitorEventType;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitPurpose;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS-SFL-S160-01: pre-registration, and reading back what was registered. */
@Service
public class VisitorRegistrationService {

    private final VisitorRepository repository;
    private final WatchlistCheckPort watchlist;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final VisitorAccessPolicy access;
    private final Clock clock;

    public VisitorRegistrationService(VisitorRepository repository, WatchlistCheckPort watchlist, AuditPort audit,
            IntegrationEventPublisher events, VisitorAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.watchlist = watchlist;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public VisitorVisit preRegister(PreRegisterVisit command) {
        ActorContext actor = command.actor();
        access.require(actor, SflPermission.VISITOR_VISIT_CREATE, command.siteCode(), "VisitorVisit", null);

        var check = watchlist.check(command.visitorName(), command.visitorContact(), command.siteCode());
        Instant now = clock.instant();
        VisitorVisit visit = VisitorVisit.preRegister(UUID.randomUUID(), command.siteCode(),
                command.visitorName(), command.visitorOrganization(), command.visitorContact(), command.hostId(),
                command.hostName(), command.purpose(), command.expectedArrival(), command.expectedDeparture(),
                check.flagged(), actor.actorId(), now, command.sourceChannel(), actor.correlationId());
        VisitorVisit saved = repository.saveVisit(visit);

        audit.record(actor, command.sourceChannel().name(), saved.siteCode(), "VISITOR_VISIT_PRE_REGISTERED",
                "VisitorVisit", saved.id().toString(), null, saved, null);
        events.publish(VisitorEventType.VISITOR_VISIT_PRE_REGISTERED.eventType(),
                VisitorEventType.VISITOR_VISIT_PRE_REGISTERED.version(), "VisitorVisit", saved.id().toString(),
                saved.siteCode(), actor, Map.of("visitId", saved.id().toString(), "siteCode", saved.siteCode(),
                        "hostId", saved.hostId(), "approvalRequired", saved.approvalRequired(),
                        "watchlistFlagged", saved.watchlistFlagged()));

        // A visit needing neither host approval nor a watchlist override is confirmed here rather
        // than left pre-registered - the same call BookingApplicationService.request() makes for a
        // booking that needs no approval. Two audit records for one act is the honest account: the
        // registration happened, and the rule that would have sent it to a host did not apply. A
        // watchlist-flagged visit is never auto-confirmed, whatever the purpose, because it always
        // needs a human override reason - see VisitorVisit.confirm.
        if (!saved.approvalRequired() && !saved.watchlistFlagged()) {
            return confirmed(saved, null, null, actor, now, command.sourceChannel());
        }
        return saved;
    }

    private VisitorVisit confirmed(VisitorVisit visit, UUID approvalId, String watchlistOverrideReason,
            ActorContext actor, Instant at, SourceChannel channel) {
        VisitorVisit confirmed = repository.saveVisit(visit.confirm(approvalId, watchlistOverrideReason,
                actor.actorId(), at, channel, actor.correlationId()));
        audit.record(actor, channel.name(), confirmed.siteCode(), "VISITOR_VISIT_CONFIRMED", "VisitorVisit",
                confirmed.id().toString(), visit, confirmed, null);
        events.publish(VisitorEventType.VISITOR_VISIT_CONFIRMED.eventType(),
                VisitorEventType.VISITOR_VISIT_CONFIRMED.version(), "VisitorVisit", confirmed.id().toString(),
                confirmed.siteCode(), actor, Map.of("visitId", confirmed.id().toString()));
        return confirmed;
    }

    @Transactional(readOnly = true)
    public VisitorVisit get(UUID id, ActorContext actor) {
        VisitorVisit visit = findOrThrow(id);
        access.require(actor, SflPermission.VISITOR_VISIT_READ, visit.siteCode(), "VisitorVisit", id.toString());
        return visit;
    }

    @Transactional(readOnly = true)
    public List<VisitorVisit> search(VisitorRepository.VisitQuery query, ActorContext actor) {
        access.require(actor, SflPermission.VISITOR_VISIT_READ, query.siteCode(), "VisitorVisit", null);
        return repository.search(query);
    }

    VisitorVisit findOrThrow(UUID id) {
        return repository.findVisit(id).orElseThrow(() -> VisitorException.notFound("VisitorVisit", id));
    }

    public record PreRegisterVisit(String siteCode, String visitorName, String visitorOrganization,
            String visitorContact, String hostId, String hostName, VisitPurpose purpose, Instant expectedArrival,
            Instant expectedDeparture, ActorContext actor, SourceChannel sourceChannel) {
    }
}
