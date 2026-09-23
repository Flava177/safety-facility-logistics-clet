package gh.edu.clet.sfl.safetysecurity.intrusion.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionIncidentSeedingPort;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionRepository;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.event.IntrusionEventType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionException;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmSeverity;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S162-02/03: the SOC alarm queue's human workflow - acknowledgement, evidence/incident
 * linkage, resolution - and the automatic severity-based escalation sweep that mirrors
 * {@code accesscontrol.application.service.AccessOverrideService}'s expiry sweep shape.
 */
@Service
public class IntrusionAlarmService {

    private static final Logger log = LoggerFactory.getLogger(IntrusionAlarmService.class);

    private final IntrusionRepository repository;
    private final IntrusionIncidentSeedingPort incidentSeeding;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final IntrusionAccessPolicy access;
    private final Clock clock;

    public IntrusionAlarmService(IntrusionRepository repository, IntrusionIncidentSeedingPort incidentSeeding,
            AuditPort audit, IntegrationEventPublisher events, IntrusionAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.incidentSeeding = incidentSeeding;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public IntrusionAlarm acknowledge(UUID id, ActorContext actor) {
        IntrusionAlarm alarm = requireAlarm(id);
        access.require(actor, SflPermission.INTRUSION_ALARM_ACKNOWLEDGE, alarm.siteCode(), "IntrusionAlarm",
                id.toString());
        Instant now = clock.instant();
        IntrusionAlarm acknowledged = repository.saveAlarm(alarm.acknowledge(actor.actorId(), now, SourceChannel.WEB,
                actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), acknowledged.siteCode(), "INTRUSION_ALARM_ACKNOWLEDGED",
                "IntrusionAlarm", acknowledged.id().toString(), alarm, acknowledged, null);
        events.publish(IntrusionEventType.ALARM_ACKNOWLEDGED.eventType(),
                IntrusionEventType.ALARM_ACKNOWLEDGED.version(), "IntrusionAlarm", acknowledged.id().toString(),
                acknowledged.siteCode(), actor, Map.of("alarmId", acknowledged.id().toString()));
        return acknowledged;
    }

    @Transactional
    public IntrusionAlarm resolve(UUID id, ActorContext actor) {
        IntrusionAlarm alarm = requireAlarm(id);
        access.require(actor, SflPermission.INTRUSION_ALARM_RESOLVE, alarm.siteCode(), "IntrusionAlarm",
                id.toString());
        Instant now = clock.instant();
        IntrusionAlarm resolved = repository.saveAlarm(alarm.resolve(actor.actorId(), now, SourceChannel.WEB,
                actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), resolved.siteCode(), "INTRUSION_ALARM_RESOLVED",
                "IntrusionAlarm", resolved.id().toString(), alarm, resolved, null);
        events.publish(IntrusionEventType.ALARM_RESOLVED.eventType(), IntrusionEventType.ALARM_RESOLVED.version(),
                "IntrusionAlarm", resolved.id().toString(), resolved.siteCode(), actor,
                Map.of("alarmId", resolved.id().toString()));
        return resolved;
    }

    /** SRS-SFL-S162-03: "an acknowledged alarm can be paired with surveillance (S161) evidence" - a
     * soft reference by id/description, not a compile-time dependency on the cctv module. */
    @Transactional
    public IntrusionAlarm linkEvidence(UUID id, String evidenceReference, ActorContext actor) {
        IntrusionAlarm alarm = requireAlarm(id);
        access.require(actor, SflPermission.INTRUSION_ALARM_LINK_EVIDENCE, alarm.siteCode(), "IntrusionAlarm",
                id.toString());
        IntrusionAlarm linked = repository.saveAlarm(alarm.linkEvidence(evidenceReference, actor.actorId(),
                clock.instant(), SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), linked.siteCode(), "INTRUSION_ALARM_EVIDENCE_LINKED",
                "IntrusionAlarm", linked.id().toString(), alarm, linked, null);
        return linked;
    }

    /** SRS-SFL-S162-03: "escalated into a security incident (S163) with a full trail" - an operator
     * decision on an already-acknowledged alarm, not automatic on every alarm. */
    @Transactional
    public IntrusionAlarm linkIncident(UUID id, ActorContext actor) {
        IntrusionAlarm alarm = requireAlarm(id);
        access.require(actor, SflPermission.INTRUSION_ALARM_LINK_INCIDENT, alarm.siteCode(), "IntrusionAlarm",
                id.toString());
        UUID incidentId = incidentSeeding.seed(alarm.siteCode(), describeForIncident(alarm), actor);
        IntrusionAlarm linked = repository.saveAlarm(alarm.linkIncident(incidentId, actor.actorId(), clock.instant(),
                SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), linked.siteCode(), "INTRUSION_ALARM_INCIDENT_LINKED",
                "IntrusionAlarm", linked.id().toString(), alarm, linked, null);
        return linked;
    }

    @Transactional(readOnly = true)
    public List<IntrusionAlarm> queue(String siteCode, AlarmStatus status, ActorContext actor) {
        access.require(actor, SflPermission.INTRUSION_ALARM_READ, siteCode, "IntrusionAlarm", null);
        return repository.findAlarmsByStatus(siteCode, status);
    }

    /**
     * The automatic severity-based escalation sweep - SRS-SFL-S162-02: "escalates automatically per
     * the configured rule". Every ten seconds, deliberately aggressive for a Phase 1 build with no
     * live panel traffic yet, mirroring {@code AccessOverrideService}'s expiry sweep; a real
     * deployment tunes {@code sfl.intrusion.escalation-sweep.fixed-delay}.
     */
    @Scheduled(fixedDelayString = "${sfl.intrusion.escalation-sweep.fixed-delay:PT10S}")
    @Transactional
    public void escalateOverdueAlarms() {
        Instant now = clock.instant();
        for (IntrusionAlarm alarm : repository.findRaisedAlarms()) {
            if (alarm.isOverdue(now)) {
                String target = alarm.severity() == AlarmSeverity.CRITICAL ? "NECC" : "SECURITY_DIRECTOR";
                ActorContext system = systemActor(alarm.siteCode());
                IntrusionAlarm escalated = repository.saveAlarm(alarm.escalate(target, now, SourceChannel.SCHEDULER,
                        "escalation-sweep"));
                audit.record(system, SourceChannel.SCHEDULER.name(), escalated.siteCode(), "INTRUSION_ALARM_ESCALATED",
                        "IntrusionAlarm", escalated.id().toString(), alarm, escalated, null);
                events.publish(IntrusionEventType.ALARM_ESCALATED.eventType(),
                        IntrusionEventType.ALARM_ESCALATED.version(), "IntrusionAlarm", escalated.id().toString(),
                        escalated.siteCode(), system, Map.of("escalatedTo", target));
                log.info("Intrusion alarm {} escalated automatically to {} at site {}", escalated.id(), target,
                        escalated.siteCode());
            }
        }
    }

    private static String describeForIncident(IntrusionAlarm alarm) {
        return "Intrusion alarm " + alarm.alarmType() + " at zone " + alarm.zoneCode() + " (panel " + alarm.panelId()
                + ").";
    }

    private IntrusionAlarm requireAlarm(UUID id) {
        return repository.findAlarm(id).orElseThrow(() -> IntrusionException.notFound("IntrusionAlarm", id));
    }

    private static ActorContext systemActor(String siteCode) {
        return new ActorContext(new SiteScopedPrincipal("system:intrusion-escalation-sweep",
                "Intrusion Escalation Sweep", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true), "escalation-sweep");
    }
}
