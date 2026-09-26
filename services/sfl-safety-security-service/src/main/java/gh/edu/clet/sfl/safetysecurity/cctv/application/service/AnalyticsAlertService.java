package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvRepository;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvIncidentSeedingPort;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvSiemForwarderPort;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.event.CctvEventType;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertSeverity;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertType;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AnalyticsAlert;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.integration.CctvIntegrationInbox;
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
 * SRS-SFL-S161-04: video-analytics alert ingestion and the SOC triage queue. Analytics correlation
 * with S162 intrusion or S160a access events is a documented follow-up (see the implementation notes)
 * rather than built here: at the time S161 ships, S162 does not exist yet in this codebase for it to
 * correlate against.
 */
@Service
public class AnalyticsAlertService {

    private final CctvRepository repository;
    private final CctvIntegrationInbox inbox;
    private final CctvSiemForwarderPort siem;
    private final CctvIncidentSeedingPort incidentSeeding;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final CctvAccessPolicy access;
    private final Clock clock;

    public AnalyticsAlertService(CctvRepository repository, CctvIntegrationInbox inbox, CctvSiemForwarderPort siem,
            CctvIncidentSeedingPort incidentSeeding, AuditPort audit, IntegrationEventPublisher events,
            CctvAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.inbox = inbox;
        this.siem = siem;
        this.incidentSeeding = incidentSeeding;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    public record IngestAlert(String source, String externalEventId, Instant signedAt, String signature,
            String rawPayload, Map<String, Object> payload, String siteCode, String cameraId, AlertType type,
            AlertSeverity severity, Instant occurredAt, ActorContext actor) {
    }

    @Transactional
    public AnalyticsAlert ingest(IngestAlert command) {
        inbox.accept(new CctvIntegrationInbox.InboundMessage(command.source(), command.externalEventId(),
                CctvEventType.ANALYTICS_ALERT_RAISED.eventType(), command.siteCode(), command.signedAt(),
                command.signature(), command.rawPayload(), command.payload(), List.of("cameraId", "type"),
                command.actor()));

        ActorContext actor = command.actor();
        Instant now = clock.instant();
        AnalyticsAlert alert = AnalyticsAlert.raise(UUID.randomUUID(), command.siteCode(), command.cameraId(),
                command.type(), command.severity(), command.occurredAt(), actor.actorId(), now,
                SourceChannel.INTEGRATION, actor.correlationId());
        AnalyticsAlert saved = repository.saveAlert(alert);

        var forward = siem.forward(saved, actor);
        if (forward.forwarded()) {
            saved = repository.saveAlert(saved.withSiemForwarded(clock.instant(), actor.actorId(), clock.instant(),
                    SourceChannel.INTEGRATION, actor.correlationId()));
        }

        if (seedsIncident(command.type())) {
            UUID incidentId = incidentSeeding.seed(command.siteCode(), describeForIncident(saved), actor);
            saved = repository.saveAlert(saved.withSeededIncident(incidentId, actor.actorId(), clock.instant(),
                    SourceChannel.INTEGRATION, actor.correlationId()));
        }

        audit.record(actor, SourceChannel.INTEGRATION.name(), saved.siteCode(), "CCTV_ANALYTICS_ALERT_RAISED",
                "AnalyticsAlert", saved.id().toString(), null, saved, null);
        events.publish(CctvEventType.ANALYTICS_ALERT_RAISED.eventType(),
                CctvEventType.ANALYTICS_ALERT_RAISED.version(), "AnalyticsAlert", saved.id().toString(),
                saved.siteCode(), actor, Map.of("type", saved.type().name(), "severity", saved.severity().name()));
        return saved;
    }

    @Transactional
    public AnalyticsAlert acknowledge(UUID id, ActorContext actor) {
        AnalyticsAlert alert = requireAlert(id);
        access.require(actor, SflPermission.CCTV_ANALYTICS_ALERT_ACKNOWLEDGE, alert.siteCode(), "AnalyticsAlert",
                id.toString());
        AnalyticsAlert acknowledged = repository.saveAlert(alert.acknowledge(actor.actorId(), clock.instant(),
                SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), acknowledged.siteCode(), "CCTV_ANALYTICS_ALERT_ACKNOWLEDGED",
                "AnalyticsAlert", acknowledged.id().toString(), alert, acknowledged, null);
        return acknowledged;
    }

    @Transactional
    public AnalyticsAlert resolve(UUID id, ActorContext actor) {
        AnalyticsAlert alert = requireAlert(id);
        access.require(actor, SflPermission.CCTV_ANALYTICS_ALERT_ACKNOWLEDGE, alert.siteCode(), "AnalyticsAlert",
                id.toString());
        AnalyticsAlert resolved = repository.saveAlert(alert.resolve(actor.actorId(), clock.instant(),
                SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), resolved.siteCode(), "CCTV_ANALYTICS_ALERT_RESOLVED",
                "AnalyticsAlert", resolved.id().toString(), alert, resolved, null);
        events.publish(CctvEventType.ANALYTICS_ALERT_RESOLVED.eventType(),
                CctvEventType.ANALYTICS_ALERT_RESOLVED.version(), "AnalyticsAlert", resolved.id().toString(),
                resolved.siteCode(), actor, Map.of("alertId", resolved.id().toString()));
        return resolved;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsAlert> queue(String siteCode, AlertStatus status, ActorContext actor) {
        access.require(actor, SflPermission.CCTV_ANALYTICS_ALERT_READ, siteCode, "AnalyticsAlert", null);
        return repository.findAlertsByStatus(siteCode, status);
    }

    /** Motion is routine SOC noise; tamper and loitering read as genuinely security-relevant patterns
     * worth a case, mirroring how S160a reserves incident-seeding for its own MUST-severity rules. */
    private static boolean seedsIncident(AlertType type) {
        return switch (type) {
            case TAMPER, LOITERING -> true;
            case MOTION, LINE_CROSSING -> false;
        };
    }

    private static String describeForIncident(AnalyticsAlert alert) {
        return "CCTV analytics alert " + alert.type() + " at camera " + alert.cameraId() + ".";
    }

    private AnalyticsAlert requireAlert(UUID id) {
        return repository.findAlert(id).orElseThrow(() -> CctvException.notFound("AnalyticsAlert", id));
    }
}
