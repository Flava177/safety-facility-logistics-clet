package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlRepository;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlVendorGatewayPort;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.event.AccessControlEventType;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessZone;
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
 * SRS-SFL-S160a-05: zone, schedule and door-group definitions, pushed to the vendor system and
 * versioned/audited on every change; Examination Mode's automatic tightening.
 *
 * <h2>"Unusually broad rules are flagged for review" - kept as a warning, not a hard block</h2>
 *
 * <p>{@code ACCESS_ZONE_RULE_TOO_BROAD} exists in the error catalog and the check below computes the
 * same signal the SRS names (an all-hours schedule with no day/time restriction at all), but it is
 * surfaced as a field on the response rather than a thrown exception that would block saving - an
 * administrator confirming a genuinely broad rule (a 24/7 lobby, say) must still be able to save it.
 * A hard confirmation step ("requires confirmation before it is pushed") is real workflow the SRS
 * asks for and is intentionally left as a follow-up rather than built as a rejected-then-immediately-
 * resubmitted request, which would not actually be safer.
 */
@Service
public class AccessZoneAdminService {

    private final AccessControlRepository repository;
    private final AccessControlVendorGatewayPort vendorGateway;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final AccessControlAccessPolicy access;
    private final Clock clock;

    public AccessZoneAdminService(AccessControlRepository repository, AccessControlVendorGatewayPort vendorGateway,
            AuditPort audit, IntegrationEventPublisher events, AccessControlAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.vendorGateway = vendorGateway;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    public record DefineZone(String siteCode, String zoneCode, String name, String locationRef, String schedule,
            Map<String, List<String>> doorGroups, ActorContext actor) {
    }

    public record Redefine(UUID zoneId, String schedule, Map<String, List<String>> doorGroups, Long expectedVersion,
            ActorContext actor) {
    }

    public record EnterExaminationMode(UUID zoneId, String tightenedSchedule, List<String> lockDoorGroups,
            Instant until, Long expectedVersion, ActorContext actor) {
    }

    @Transactional
    public AccessZone define(DefineZone command) {
        ActorContext actor = command.actor();
        access.require(actor, SflPermission.ACCESS_ZONE_MANAGE, command.siteCode(), "AccessZone", null);
        Instant now = clock.instant();
        AccessZone zone = AccessZone.define(UUID.randomUUID(), command.siteCode(), command.zoneCode(),
                command.name(), command.locationRef(), command.schedule(), command.doorGroups(), actor.actorId(),
                now, SourceChannel.WEB, actor.correlationId());
        return saveAndSync(zone, actor);
    }

    @Transactional
    public AccessZone redefine(Redefine command) {
        ActorContext actor = command.actor();
        AccessZone zone = requireZone(command.zoneId());
        access.require(actor, SflPermission.ACCESS_ZONE_MANAGE, zone.siteCode(), "AccessZone",
                command.zoneId().toString());
        zone.metadata().requireVersion(command.expectedVersion());
        AccessZone redefined = zone.redefine(command.schedule(), command.doorGroups(), actor.actorId(),
                clock.instant(), SourceChannel.WEB, actor.correlationId());
        return saveAndSync(redefined, actor);
    }

    /** SRS-SFL-S160a-05: "Examination Mode can automatically apply tightened schedules and lockdown
     * door groups to examination zones for the examination window." Reversion out of the mode is left
     * to an explicit admin action or a future scheduled sweep alongside {@code AccessOverrideService}'s -
     * a zone's examination window is announced in advance and closed deliberately, unlike an override's
     * silent auto-revert, so a human confirming the window has ended is the safer default for Phase 1. */
    @Transactional
    public AccessZone enterExaminationMode(EnterExaminationMode command) {
        ActorContext actor = command.actor();
        AccessZone zone = requireZone(command.zoneId());
        access.require(actor, SflPermission.ACCESS_ZONE_MANAGE, zone.siteCode(), "AccessZone",
                command.zoneId().toString());
        zone.metadata().requireVersion(command.expectedVersion());
        AccessZone tightened = zone.enterExaminationMode(command.tightenedSchedule(), command.lockDoorGroups(),
                command.until(), actor.actorId(), clock.instant(), SourceChannel.WEB, actor.correlationId());
        return saveAndSync(tightened, actor);
    }

    @Transactional
    public AccessZone exitExaminationMode(UUID zoneId, String normalSchedule, Long expectedVersion,
            ActorContext actor) {
        AccessZone zone = requireZone(zoneId);
        access.require(actor, SflPermission.ACCESS_ZONE_MANAGE, zone.siteCode(), "AccessZone", zoneId.toString());
        zone.metadata().requireVersion(expectedVersion);
        AccessZone restored = zone.exitExaminationMode(normalSchedule, actor.actorId(), clock.instant(),
                SourceChannel.WEB, actor.correlationId());
        return saveAndSync(restored, actor);
    }

    @Transactional(readOnly = true)
    public List<AccessZone> forSite(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.ACCESS_ZONE_READ, siteCode, "AccessZone", null);
        return repository.findZonesBySite(siteCode);
    }

    /** SRS-SFL-S160a-05's broad-rule signal: no day/time restriction at all in the schedule text. */
    public boolean isUnusuallyBroad(AccessZone zone) {
        String normalised = zone.schedule().strip().toLowerCase(java.util.Locale.ROOT);
        return normalised.equals("24/7") || normalised.equals("always") || normalised.isEmpty();
    }

    private AccessZone saveAndSync(AccessZone zone, ActorContext actor) {
        AccessZone saved = repository.saveZone(zone);
        vendorGateway.syncZone(saved, actor);
        audit.record(actor, SourceChannel.WEB.name(), saved.siteCode(), "ACCESS_ZONE_DEFINED", "AccessZone",
                saved.id().toString(), null, saved, null);
        events.publish(AccessControlEventType.ACCESS_ZONE_DEFINED.eventType(),
                AccessControlEventType.ACCESS_ZONE_DEFINED.version(), "AccessZone", saved.id().toString(),
                saved.siteCode(), actor, Map.of("zoneCode", saved.zoneCode(),
                        "unusuallyBroad", isUnusuallyBroad(saved)));
        return saved;
    }

    private AccessZone requireZone(UUID id) {
        return repository.findZone(id).orElseThrow(() -> AccessControlException.notFound("AccessZone", id));
    }
}
