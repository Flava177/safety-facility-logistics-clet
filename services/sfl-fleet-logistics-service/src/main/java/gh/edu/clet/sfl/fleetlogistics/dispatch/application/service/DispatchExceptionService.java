package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchOutboxAdminPort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.SecurityVisibilityPort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.NotificationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.NotificationPort.NotificationKind;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accountable dispatch exception/case workflow: creation (idempotent by occurrence), SLA, transitions,
 * notifications, SSEMP surfacing for security-relevant variances, and outbox delivery health/replay.
 */
@Service
public class DispatchExceptionService {

    private final DispatchRepository repository;
    private final DispatchAccessPolicy access;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final NotificationPort notifications;
    private final RuntimeConfigurationPort runtimeConfig;
    private final SecurityVisibilityPort security;
    private final DispatchOutboxAdminPort outboxAdmin;
    private final Clock clock;

    public DispatchExceptionService(DispatchRepository repository, DispatchAccessPolicy access, AuditPort audit,
            IntegrationEventPublisher events, NotificationPort notifications, RuntimeConfigurationPort runtimeConfig,
            SecurityVisibilityPort security, DispatchOutboxAdminPort outboxAdmin, Clock clock) {
        this.repository = repository; this.access = access; this.audit = audit; this.events = events;
        this.notifications = notifications; this.runtimeConfig = runtimeConfig; this.security = security;
        this.outboxAdmin = outboxAdmin; this.clock = clock;
    }

    public record OpenCase(String siteCode, DispatchExceptionCase.Type type, DispatchExceptionCase.Severity severity,
            boolean securityRelevant, String occurrenceKey, UUID courierItemId, UUID dispatchId, UUID handoverId,
            UUID receiptId, UUID tripId, List<String> detectedRules, ActorContext actor, SourceChannel channel) {}

    /** Open an accountable case, idempotent by (site, occurrenceKey): a repeated detection returns the open case. */
    @Transactional
    public DispatchExceptionCase openCase(OpenCase c) {
        var existing = repository.findExceptionByOccurrence(c.siteCode(), c.occurrenceKey());
        if (existing.isPresent() && existing.get().open()) return existing.get();
        Instant now = clock.instant();
        var meta = RecordMetadata.createdBy(c.actor().actorId(), now, c.channel(), c.actor().correlationId());
        var kase = new DispatchExceptionCase(UUID.randomUUID(), DispatchNumbers.next("DXC"), SiteCode.of(c.siteCode()),
                c.occurrenceKey(), c.courierItemId(), c.dispatchId(), c.handoverId(), c.receiptId(), c.tripId(),
                c.type(), c.severity(), c.securityRelevant(), DispatchExceptionCase.Status.DETECTED, null,
                now.plus(severityAdjustedSla(c.siteCode(), c.severity())), null, null, null, null, 0,
                c.detectedRules(), meta);
        kase = repository.saveException(kase);
        repository.saveExceptionHistory(kase.id(), null, kase.status().name(), "open", c.actor().actorId(), null,
                now, c.actor().correlationId());
        audit.record(c.actor(), c.channel(), kase.siteCode(), AuditAction.CREATE, "DispatchExceptionCase",
                kase.id().toString(), null, kase);
        events.publish(detectionEvent(c.type()), "DispatchExceptionCase", kase.id().toString(), kase.siteCode(),
                c.actor(), Map.of("exceptionId", kase.id(), "type", c.type(), "severity", c.severity(),
                        "securityRelevant", c.securityRelevant()));
        notifications.notifyRole(kase.siteCode(), SflRole.DISPATCH_CONTROLLER, NotificationKind.WORK_ASSIGNED,
                kase.exceptionNumber(), context(kase));
        if (kase.securityRelevant()) security.surfaceSecurityVariance(kase, c.actor());
        return kase;
    }

