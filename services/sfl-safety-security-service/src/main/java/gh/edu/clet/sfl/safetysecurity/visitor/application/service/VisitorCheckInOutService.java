package gh.edu.clet.sfl.safetysecurity.visitor.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.BadgeDeviceGatewayPort;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.event.VisitorEventType;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS-SFL-S160-01: badge/access-zone assignment, check-in, check-out, and withdrawal before arrival. */
@Service
public class VisitorCheckInOutService {

    private final VisitorRepository repository;
    private final BadgeDeviceGatewayPort badgeGateway;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final VisitorAccessPolicy access;
    private final Clock clock;

    public VisitorCheckInOutService(VisitorRepository repository, BadgeDeviceGatewayPort badgeGateway,
            AuditPort audit, IntegrationEventPublisher events, VisitorAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.badgeGateway = badgeGateway;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public VisitorVisit assignBadge(AssignBadge command) {
        ActorContext actor = command.actor();
        VisitorVisit visit = requireVisit(command.visitId());
        access.require(actor, SflPermission.VISITOR_BADGE_ASSIGN, visit.siteCode(), "VisitorVisit",
                visit.id().toString());
        visit.metadata().requireVersion(command.expectedVersion());

        Instant at = clock.instant();
        VisitorVisit badged = visit.assignBadge(command.badgeNumber(), command.accessZones(), actor.actorId(), at,
                command.sourceChannel(), actor.correlationId());
        VisitorVisit saved = repository.saveVisit(badged);

        badgeGateway.syncAssignment(saved.id(), saved.badgeNumber(), saved.accessZones(), saved.siteCode(), actor);
        audit.record(actor, command.sourceChannel().name(), saved.siteCode(), "VISITOR_BADGE_ASSIGNED",
                "VisitorVisit", saved.id().toString(), visit, saved, null);
        events.publish(VisitorEventType.VISITOR_BADGE_ASSIGNED.eventType(),
                VisitorEventType.VISITOR_BADGE_ASSIGNED.version(), "VisitorVisit", saved.id().toString(),
                saved.siteCode(), actor, Map.of("visitId", saved.id().toString(), "badgeNumber", saved.badgeNumber()));
        return saved;
    }

    @Transactional
    public VisitorVisit checkIn(Transition command) {
        return transition(command, SflPermission.VISITOR_CHECKIN, VisitorVisit::checkIn,
                VisitorEventType.VISITOR_CHECKED_IN, "VISITOR_CHECKED_IN");
    }

    @Transactional
    public VisitorVisit checkOut(Transition command) {
        return transition(command, SflPermission.VISITOR_CHECKOUT, VisitorVisit::checkOut,
                VisitorEventType.VISITOR_CHECKED_OUT, "VISITOR_CHECKED_OUT");
    }

    @Transactional
    public VisitorVisit cancel(CancelVisit command) {
        ActorContext actor = command.actor();
        VisitorVisit visit = requireVisit(command.visitId());
        access.require(actor, SflPermission.VISITOR_CANCEL, visit.siteCode(), "VisitorVisit",
                visit.id().toString());
        visit.metadata().requireVersion(command.expectedVersion());

        Instant at = clock.instant();
        VisitorVisit cancelled = visit.cancel(command.reason(), actor.actorId(), at, command.sourceChannel(),
                actor.correlationId());
        VisitorVisit saved = repository.saveVisit(cancelled);

        audit.record(actor, command.sourceChannel().name(), saved.siteCode(), "VISITOR_VISIT_CANCELLED",
                "VisitorVisit", saved.id().toString(), visit, saved, command.reason());
        events.publish(VisitorEventType.VISITOR_VISIT_CANCELLED.eventType(),
                VisitorEventType.VISITOR_VISIT_CANCELLED.version(), "VisitorVisit", saved.id().toString(),
                saved.siteCode(), actor, Map.of("visitId", saved.id().toString(), "reason", command.reason()));
        return saved;
    }

    private VisitorVisit transition(Transition command, SflPermission permission, VisitTransitionFn transitionFn,
            VisitorEventType eventType, String auditAction) {
        ActorContext actor = command.actor();
        VisitorVisit visit = requireVisit(command.visitId());
        access.require(actor, permission, visit.siteCode(), "VisitorVisit", visit.id().toString());
        visit.metadata().requireVersion(command.expectedVersion());

        Instant at = clock.instant();
        VisitorVisit moved = transitionFn.apply(visit, actor.actorId(), at, command.sourceChannel(),
                actor.correlationId());
        VisitorVisit saved = repository.saveVisit(moved);

        audit.record(actor, command.sourceChannel().name(), saved.siteCode(), auditAction, "VisitorVisit",
                saved.id().toString(), visit, saved, null);
        events.publish(eventType.eventType(), eventType.version(), "VisitorVisit", saved.id().toString(),
                saved.siteCode(), actor, Map.of("visitId", saved.id().toString()));
        return saved;
    }

    private VisitorVisit requireVisit(UUID id) {
        return repository.findVisit(id).orElseThrow(() -> VisitorException.notFound("VisitorVisit", id));
    }

    @FunctionalInterface
    private interface VisitTransitionFn {
        VisitorVisit apply(VisitorVisit visit, String actorId, Instant at, SourceChannel channel,
                String correlationId);
    }

    public record AssignBadge(UUID visitId, String badgeNumber, List<String> accessZones, Long expectedVersion,
            ActorContext actor, SourceChannel sourceChannel) {
    }

    public record Transition(UUID visitId, Long expectedVersion, ActorContext actor, SourceChannel sourceChannel) {
    }

    public record CancelVisit(UUID visitId, String reason, Long expectedVersion, ActorContext actor,
            SourceChannel sourceChannel) {
    }
}
