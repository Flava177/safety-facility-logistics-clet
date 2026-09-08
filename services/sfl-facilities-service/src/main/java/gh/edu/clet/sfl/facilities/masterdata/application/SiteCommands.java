package gh.edu.clet.sfl.facilities.masterdata.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The site commands - create, update, lifecycle, operating mode - split out of
 * {@link FacilitiesMasterDataService} for the same reason the rest of that class's Javadoc gives.
 * Holds a reference to that class to reuse {@code requireSite}, {@code replay}, {@code remember},
 * {@code publish}, {@code now} and {@code normalize}.
 */
final class SiteCommands {

    private final FacilitiesMasterDataService service;
    private final FacilitiesRepository facilities;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;

    SiteCommands(FacilitiesMasterDataService service, FacilitiesRepository facilities,
            FacilitiesAuthorization authorization, AuditPort audit) {
        this.service = service;
        this.facilities = facilities;
        this.authorization = authorization;
        this.audit = audit;
    }

    Site create(FacilitiesCommands.CreateSite command) {
        ActorContext actor = command.actor();
        String siteCode = FacilitiesMasterDataService.normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_SITE_MANAGE, siteCode, command.channel(),
                "Site", siteCode);

        Optional<Site> replayed = service.replay("create-site", command.idempotencyKey(),
                command.idempotencyPayload(), facilities::findSite);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        facilities.findSiteByCode(siteCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("site", siteCode, siteCode);
            }
        });

        Site saved = facilities.saveSite(Site.create(UUID.randomUUID(), siteCode, command.name(),
                command.description(), actor.actorId(), service.now(), command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.SITE_CREATED, "Site", saved.id().toString(),
                saved.siteCode(), null, saved);
        service.publish("sfl.ifimp.site-created.v1", "Site", saved.id(), saved.siteCode(), actor, saved);
        service.remember("create-site", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    Site update(FacilitiesCommands.UpdateSite command) {
        ActorContext actor = command.actor();
        Site site = service.requireSite(command.siteId());
        authorization.require(actor, SflPermission.FACILITIES_SITE_MANAGE, site.siteCode(), command.channel(),
                "Site", site.id().toString());
        site.metadata().requireVersion(command.expectedVersion(), "Site", site.id());

        Site saved = facilities.saveSite(site.update(command.name(), command.description(), actor.actorId(),
                service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.SITE_UPDATED, "Site", saved.id().toString(),
                saved.siteCode(), site, saved);
        service.publish("sfl.ifimp.site-updated.v1", "Site", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    Site changeLifecycle(FacilitiesCommands.ChangeSiteLifecycle command) {
        ActorContext actor = command.actor();
        Site site = service.requireSite(command.siteId());
        authorization.require(actor, SflPermission.FACILITIES_SITE_MANAGE, site.siteCode(), command.channel(),
                "Site", site.id().toString());
        site.metadata().requireVersion(command.expectedVersion(), "Site", site.id());

        Site saved = facilities.saveSite(site.changeLifecycle(command.status(), actor.actorId(), service.now(),
                command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.SITE_LIFECYCLE_CHANGED, "Site",
                saved.id().toString(), saved.siteCode(), site.lifecycleStatus(), saved.lifecycleStatus());
        service.publish("sfl.ifimp.site-lifecycle-changed.v1", "Site", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    /**
     * Declares or stands down examination mode (NFR 23.3).
     *
     * <p>Its own permission, its own audit action and its own event, because the mode change is the
     * decision - every stricter rule that follows is a consequence of it, and burying it inside a
     * general site update would make the one thing a reviewer looks for invisible.
     */
    Site changeOperatingMode(FacilitiesCommands.ChangeOperatingMode command) {
        ActorContext actor = command.actor();
        Site site = service.requireSite(command.siteId());
        authorization.require(actor, SflPermission.FACILITIES_OPERATING_MODE_CHANGE, site.siteCode(),
                command.channel(), "Site", site.id().toString());

        OperatingMode previous = site.operatingMode();
        Site saved = facilities.saveSite(site.changeOperatingMode(command.operatingMode(), actor.actorId(),
                service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.SITE_OPERATING_MODE_CHANGED, "Site",
                saved.id().toString(), saved.siteCode(),
                new ModeChange(previous, null), new ModeChange(saved.operatingMode(), command.reason()));
        service.publish("sfl.ifimp.site-operating-mode-changed.v1", "Site", saved.id(), saved.siteCode(), actor,
                new ModeChangeEvent(saved.siteCode(), previous, saved.operatingMode(), command.reason(),
                        actor.actorId(), saved.operatingModeChangedAt()));
        return saved;
    }

    private record ModeChange(OperatingMode operatingMode, String reason) {
    }

    private record ModeChangeEvent(String siteCode, OperatingMode from, OperatingMode to, String reason,
            String changedBy, Instant changedAt) {
    }
}