    @Transactional
    public DispatchExceptionCase transition(UUID id, String action, String value, UUID evidence, ActorContext actor,
            SourceChannel channel) {
        var before = exceptionCase(id, actor);
        SflPermission permission = Set.of("approve", "reject", "close").contains(action)
                ? SflPermission.DISPATCH_EXCEPTION_APPROVE
                : action.equals("escalate") ? SflPermission.DISPATCH_EXCEPTION_ESCALATE
                : SflPermission.DISPATCH_EXCEPTION_MANAGE;
        access.require(actor, permission, before.siteCode().value(), "DispatchExceptionCase", id.toString());
        var meta = before.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId());
        var after = switch (action) {
            case "assign" -> before.assign(value, meta);
            case "reassign" -> before.reassign(value, meta);
            case "review" -> before.review(meta);
            case "request-explanation" -> before.requestExplanation(meta);
            case "explain" -> before.explain(value, evidence, meta);
            case "approve" -> before.decide(DispatchExceptionCase.Decision.APPROVED, value, meta);
            case "reject" -> before.decide(DispatchExceptionCase.Decision.REJECTED, value, meta);
            case "escalate" -> before.escalate(value, meta);
            case "hold" -> before.hold(value, meta);
            case "resume" -> before.resume(meta);
            case "cancel" -> before.cancel(value, meta);
            case "close" -> before.close(value, evidence, meta);
            case "reopen" -> before.reopen(value, meta);
            default -> throw new IllegalArgumentException("Unknown exception transition");
        };
        after = repository.saveException(after);
        repository.saveExceptionHistory(after.id(), before.status().name(), after.status().name(), action,
                actor.actorId(), value, clock.instant(), actor.correlationId());
        audit.record(actor, channel, after.siteCode(), auditAction(action), "DispatchExceptionCase", id.toString(),
                before, after);
        notify(action, after);
        FleetEventType event = switch (action) {
            case "assign", "reassign" -> FleetEventType.DISPATCH_EXCEPTION_ASSIGNED;
            case "approve" -> FleetEventType.DISPATCH_EXCEPTION_APPROVED;
            case "reject" -> FleetEventType.DISPATCH_EXCEPTION_REJECTED;
            case "escalate" -> FleetEventType.DISPATCH_EXCEPTION_ESCALATED;
            default -> null;
        };
        if (event != null) {
            events.publish(event, "DispatchExceptionCase", id.toString(), after.siteCode(), actor,
                    Map.of("exceptionId", id, "status", after.status()));
        }
        if (action.equals("escalate") && after.securityRelevant()) security.surfaceSecurityVariance(after, actor);
        return after;
    }

    public DispatchExceptionCase exceptionCase(UUID id, ActorContext actor) {
        var kase = repository.findException(id).orElseThrow(() -> RecordNotFoundException.of("DispatchExceptionCase", id));
        access.require(actor, SflPermission.DISPATCH_EXCEPTION_READ, kase.siteCode().value(), "DispatchExceptionCase",
                id.toString());
        return kase;
    }

    public DispatchRepository.DispatchPage<DispatchExceptionCase> exceptions(String site,
            DispatchExceptionCase.Type type, DispatchExceptionCase.Status status,
            DispatchExceptionCase.Severity severity, String assignee, Boolean unassigned, Boolean securityRelevant,
            Boolean openOnly, java.time.Instant dueBefore, UUID dispatchId, UUID courierItemId,
            DispatchRepository.Paging paging, ActorContext actor) {
        access.require(actor, SflPermission.DISPATCH_EXCEPTION_READ, site, "DispatchExceptionCase", null);
        return repository.findExceptions(new DispatchRepository.ExceptionQuery(List.of(SiteCode.of(site).value()),
                type, status, severity, assignee, unassigned, securityRelevant, openOnly, dueBefore, dispatchId,
                courierItemId, paging));
    }

    /** The case's transition history: assignment, review, explanation, decision and closure. */
    public List<AuditEvent> history(UUID id, ActorContext actor) {
        var kase = exceptionCase(id, actor);
        return audit.search(new AuditPort.AuditQuery(List.of(kase.siteCode().value()), "DispatchExceptionCase",
                id.toString(), null, null, null, null, 0, 200));
    }

    public DispatchOutboxAdminPort.OutboxHealth integrationHealth(ActorContext actor) {
        access.requirePermission(actor, SflPermission.DISPATCH_INTEGRATION_REPLAY, "DispatchOutbox");
        return outboxAdmin.health();
    }

    @Transactional
    public boolean replayIntegration(UUID messageId, ActorContext actor, SourceChannel channel) {
        access.requirePermission(actor, SflPermission.DISPATCH_INTEGRATION_REPLAY, "DispatchOutbox");
        boolean requeued = outboxAdmin.replay(messageId);
        audit.record(actor, channel, SiteCode.of("SYSTEM"), AuditAction.INTEGRATION_REPLAYED, "DispatchOutboxMessage",
                messageId.toString(), null, Map.of("requeued", requeued));
        return requeued;
    }

    /** Scheduled SLA escalation for an overdue case (system actor). Idempotent per case state. */
    @Transactional
    public void escalateForSla(UUID id, ActorContext actor, SourceChannel channel) {
        var before = repository.findException(id).orElse(null);
        if (before == null || !before.open() || before.status() == DispatchExceptionCase.Status.ESCALATED) return;
        transition(id, "escalate", "SLA threshold breached", null, actor, channel);
    }

    private Duration severityAdjustedSla(String site, DispatchExceptionCase.Severity severity) {
        Duration base = runtimeConfig.value("dispatch.exception.sla.default", site)
                .map(Duration::parse).orElse(Duration.ofHours(24));
        return switch (severity) {
            case CRITICAL -> base.dividedBy(4);
            case HIGH -> base.dividedBy(2);
            case MEDIUM -> base;
            case LOW -> base.multipliedBy(2);
        };
    }

    private void notify(String action, DispatchExceptionCase c) {
        switch (action) {
            case "assign", "reassign" -> {
                if (c.assignee() != null) notifications.notifyAssignee(c.siteCode(), c.assignee(),
                        NotificationKind.WORK_ASSIGNED, c.exceptionNumber(), context(c));
            }
            case "escalate" -> notifications.notifyRole(c.siteCode(),
                    c.securityRelevant() ? SflRole.SECURITY_OFFICER : SflRole.FLEET_MANAGER,
                    NotificationKind.WORK_ESCALATED, c.exceptionNumber(), context(c));
            case "hold" -> {
                if (c.assignee() != null) notifications.notifyAssignee(c.siteCode(), c.assignee(),
                        NotificationKind.WORK_BLOCKED, c.exceptionNumber(), context(c));
            }
            default -> { }
        }
    }

    private static Map<String, String> context(DispatchExceptionCase c) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("exceptionNumber", c.exceptionNumber());
        m.put("type", c.type().name());
        m.put("severity", c.severity().name());
        m.put("status", c.status().name());
        m.put("securityRelevant", Boolean.toString(c.securityRelevant()));
        if (c.dispatchId() != null) m.put("dispatchId", c.dispatchId().toString());
        if (c.courierItemId() != null) m.put("courierItemId", c.courierItemId().toString());
        if (c.metadata().auditCorrelationId() != null) m.put("correlationId", c.metadata().auditCorrelationId());
        return m;
    }

    private static AuditAction auditAction(String action) {
        return switch (action) {
            case "assign" -> AuditAction.ASSIGN;
            case "reassign" -> AuditAction.REASSIGN;
            case "hold" -> AuditAction.HOLD;
            case "resume" -> AuditAction.RESUME;
            case "escalate" -> AuditAction.ESCALATE;
            case "cancel" -> AuditAction.CANCEL;
            case "close" -> AuditAction.CLOSE;
            case "reopen" -> AuditAction.REOPEN;
            default -> AuditAction.STATE_TRANSITION;
        };
    }

    private static FleetEventType detectionEvent(DispatchExceptionCase.Type type) {
        return switch (type) {
            case CUSTODY_GAP -> FleetEventType.CUSTODY_GAP_DETECTED;
            case RECEIPT_VARIANCE -> FleetEventType.DISPATCH_RECEIPT_VARIANCE;
            case RETURN_DISCREPANCY -> FleetEventType.DISPATCH_RETURN_DISCREPANCY;
            case UNDELIVERED_ITEM -> FleetEventType.INBOUND_ITEM_UNDELIVERED;
            case SCAN_MISMATCH, UNREGISTERED_ITEM -> FleetEventType.DISPATCH_SCAN_MISMATCH;
        };
    }
}
