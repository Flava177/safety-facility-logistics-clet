package gh.edu.clet.sfl.facilities.masterdata.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMembership;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.Optional;
import java.util.UUID;

/**
 * The zone commands - split out of {@link FacilitiesMasterDataService} for the same reason the rest
 * of that class's Javadoc gives. Holds a reference to that class to reuse {@code requireZone}, {@code
 * requireRoom}, {@code replay}, {@code remember}, {@code publish}, {@code now} and {@code normalize}.
 */
final class ZoneCommands {

    private final FacilitiesMasterDataService service;
    private final FacilitiesRepository facilities;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;

    ZoneCommands(FacilitiesMasterDataService service, FacilitiesRepository facilities,
            FacilitiesAuthorization authorization, AuditPort audit) {
        this.service = service;
        this.facilities = facilities;
        this.authorization = authorization;
        this.audit = audit;
    }

    Zone create(FacilitiesCommands.CreateZone command) {
        ActorContext actor = command.actor();
        String siteCode = FacilitiesMasterDataService.normalize(command.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_ZONE_MANAGE, siteCode, command.channel(),
                "Zone", FacilitiesMasterDataService.normalize(command.zoneCode()));

        Optional<Zone> replayed = service.replay("create-zone", command.idempotencyKey(),
                command.idempotencyPayload(), facilities::findZone);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String zoneCode = FacilitiesMasterDataService.normalize(command.zoneCode());
        facilities.findZoneByCode(siteCode, zoneCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("zone", zoneCode, siteCode);
            }
        });
        if (command.parentZoneId() != null) {
            Zone parent = facilities.findZone(command.parentZoneId())
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Zone",
                            command.parentZoneId()));
            if (!parent.siteCode().equals(siteCode)) {
                throw new FacilitiesException.ValidationFailedException(
                        "A zone's parent must belong to the same site.");
            }
        }

        Zone saved = facilities.saveZone(Zone.create(UUID.randomUUID(), siteCode, zoneCode, command.name(),
                command.purpose(), command.parentZoneId(), actor.actorId(), service.now(), command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ZONE_CREATED, "Zone", saved.id().toString(),
                saved.siteCode(), null, saved);
        service.publish("sfl.ifimp.zone-created.v1", "Zone", saved.id(), saved.siteCode(), actor, saved);
        service.remember("create-zone", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    /**
     * Adds a record to a zone.
     *
     * <p>The member must belong to the zone's own site. Without that check a zone could reach across
     * sites, and an evacuation broadcast addressed to it would page a building three hundred kilometres
     * from the fire.
     */
    ZoneMembership addMember(FacilitiesCommands.AddZoneMember command) {
        ActorContext actor = command.actor();
        Zone zone = facilities.findZone(command.zoneId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Zone", command.zoneId()));
        authorization.require(actor, SflPermission.FACILITIES_ZONE_MANAGE, zone.siteCode(), command.channel(),
                "Zone", zone.id().toString());

        String memberSite = resolveMemberSite(command.memberType(), command.memberId());
        if (!zone.siteCode().equals(memberSite)) {
            throw new FacilitiesException.ValidationFailedException(
                    "A zone member must belong to the zone's site (" + zone.siteCode() + ").");
        }

        ZoneMembership saved = facilities.saveZoneMembership(ZoneMembership.of(zone.id(), command.memberType(),
                command.memberId(), zone.siteCode(), actor.actorId(), service.now()));

        audit.record(actor, command.channel(), AuditAction.ZONE_MEMBER_ADDED, "Zone", zone.id().toString(),
                zone.siteCode(), null, saved);
        service.publish("sfl.ifimp.zone-member-added.v1", "Zone", zone.id(), zone.siteCode(), actor, saved);
        return saved;
    }

    void removeMember(FacilitiesCommands.RemoveZoneMember command) {
        ActorContext actor = command.actor();
        Zone zone = facilities.findZone(command.zoneId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Zone", command.zoneId()));
        authorization.require(actor, SflPermission.FACILITIES_ZONE_MANAGE, zone.siteCode(), command.channel(),
                "Zone", zone.id().toString());

        facilities.deleteZoneMembership(zone.id(), command.memberType(), command.memberId());

        ZoneMemberRef removed = new ZoneMemberRef(command.memberType(), command.memberId());
        audit.record(actor, command.channel(), AuditAction.ZONE_MEMBER_REMOVED, "Zone", zone.id().toString(),
                zone.siteCode(), removed, null);
        service.publish("sfl.ifimp.zone-member-removed.v1", "Zone", zone.id(), zone.siteCode(), actor, removed);
    }

    Zone changeLifecycle(FacilitiesCommands.ChangeZoneLifecycle command) {
        ActorContext actor = command.actor();
        Zone zone = service.requireZone(command.zoneId());
        authorization.require(actor, SflPermission.FACILITIES_ZONE_MANAGE, zone.siteCode(),
                command.channel(), "Zone", zone.id().toString());
        zone.metadata().requireVersion(command.expectedVersion(), "Zone", zone.id());

        Zone saved = facilities.saveZone(zone.changeLifecycle(command.status(), actor.actorId(), service.now(),
                command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ZONE_LIFECYCLE_CHANGED, "Zone",
                saved.id().toString(), saved.siteCode(), zone.lifecycleStatus(), saved.lifecycleStatus());
        service.publish("sfl.ifimp.zone-lifecycle-changed.v1", "Zone", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    private record ZoneMemberRef(ZoneMemberType memberType, UUID memberId) {
    }

    /** The site a zone member belongs to, which is what constrains it to the zone's own site. */
    private String resolveMemberSite(ZoneMemberType memberType, UUID memberId) {
        return switch (memberType) {
            case BUILDING -> facilities.findBuilding(memberId)
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Building",
                            memberId))
                    .siteCode();
            case FLOOR -> facilities.findFloor(memberId)
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Floor", memberId))
                    .siteCode();
            case ROOM -> service.requireRoom(memberId).siteCode();
            case DEVICE -> facilities.findDeviceReference(memberId)
                    .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException(
                            "Device reference", memberId))
                    .siteCode();
        };
    }
}
