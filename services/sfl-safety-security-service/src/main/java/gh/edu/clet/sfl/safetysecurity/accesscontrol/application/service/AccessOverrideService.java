package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlRepository;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlVendorGatewayPort;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.event.AccessControlEventType;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessOverride;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.OverrideStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S160a-03: time-bound access overrides, break-glass, and the expiry sweep that restores
 * normal rules automatically - the same "reached only by a scheduled sweep" shape {@code
 * VisitorVisit.markNoShow} documents as a deferred pattern, built here rather than deferred because
 * automatic reversion is this requirement's stated acceptance criterion.
 */
@Service
public class AccessOverrideService {

    private static final Logger log = LoggerFactory.getLogger(AccessOverrideService.class);

    private final AccessControlRepository repository;
    private final AccessControlVendorGatewayPort vendorGateway;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final AccessControlAccessPolicy access;
    private final Clock clock;

    public AccessOverrideService(AccessControlRepository repository, AccessControlVendorGatewayPort vendorGateway,
            AuditPort audit, IntegrationEventPublisher events, AccessControlAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.vendorGateway = vendorGateway;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    public record RequestOverride(String siteCode, String scopeRef, String reason, String approverId,
            Instant startsAt, Instant expiresAt, ActorContext actor) {
    }

    public record RequestBreakGlassOverride(String siteCode, String scopeRef, String reason, Instant startsAt,
            Instant expiresAt, ActorContext actor) {
    }

    @Transactional
    public AccessOverride request(RequestOverride command) {
        ActorContext actor = command.actor();
        access.requireOverrideAuthority(actor, SflPermission.ACCESS_OVERRIDE_CREATE, command.siteCode(), null);
        Instant now = clock.instant();
        AccessOverride override = AccessOverride.request(UUID.randomUUID(), command.siteCode(), command.scopeRef(),
                command.reason(), actor.actorId(), command.approverId(), command.startsAt(), command.expiresAt(),
                actor.actorId(), now, SourceChannel.WEB, actor.correlationId());
        return saveAndSync(override, actor);
    }

    /** SRS-SFL-S160a-03: "pre-authorised roles ... approval recorded after the fact". */
    @Transactional
    public AccessOverride requestBreakGlass(RequestBreakGlassOverride command) {
        ActorContext actor = command.actor();
        access.requireOverrideAuthority(actor, SflPermission.ACCESS_OVERRIDE_BREAK_GLASS, command.siteCode(), null);
        Instant now = clock.instant();
        AccessOverride override = AccessOverride.breakGlass(UUID.randomUUID(), command.siteCode(),
                command.scopeRef(), command.reason(), actor.actorId(), command.startsAt(), command.expiresAt(),
                actor.actorId(), now, SourceChannel.WEB, actor.correlationId());
        return saveAndSync(override, actor);
    }

    @Transactional
    public AccessOverride recordPostHocApproval(UUID id, String approverId, ActorContext actor) {
        AccessOverride override = requireOverride(id);
        access.requireOverrideAuthority(actor, SflPermission.ACCESS_OVERRIDE_BREAK_GLASS, override.siteCode(),
                id.toString());
        AccessOverride approved = repository.saveOverride(override.recordPostHocApproval(approverId, actor.actorId(),
                clock.instant(), SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), approved.siteCode(), "ACCESS_OVERRIDE_APPROVED_POST_HOC",
                "AccessOverride", approved.id().toString(), override, approved, null);
        return approved;
    }

    @Transactional
    public AccessOverride revoke(UUID id, ActorContext actor) {
        AccessOverride override = requireOverride(id);
        access.require(actor, SflPermission.ACCESS_OVERRIDE_CREATE, override.siteCode(), "AccessOverride",
                id.toString());
        AccessOverride revoked = repository.saveOverride(override.revoke(actor.actorId(), clock.instant(),
                SourceChannel.WEB, actor.correlationId()));
        vendorGateway.syncOverrideReversion(revoked.id(), actor);
        audit.record(actor, SourceChannel.WEB.name(), revoked.siteCode(), "ACCESS_OVERRIDE_REVOKED", "AccessOverride",
                revoked.id().toString(), override, revoked, null);
        return revoked;
    }

    @Transactional(readOnly = true)
    public List<AccessOverride> activeForSite(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.ACCESS_OVERRIDE_READ, siteCode, "AccessOverride", null);
        return repository.findOverridesByStatus(siteCode, OverrideStatus.ACTIVE);
    }

    /**
     * The automatic-reversion sweep. Every ten seconds is deliberately aggressive for a Phase 1 build
     * with no live vendor traffic yet; a real deployment tunes {@code
     * sfl.access-control.override-sweep.fixed-delay} once override volume is real.
     */
    @Scheduled(fixedDelayString = "${sfl.access-control.override-sweep.fixed-delay:PT10S}")
    @Transactional
    public void expireOverdueOverrides() {
        Instant now = clock.instant();
        for (AccessOverride override : repository.findActiveOverrides()) {
            if (!override.expiresAt().isAfter(now)) {
                AccessOverride expired = repository.saveOverride(override.expire(now, SourceChannel.SCHEDULER,
                        "override-expiry-sweep"));
                vendorGateway.syncOverrideReversion(expired.id(), systemActor(expired.siteCode()));
                audit.record(systemActor(expired.siteCode()), SourceChannel.SCHEDULER.name(), expired.siteCode(),
                        "ACCESS_OVERRIDE_EXPIRED", "AccessOverride", expired.id().toString(), override, expired, null);
                events.publish(AccessControlEventType.ACCESS_OVERRIDE_EXPIRED.eventType(),
                        AccessControlEventType.ACCESS_OVERRIDE_EXPIRED.version(), "AccessOverride",
                        expired.id().toString(), expired.siteCode(), systemActor(expired.siteCode()),
                        Map.of("overrideId", expired.id().toString()));
                log.info("Access override {} expired automatically at site {}", expired.id(), expired.siteCode());
            }
        }
    }

    private AccessOverride saveAndSync(AccessOverride override, ActorContext actor) {
        AccessOverride saved = repository.saveOverride(override);
        vendorGateway.syncOverride(saved, actor);
        audit.record(actor, SourceChannel.WEB.name(), saved.siteCode(), "ACCESS_OVERRIDE_ACTIVATED", "AccessOverride",
                saved.id().toString(), null, saved, null);
        events.publish(AccessControlEventType.ACCESS_OVERRIDE_ACTIVATED.eventType(),
                AccessControlEventType.ACCESS_OVERRIDE_ACTIVATED.version(), "AccessOverride", saved.id().toString(),
                saved.siteCode(), actor, Map.of("breakGlass", saved.breakGlass(), "scopeRef", saved.scopeRef()));
        return saved;
    }

    private AccessOverride requireOverride(UUID id) {
        return repository.findOverride(id).orElseThrow(() -> AccessControlException.notFound("AccessOverride", id));
    }

    private static ActorContext systemActor(String siteCode) {
        return new ActorContext(new gh.edu.clet.sfl.common.security.SiteScopedPrincipal(
                "system:access-override-expiry-sweep", "Access Override Expiry Sweep",
                java.util.Set.of(gh.edu.clet.sfl.common.security.SflRole.SFL_ADMIN), java.util.Set.of("*"), true),
                "override-expiry-sweep");
    }
}
