package gh.edu.clet.sfl.safetysecurity.intrusion.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionPanelGatewayPort;
import gh.edu.clet.sfl.safetysecurity.intrusion.application.port.IntrusionRepository;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.event.IntrusionEventType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionException;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmType;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverride;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverrideStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionZone;
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
 * SRS-SFL-S162-04: zone arming/disarming schedules and state, the authorised time-bound disarm and
 * its automatic reversion, and Examination Mode's mandatory arming. Raises {@link AlarmType#ARM_FAILURE}
 * through {@code IntrusionIngestionService#raiseOrCoalesce} - see that method's javadoc for why zone
 * administration shares the alarm-raising path rather than duplicating it.
 */
@Service
public class IntrusionZoneService {

    private static final Logger log = LoggerFactory.getLogger(IntrusionZoneService.class);

    private final IntrusionRepository repository;
    private final IntrusionPanelGatewayPort panelGateway;
    private final IntrusionIngestionService ingestion;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final IntrusionAccessPolicy access;
    private final Clock clock;

    public IntrusionZoneService(IntrusionRepository repository, IntrusionPanelGatewayPort panelGateway,
            IntrusionIngestionService ingestion, AuditPort audit, IntegrationEventPublisher events,
            IntrusionAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.panelGateway = panelGateway;
        this.ingestion = ingestion;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    public record DefineZone(String siteCode, String zoneCode, String name, String locationRef, String armSchedule,
            boolean protectedZone, ActorContext actor) {
    }

    public record ConfirmArmed(UUID zoneId, boolean panelConfirmed, Long expectedVersion, ActorContext actor) {
    }

    public record EnterExaminationMode(UUID zoneId, Instant until, Long expectedVersion, ActorContext actor) {
    }

    public record RequestDisarm(String siteCode, String zoneCode, String reason, String approverId, Instant startsAt,
            Instant expiresAt, ActorContext actor) {
    }

    @Transactional
    public IntrusionZone define(DefineZone command) {
        ActorContext actor = command.actor();
        access.require(actor, SflPermission.INTRUSION_ZONE_MANAGE, command.siteCode(), "IntrusionZone", null);
        Instant now = clock.instant();
        IntrusionZone zone = IntrusionZone.define(UUID.randomUUID(), command.siteCode(), command.zoneCode(),
                command.name(), command.locationRef(), command.armSchedule(), command.protectedZone(),
                actor.actorId(), now, SourceChannel.WEB, actor.correlationId());
        return repository.saveZone(zone);
    }

    /** SRS-SFL-S162-04: "the zone is armed and its state confirmed from the panel"; if the panel
     * disputes it, raises {@link AlarmType#ARM_FAILURE} to the SOC instead of silently marking armed. */
    @Transactional
    public IntrusionZone confirmArmed(ConfirmArmed command) {
        ActorContext actor = command.actor();
        IntrusionZone zone = requireZone(command.zoneId());
        access.require(actor, SflPermission.INTRUSION_ZONE_MANAGE, zone.siteCode(), "IntrusionZone",
                command.zoneId().toString());
        zone.metadata().requireVersion(command.expectedVersion());
        Instant now = clock.instant();
        if (!command.panelConfirmed()) {
            ingestion.raiseOrCoalesce(zone.siteCode(), zone.zoneCode(), zone.zoneCode(), AlarmType.ARM_FAILURE, now,
                    actor);
            return zone;
        }
        IntrusionZone confirmed = repository.saveZone(zone.confirmArmed(actor.actorId(), now, SourceChannel.WEB,
                actor.correlationId()));
        events.publish(IntrusionEventType.ZONE_ARMED.eventType(), IntrusionEventType.ZONE_ARMED.version(),
                "IntrusionZone", confirmed.id().toString(), confirmed.siteCode(), actor,
                Map.of("zoneCode", confirmed.zoneCode()));
        return confirmed;
    }

    /** A panel-reported arm state that was not requested by an active {@link DisarmOverride} - "is
     * disarmed out of policy" (SRS-SFL-S162-04) - also raises {@link AlarmType#ARM_FAILURE}. */
    @Transactional
    public IntrusionZone reportZoneState(String siteCode, String zoneCode, boolean panelReportsArmed,
            Instant observedAt, ActorContext actor) {
        IntrusionZone zone = repository.findZoneByCode(siteCode, zoneCode)
                .orElseThrow(() -> IntrusionException.notFound("IntrusionZone", null));
        boolean coveredByOverride = repository.findDisarmOverridesByStatus(siteCode, DisarmOverrideStatus.ACTIVE)
                .stream().anyMatch(o -> o.zoneCode().equals(zoneCode) && o.isEffective(observedAt));
        if (!panelReportsArmed && !coveredByOverride && zone.protectedZone()) {
            ingestion.raiseOrCoalesce(siteCode, zoneCode, zoneCode, AlarmType.ARM_FAILURE, observedAt, actor);
        }
        return repository.saveZone(zone.setArmed(panelReportsArmed, actor.actorId(), clock.instant(),
                SourceChannel.INTEGRATION, actor.correlationId()));
    }

    @Transactional
    public IntrusionZone enterExaminationMode(EnterExaminationMode command) {
        ActorContext actor = command.actor();
        IntrusionZone zone = requireZone(command.zoneId());
        access.require(actor, SflPermission.INTRUSION_ZONE_MANAGE, zone.siteCode(), "IntrusionZone",
                command.zoneId().toString());
        zone.metadata().requireVersion(command.expectedVersion());
        IntrusionZone tightened = repository.saveZone(zone.enterExaminationMode(command.until(), actor.actorId(),
                clock.instant(), SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), tightened.siteCode(), "INTRUSION_ZONE_EXAM_MODE_ENTERED",
                "IntrusionZone", tightened.id().toString(), zone, tightened, null);
        return tightened;
    }

    @Transactional
    public IntrusionZone exitExaminationMode(UUID zoneId, Long expectedVersion, ActorContext actor) {
        IntrusionZone zone = requireZone(zoneId);
        access.require(actor, SflPermission.INTRUSION_ZONE_MANAGE, zone.siteCode(), "IntrusionZone",
                zoneId.toString());
        zone.metadata().requireVersion(expectedVersion);
        return repository.saveZone(zone.exitExaminationMode(actor.actorId(), clock.instant(), SourceChannel.WEB,
                actor.correlationId()));
    }

    @Transactional(readOnly = true)
    public List<IntrusionZone> forSite(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.INTRUSION_ZONE_READ, siteCode, "IntrusionZone", null);
        return repository.findZonesBySite(siteCode);
    }

    @Transactional
    public DisarmOverride requestDisarm(RequestDisarm command) {
        ActorContext actor = command.actor();
        access.requireDisarmAuthority(actor, SflPermission.INTRUSION_ZONE_DISARM, command.siteCode(), null);
        IntrusionZone zone = repository.findZoneByCode(command.siteCode(), command.zoneCode())
                .orElseThrow(() -> IntrusionException.notFound("IntrusionZone", null));
        Instant now = clock.instant();
        DisarmOverride override = DisarmOverride.request(UUID.randomUUID(), command.siteCode(), command.zoneCode(),
                command.reason(), actor.actorId(), command.approverId(), command.startsAt(), command.expiresAt(),
                actor.actorId(), now, SourceChannel.WEB, actor.correlationId());
        DisarmOverride saved = repository.saveDisarmOverride(override);
        panelGateway.requestDisarm(saved, actor);
        repository.saveZone(zone.setArmed(false, actor.actorId(), now, SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), saved.siteCode(), "INTRUSION_ZONE_DISARMED", "DisarmOverride",
                saved.id().toString(), null, saved, null);
        events.publish(IntrusionEventType.ZONE_DISARMED.eventType(), IntrusionEventType.ZONE_DISARMED.version(),
                "DisarmOverride", saved.id().toString(), saved.siteCode(), actor,
                Map.of("zoneCode", saved.zoneCode()));
        return saved;
    }

    @Transactional
    public DisarmOverride revokeDisarm(UUID id, ActorContext actor) {
        DisarmOverride override = repository.findDisarmOverride(id)
                .orElseThrow(() -> IntrusionException.notFound("DisarmOverride", id));
        access.requireDisarmAuthority(actor, SflPermission.INTRUSION_ZONE_DISARM, override.siteCode(),
                id.toString());
        Instant now = clock.instant();
        DisarmOverride revoked = repository.saveDisarmOverride(override.revoke(actor.actorId(), now,
                SourceChannel.WEB, actor.correlationId()));
        reArmZone(revoked, actor, now, SourceChannel.WEB);
        return revoked;
    }

    /** The automatic re-arm sweep - SRS-SFL-S162-04: "reverts to armed automatically at expiry" -
     * mirrors {@code AccessOverrideService#expireOverdueOverrides}. */
    @Scheduled(fixedDelayString = "${sfl.intrusion.disarm-sweep.fixed-delay:PT10S}")
    @Transactional
    public void expireOverdueDisarms() {
        Instant now = clock.instant();
        for (DisarmOverride override : repository.findActiveDisarmOverrides()) {
            if (!override.expiresAt().isAfter(now)) {
                ActorContext system = systemActor(override.siteCode());
                DisarmOverride expired = repository.saveDisarmOverride(override.expire(now, SourceChannel.SCHEDULER,
                        "disarm-expiry-sweep"));
                reArmZone(expired, system, now, SourceChannel.SCHEDULER);
                log.info("Intrusion disarm {} expired automatically at site {}", expired.id(), expired.siteCode());
            }
        }
    }

    private void reArmZone(DisarmOverride override, ActorContext actor, Instant now, SourceChannel channel) {
        panelGateway.requestReArm(override.siteCode(), override.zoneCode(), actor);
        repository.findZoneByCode(override.siteCode(), override.zoneCode())
                .ifPresent(zone -> repository.saveZone(zone.setArmed(true, actor.actorId(), now, channel,
                        actor.correlationId())));
        audit.record(actor, channel.name(), override.siteCode(), "INTRUSION_ZONE_REARMED", "DisarmOverride",
                override.id().toString(), null, override, null);
        events.publish(IntrusionEventType.ZONE_ARMED.eventType(), IntrusionEventType.ZONE_ARMED.version(),
                "IntrusionZone", override.id().toString(), override.siteCode(), actor,
                Map.of("zoneCode", override.zoneCode()));
    }

    private IntrusionZone requireZone(UUID id) {
        return repository.findZone(id).orElseThrow(() -> IntrusionException.notFound("IntrusionZone", id));
    }

    private static ActorContext systemActor(String siteCode) {
        return new ActorContext(new gh.edu.clet.sfl.common.security.SiteScopedPrincipal(
                "system:intrusion-disarm-expiry-sweep", "Intrusion Disarm Expiry Sweep",
                Set.of(gh.edu.clet.sfl.common.security.SflRole.SFL_ADMIN), Set.of("*"), true),
                "disarm-expiry-sweep");
    }
}
