package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlRepository;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.IncidentSeedingPort;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.SiemForwarderPort;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.event.AccessControlEventType;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionRuleCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionSeverity;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.SourceChannel;
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
 * SRS-SFL-S160a-04: the SOC exception queue - raising, correlating with a seeded incident, forwarding
 * to SIEM, and the SOC's acknowledge/resolve workflow.
 */
@Service
public class AccessExceptionService {

    private final AccessControlRepository repository;
    private final SiemForwarderPort siem;
    private final IncidentSeedingPort incidentSeeding;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final AccessControlAccessPolicy access;
    private final Clock clock;

    public AccessExceptionService(AccessControlRepository repository, SiemForwarderPort siem,
            IncidentSeedingPort incidentSeeding, AuditPort audit, IntegrationEventPublisher events,
            AccessControlAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.siem = siem;
        this.incidentSeeding = incidentSeeding;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Raises an exception, forwards it to SIEM, and - for the rule codes the SRS names as seeding a
     * security incident (a genuinely security-relevant pattern, not every reader-health blip) - seeds
     * one. Called from within the ingestion transaction (system-triggered), so no permission gate here;
     * the same shape {@code ProviderCallbackService} uses for its own integration-triggered writes.
     */
    @Transactional
    public AccessException raise(String siteCode, UUID eventId, String readerId, String zoneCode,
            ExceptionRuleCode ruleCode, ExceptionSeverity severity, ActorContext actor) {
        Instant now = clock.instant();
        AccessException raised = AccessException.raise(UUID.randomUUID(), siteCode, eventId, readerId, zoneCode,
                ruleCode, severity, actor.actorId(), now, SourceChannel.INTEGRATION, actor.correlationId());
        AccessException saved = repository.saveException(raised);

        var forward = siem.forward(saved, actor);
        if (forward.forwarded()) {
            saved = repository.saveException(saved.withSiemForwarded(clock.instant(), actor.actorId(), clock.instant(),
                    SourceChannel.INTEGRATION, actor.correlationId()));
        }

        if (seedsIncident(ruleCode)) {
            UUID incidentId = incidentSeeding.seed(siteCode, describeForIncident(saved), actor);
            saved = repository.saveException(saved.withSeededIncident(incidentId, actor.actorId(), clock.instant(),
                    SourceChannel.INTEGRATION, actor.correlationId()));
        }

        audit.record(actor, SourceChannel.INTEGRATION.name(), saved.siteCode(), "ACCESS_EXCEPTION_RAISED",
                "AccessException", saved.id().toString(), null, saved, null);
        events.publish(AccessControlEventType.ACCESS_EXCEPTION_RAISED.eventType(),
                AccessControlEventType.ACCESS_EXCEPTION_RAISED.version(), "AccessException", saved.id().toString(),
                saved.siteCode(), actor, Map.of("ruleCode", ruleCode.name(), "severity", severity.name()));
        return saved;
    }

    @Transactional
    public AccessException acknowledge(UUID id, ActorContext actor) {
        AccessException exception = requireException(id);
        access.require(actor, SflPermission.ACCESS_EXCEPTION_ACKNOWLEDGE, exception.siteCode(), "AccessException",
                id.toString());
        Instant now = clock.instant();
        AccessException acknowledged = repository.saveException(exception.acknowledge(actor.actorId(), now,
                SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), acknowledged.siteCode(), "ACCESS_EXCEPTION_ACKNOWLEDGED",
                "AccessException", acknowledged.id().toString(), exception, acknowledged, null);
        return acknowledged;
    }

    @Transactional
    public AccessException resolve(UUID id, ActorContext actor) {
        AccessException exception = requireException(id);
        access.require(actor, SflPermission.ACCESS_EXCEPTION_ACKNOWLEDGE, exception.siteCode(), "AccessException",
                id.toString());
        Instant now = clock.instant();
        AccessException resolved = repository.saveException(exception.resolve(actor.actorId(), now, SourceChannel.WEB,
                actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), resolved.siteCode(), "ACCESS_EXCEPTION_RESOLVED",
                "AccessException", resolved.id().toString(), exception, resolved, null);
        events.publish(AccessControlEventType.ACCESS_EXCEPTION_RESOLVED.eventType(),
                AccessControlEventType.ACCESS_EXCEPTION_RESOLVED.version(), "AccessException", resolved.id().toString(),
                resolved.siteCode(), actor, Map.of("exceptionId", resolved.id().toString()));
        return resolved;
    }

    @Transactional(readOnly = true)
    public List<AccessException> queue(String siteCode, ExceptionStatus status, ActorContext actor) {
        access.require(actor, SflPermission.ACCESS_EXCEPTION_READ, siteCode, "AccessException", null);
        return repository.findExceptionsByStatus(siteCode, status);
    }

    /** Reader-health noise and generic denials stay operational; only a genuinely security-relevant
     * pattern seeds a case for the HSE/Security investigation workflow. */
    private static boolean seedsIncident(ExceptionRuleCode ruleCode) {
        return switch (ruleCode) {
            case FORCED_OPEN, TAILGATING, RESTRICTED_ZONE -> true;
            case REPEATED_DENIAL, OUT_OF_HOURS, READER_OFFLINE, ANTI_PASSBACK -> false;
        };
    }

    private static String describeForIncident(AccessException exception) {
        return "Access-control exception " + exception.ruleCode() + " at zone " + exception.zoneCode()
                + (exception.readerId() == null ? "" : " (reader " + exception.readerId() + ")") + ".";
    }

    private AccessException requireException(UUID id) {
        return repository.findException(id).orElseThrow(() -> AccessControlException.notFound("AccessException", id));
    }
}
