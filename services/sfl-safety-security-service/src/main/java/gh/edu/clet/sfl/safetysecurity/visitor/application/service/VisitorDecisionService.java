package gh.edu.clet.sfl.safetysecurity.visitor.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.event.VisitorEventType;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorApproval;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorApprovalDecision;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S160-01: host approval.
 *
 * <p>The actor who registered a visit may not also decide on it - the same rule {@code
 * BookingApplicationService} enforces for room approval, administrators included.
 */
@Service
public class VisitorDecisionService {

    private final VisitorRepository repository;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final VisitorAccessPolicy access;
    private final Clock clock;

    public VisitorDecisionService(VisitorRepository repository, AuditPort audit, IntegrationEventPublisher events,
            VisitorAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public VisitorVisit decide(DecideVisit command) {
        ActorContext actor = command.actor();
        VisitorVisit visit = repository.findVisit(command.visitId())
                .orElseThrow(() -> VisitorException.notFound("VisitorVisit", command.visitId()));
        access.requireApproval(actor, SflPermission.VISITOR_VISIT_APPROVE, visit.siteCode(), "VisitorVisit",
                visit.id().toString());
        visit.metadata().requireVersion(command.expectedVersion());

        // The host is the intended approver - VisitorPermissionMatrix only grants VISITOR_VISIT_APPROVE
        // to VISITOR_HOST (and admin/director roles) in the first place. What is forbidden is the
        // *registrant* deciding on their own registration: reception pre-registering a visit and then
        // also approving it would make the approval step meaningless, the same conflict Booking's
        // "may not approve your own request" rule guards against for whoever requested the room.
        if (actor.actorId().equals(visit.metadata().createdBy())) {
            audit.record(actor, command.sourceChannel().name(), visit.siteCode(),
                    "VISITOR_SELF_APPROVAL_DENIED", "VisitorVisit", visit.id().toString(), null, null,
                    "An actor may not decide on a visit they registered themselves");
            throw new VisitorException(VisitorErrorCode.VISITOR_SELF_APPROVAL_NOT_ALLOWED);
        }

        Instant at = clock.instant();
        UUID approvalId = UUID.randomUUID();
        VisitorApproval approval = VisitorApproval.decide(approvalId, visit,
                command.approve() ? VisitorApprovalDecision.APPROVED : VisitorApprovalDecision.REJECTED,
                command.reason(), actor.actorId(), at);
        repository.saveApproval(approval);

        VisitorVisit decided = command.approve()
                ? visit.confirm(approvalId, command.watchlistOverrideReason(), actor.actorId(), at,
                        command.sourceChannel(), actor.correlationId())
                : visit.reject(approvalId, command.reason(), actor.actorId(), at, command.sourceChannel(),
                        actor.correlationId());
        VisitorVisit saved = repository.saveVisit(decided);

        String action = command.approve() ? "VISITOR_VISIT_CONFIRMED" : "VISITOR_VISIT_REJECTED";
        audit.record(actor, command.sourceChannel().name(), saved.siteCode(), action, "VisitorVisit",
                saved.id().toString(), visit, saved, command.reason());
        VisitorEventType eventType = command.approve() ? VisitorEventType.VISITOR_VISIT_CONFIRMED
                : VisitorEventType.VISITOR_VISIT_REJECTED;
        events.publish(eventType.eventType(), eventType.version(), "VisitorVisit", saved.id().toString(),
                saved.siteCode(), actor, Map.of("visitId", saved.id().toString(), "decidedBy", actor.actorId()));
        return saved;
    }

    public record DecideVisit(UUID visitId, boolean approve, String reason, String watchlistOverrideReason,
            Long expectedVersion, ActorContext actor, SourceChannel sourceChannel) {
    }
}
