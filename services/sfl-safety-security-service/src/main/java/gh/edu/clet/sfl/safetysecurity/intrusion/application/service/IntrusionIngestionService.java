package gh.edu.clet.sfl.safetysecurity.intrusion.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionRepository;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.event.IntrusionEventType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmSeverity;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionSignal;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionZone;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.PanelHealth;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.PanelHealthStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SignalType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.integration.IntrusionIntegrationInbox;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S162-01/04: authenticated ingestion of panel signals and health, normalisation into SOC
 * alarms, and the debounce/coalesce logic that stops a flapping panel from flooding the SOC queue.
 *
 * <h2>Deliberate scope cut: schedule-driven auto-arming</h2>
 *
 * <p>{@link IntrusionZone#armSchedule} is kept as plain text for the same reason
 * {@code accesscontrol.domain.model.AccessZone#schedule()} is: no structured schedule DSL exists yet
 * in this codebase to parse "arm at 19:00" against. Scheduled arming is therefore confirmed
 * explicitly through {@code IntrusionZoneService#confirmArmed}, not computed here from the schedule
 * text - a natural next slice once a schedule DSL exists, not a gap in what ships.
 */
@Service
public class IntrusionIngestionService {

    /** Five or more signals for the same open alarm inside two minutes reads as a flapping panel
     * rather than a genuine, escalating event. */
    private static final int FLAP_THRESHOLD = 5;
    private static final Duration FLAP_WINDOW = Duration.ofMinutes(2);

    private final IntrusionRepository repository;
    private final IntrusionIntegrationInbox inbox;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public IntrusionIngestionService(IntrusionRepository repository, IntrusionIntegrationInbox inbox,
            AuditPort audit, IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.inbox = inbox;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record IngestSignal(String source, String externalEventId, Instant signedAt, String signature,
            String rawPayload, Map<String, Object> payload, String siteCode, String panelId, String zoneCode,
            SignalType signalType, Instant occurredAt, ActorContext actor) {
    }

    public record ReportHealth(String source, String externalEventId, Instant signedAt, String signature,
            String rawPayload, Map<String, Object> payload, String siteCode, String panelId, String zoneCode,
            PanelHealthStatus status, Instant observedAt, ActorContext actor) {
    }

    @Transactional
    public IntrusionSignal ingest(IngestSignal command) {
        inbox.accept(new IntrusionIntegrationInbox.InboundMessage(command.source(), command.externalEventId(),
                "INTRUSION_SIGNAL", command.siteCode(), command.signedAt(), command.signature(),
                command.rawPayload(), command.payload(), List.of("panelId", "zoneCode", "signalType"),
                command.actor()));

        Optional<IntrusionSignal> existing = repository.findSignalByExternalId(command.source(),
                command.externalEventId());
        if (existing.isPresent()) {
            return existing.get();
        }

        ActorContext actor = command.actor();
        Instant now = clock.instant();
        IntrusionSignal signal = IntrusionSignal.ingest(UUID.randomUUID(), command.siteCode(), command.source(),
                command.externalEventId(), command.panelId(), command.zoneCode(), command.signalType(),
                command.occurredAt(), actor.actorId(), now, SourceChannel.INTEGRATION, actor.correlationId());
        IntrusionSignal saved = repository.saveSignal(signal);
        audit.record(actor, SourceChannel.INTEGRATION.name(), saved.siteCode(), "INTRUSION_SIGNAL_INGESTED",
                "IntrusionSignal", saved.id().toString(), null, saved, null);

        switch (command.signalType()) {
            case RESTORATION -> restoreZone(saved, actor);
            case FAULT -> {
                updatePanelHealth(saved.siteCode(), saved.panelId(), saved.zoneCode(), PanelHealthStatus.FAULT,
                        saved.occurredAt(), actor);
                raiseOrCoalesce(saved.siteCode(), saved.panelId(), saved.zoneCode(), AlarmType.DEVICE_FAULT,
                        saved.occurredAt(), actor);
            }
            case ZONE_ALARM -> raiseOrCoalesce(saved.siteCode(), saved.panelId(), saved.zoneCode(),
                    AlarmType.ZONE_ALARM, saved.occurredAt(), actor);
            case TAMPER -> raiseOrCoalesce(saved.siteCode(), saved.panelId(), saved.zoneCode(), AlarmType.TAMPER,
                    saved.occurredAt(), actor);
        }
        return saved;
    }

    @Transactional
    public PanelHealth reportHealth(ReportHealth command) {
        inbox.accept(new IntrusionIntegrationInbox.InboundMessage(command.source(), command.externalEventId(),
                "PANEL_HEALTH", command.siteCode(), command.signedAt(), command.signature(), command.rawPayload(),
                command.payload(), List.of("panelId", "zoneCode", "status"), command.actor()));
        return updatePanelHealth(command.siteCode(), command.panelId(), command.zoneCode(), command.status(),
                command.observedAt(), command.actor());
    }

    private PanelHealth updatePanelHealth(String siteCode, String panelId, String zoneCode,
            PanelHealthStatus status, Instant observedAt, ActorContext actor) {
        Instant now = clock.instant();
        Optional<PanelHealth> existing = repository.findPanelHealth(siteCode, panelId);
        PanelHealthStatus previousStatus = existing.map(PanelHealth::status).orElse(null);
        PanelHealth health = existing
                .map(h -> h.update(status, observedAt, actor.actorId(), now, SourceChannel.INTEGRATION,
                        actor.correlationId()))
                .orElseGet(() -> PanelHealth.report(UUID.randomUUID(), siteCode, panelId, zoneCode, status,
                        observedAt, actor.actorId(), now, SourceChannel.INTEGRATION, actor.correlationId()));
        PanelHealth saved = repository.savePanelHealth(health);
        if (previousStatus != saved.status()) {
            audit.record(actor, SourceChannel.INTEGRATION.name(), saved.siteCode(), "INTRUSION_PANEL_HEALTH_CHANGED",
                    "PanelHealth", saved.id().toString(), existing.orElse(null), saved, null);
            events.publish(IntrusionEventType.PANEL_HEALTH_CHANGED.eventType(),
                    IntrusionEventType.PANEL_HEALTH_CHANGED.version(), "PanelHealth", saved.id().toString(),
                    saved.siteCode(), actor, Map.of("panelId", saved.panelId(), "status", saved.status().name()));
        }
        return saved;
    }

    /** SRS-SFL-S162-01: "restoration" closes whatever this panel/zone still has open, and returns the
     * panel to healthy. */
    private void restoreZone(IntrusionSignal signal, ActorContext actor) {
        updatePanelHealth(signal.siteCode(), signal.panelId(), signal.zoneCode(), PanelHealthStatus.ONLINE,
                signal.occurredAt(), actor);
        for (AlarmType type : AlarmType.values()) {
            repository.findOpenAlarm(signal.siteCode(), signal.panelId(), signal.zoneCode(), type)
                    .ifPresent(alarm -> resolveFromRestoration(alarm, actor));
        }
    }

    private void resolveFromRestoration(IntrusionAlarm alarm, ActorContext actor) {
        Instant now = clock.instant();
        IntrusionAlarm resolved = repository.saveAlarm(alarm.resolve("system:panel-restoration", now,
                SourceChannel.INTEGRATION, actor.correlationId()));
        audit.record(actor, SourceChannel.INTEGRATION.name(), resolved.siteCode(), "INTRUSION_ALARM_RESOLVED",
                "IntrusionAlarm", resolved.id().toString(), alarm, resolved, null);
        events.publish(IntrusionEventType.ALARM_RESOLVED.eventType(), IntrusionEventType.ALARM_RESOLVED.version(),
                "IntrusionAlarm", resolved.id().toString(), resolved.siteCode(), actor,
                Map.of("reason", "panel-restoration"));
    }

    /**
     * SRS-SFL-S162-01/04: raises a new alarm, or coalesces into an already-open one for the same
     * panel/zone/type; once the coalesced signal count crosses the flap threshold inside the flap
     * window, flags the panel as flapping instead of continuing to look like an escalating alarm.
     *
     * <p>Package-visible (not {@code private}) because {@code IntrusionZoneService} also raises
     * {@link AlarmType#ARM_FAILURE} through this same path for "a zone that fails to arm ... raises
     * an exception to the SOC" (SRS-SFL-S162-04) - one coalesce/flap mechanism, not two.
     */
    void raiseOrCoalesce(String siteCode, String panelId, String zoneCode, AlarmType alarmType, Instant signalAt,
            ActorContext actor) {
        Instant now = clock.instant();
        Optional<IntrusionAlarm> open = repository.findOpenAlarm(siteCode, panelId, zoneCode, alarmType);
        if (open.isPresent()) {
            IntrusionAlarm coalesced = open.get().coalesce(signalAt, actor.actorId(), now, SourceChannel.INTEGRATION,
                    actor.correlationId());
            boolean flapping = coalesced.signalCount() >= FLAP_THRESHOLD
                    && Duration.between(coalesced.firstSignalAt(), signalAt).compareTo(FLAP_WINDOW) <= 0;
            IntrusionAlarm saved = flapping
                    ? repository.saveAlarm(coalesced.markFlapping(actor.actorId(), now, SourceChannel.INTEGRATION,
                            actor.correlationId()))
                    : repository.saveAlarm(coalesced);
            audit.record(actor, SourceChannel.INTEGRATION.name(), saved.siteCode(),
                    flapping ? "INTRUSION_ALARM_COALESCED_FLAPPING" : "INTRUSION_ALARM_COALESCED", "IntrusionAlarm",
                    saved.id().toString(), open.get(), saved, null);
            if (flapping) {
                updatePanelHealth(siteCode, panelId, zoneCode, PanelHealthStatus.FLAPPING, signalAt, actor);
            }
            return;
        }

        Optional<IntrusionZone> zone = repository.findZoneByCode(siteCode, zoneCode);
        boolean elevated = zone.map(z -> z.protectedZone() || z.examinationMode()).orElse(false);
        AlarmSeverity severity = baseSeverity(alarmType);
        if (elevated) {
            severity = severity.elevate();
        }
        Instant ackDueAt = signalAt.plus(elevated ? ackWindow(severity).dividedBy(2) : ackWindow(severity));

        IntrusionAlarm raised = IntrusionAlarm.raise(UUID.randomUUID(), siteCode, panelId, zoneCode, alarmType,
                severity, signalAt, ackDueAt, actor.actorId(), now, SourceChannel.INTEGRATION, actor.correlationId());
        IntrusionAlarm saved = repository.saveAlarm(raised);
        audit.record(actor, SourceChannel.INTEGRATION.name(), saved.siteCode(), "INTRUSION_ALARM_RAISED",
                "IntrusionAlarm", saved.id().toString(), null, saved, null);
        events.publish(IntrusionEventType.ALARM_RAISED.eventType(), IntrusionEventType.ALARM_RAISED.version(),
                "IntrusionAlarm", saved.id().toString(), saved.siteCode(), actor,
                Map.of("alarmType", alarmType.name(), "severity", severity.name(), "zoneCode", zoneCode));
    }

    private static AlarmSeverity baseSeverity(AlarmType alarmType) {
        return switch (alarmType) {
            case TAMPER -> AlarmSeverity.CRITICAL;
            case ZONE_ALARM, ARM_FAILURE -> AlarmSeverity.HIGH;
            case DEVICE_FAULT -> AlarmSeverity.MEDIUM;
        };
    }

    private static Duration ackWindow(AlarmSeverity severity) {
        return switch (severity) {
            case CRITICAL -> Duration.ofMinutes(2);
            case HIGH -> Duration.ofMinutes(5);
            case MEDIUM -> Duration.ofMinutes(15);
            case LOW -> Duration.ofMinutes(30);
        };
    }
}
