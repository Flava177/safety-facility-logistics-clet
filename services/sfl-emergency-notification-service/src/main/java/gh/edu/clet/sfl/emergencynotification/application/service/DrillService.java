package gh.edu.clet.sfl.emergencynotification.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.emergencynotification.application.port.AuditPort;
import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.emergencynotification.domain.event.EmergencyEventType;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyException;
import gh.edu.clet.sfl.emergencynotification.domain.model.DrillRun;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecordMetadata;
import gh.edu.clet.sfl.emergencynotification.domain.model.SiteCode;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS-SFL-S174-05: notification drills — rehearse the activation path and record performance metrics. */
@Service
public class DrillService {

    private final EmergencyRepository repository;
    private final EmergencyAccessPolicy access;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public DrillService(EmergencyRepository repository, EmergencyAccessPolicy access, AuditPort audit,
            IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record StartDrill(String siteCode, UUID scenarioId, int targetRecipients, String notes, ActorContext actor,
            SourceChannel channel) {}

    @Transactional
    public DrillRun start(StartDrill c) {
        SiteCode site = SiteCode.of(c.siteCode());
        access.require(c.actor(), SflPermission.EMERGENCY_ACTIVATION_CREATE, site.value(), "DrillRun", null);
        var drill = new DrillRun(UUID.randomUUID(), EmergencyNumbers.next("DRILL"), site, c.scenarioId(),
                DrillRun.Status.RUNNING, Math.max(c.targetRecipients(), 0), 0, 0, null, clock.instant(), null,
                c.notes(), RecordMetadata.createdBy(c.actor().actorId(), clock.instant(), c.channel(),
                        c.actor().correlationId()));
        var saved = repository.saveDrill(drill);
        audit.record(c.actor(), c.channel(), site.value(), "CREATE", "DrillRun", saved.id().toString(), null, saved,
                null);
        return saved;
    }

    @Transactional
    public DrillRun complete(UUID id, int reached, int acknowledged, long activationMillis, String notes,
            ActorContext actor, SourceChannel channel) {
        var before = repository.findDrill(id).orElseThrow(() -> EmergencyException.notFound("DrillRun", id));
        access.require(actor, SflPermission.EMERGENCY_ACTIVATION_CREATE, before.siteCode().value(), "DrillRun",
                id.toString());
        var completed = before.complete(reached, acknowledged, activationMillis, clock.instant(), notes,
                before.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId()));
        var saved = repository.saveDrill(completed);
        audit.record(actor, channel, saved.siteCode().value(), "STATE_TRANSITION", "DrillRun", id.toString(), before,
                saved, null);
        events.publish(EmergencyEventType.EMERGENCY_DRILL_COMPLETED, "DrillRun", saved.id().toString(),
                saved.siteCode().value(), actor, Map.of("drillId", saved.id(), "acknowledgementRatePercent",
                        saved.acknowledgementRatePercent(), "activationMillis", activationMillis));
        return saved;
    }

    public DrillRun get(UUID id, ActorContext actor) {
        var d = repository.findDrill(id).orElseThrow(() -> EmergencyException.notFound("DrillRun", id));
        access.require(actor, SflPermission.EMERGENCY_ACTIVATION_READ, d.siteCode().value(), "DrillRun", id.toString());
        return d;
    }

    public EmergencyRepository.EmergencyPage<DrillRun> list(String site, DrillRun.Status status, UUID scenarioId,
            java.time.Instant from, java.time.Instant to, EmergencyRepository.Paging paging, ActorContext actor) {
        access.require(actor, SflPermission.EMERGENCY_ACTIVATION_READ, site, "DrillRun", null);
        return repository.findDrills(new EmergencyRepository.DrillQuery(List.of(SiteCode.of(site).value()), status,
                scenarioId, from, to, paging));
    }
}
