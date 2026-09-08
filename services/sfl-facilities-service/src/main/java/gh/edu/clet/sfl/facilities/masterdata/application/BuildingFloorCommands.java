package gh.edu.clet.sfl.facilities.masterdata.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Building;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.Optional;
import java.util.UUID;

/**
 * The building and floor commands - split out of {@link FacilitiesMasterDataService} for the same
 * reason the rest of that class's Javadoc gives. Holds a reference to that class to reuse {@code
 * requireBuilding}, {@code requireFloor}, {@code replay}, {@code remember}, {@code publish}, {@code
 * now} and {@code normalize}.
 */
final class BuildingFloorCommands {

    private final FacilitiesMasterDataService service;
    private final FacilitiesRepository facilities;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;

    BuildingFloorCommands(FacilitiesMasterDataService service, FacilitiesRepository facilities,
            FacilitiesAuthorization authorization, AuditPort audit) {
        this.service = service;
        this.facilities = facilities;
        this.authorization = authorization;
        this.audit = audit;
    }

    Building createBuilding(FacilitiesCommands.CreateBuilding command) {
        ActorContext actor = command.actor();
        Site site = facilities.findSite(command.siteId())
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Site",
                        command.siteId()));
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, site.siteCode(), command.channel(),
                "Building", FacilitiesMasterDataService.normalize(command.buildingCode()));

        Optional<Building> replayed = service.replay("create-building", command.idempotencyKey(),
                command.idempotencyPayload(), facilities::findBuilding);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String buildingCode = FacilitiesMasterDataService.normalize(command.buildingCode());
        facilities.findBuildingByCode(site.siteCode(), buildingCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("building", buildingCode,
                        site.siteCode());
            }
        });

        Building saved = facilities.saveBuilding(Building.create(UUID.randomUUID(), site.id(), site.siteCode(),
                buildingCode, command.name(), command.description(), actor.actorId(), service.now(),
                command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.BUILDING_CREATED, "Building",
                saved.id().toString(), saved.siteCode(), null, saved);
        service.publish("sfl.ifimp.building-created.v1", "Building", saved.id(), saved.siteCode(), actor, saved);
        service.remember("create-building", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    FacilityFloor createFloor(FacilitiesCommands.CreateFloor command) {
        ActorContext actor = command.actor();
        Building building = facilities.findBuilding(command.buildingId())
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Building",
                        command.buildingId()));
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, building.siteCode(),
                command.channel(), "FacilityFloor", FacilitiesMasterDataService.normalize(command.floorCode()));

        Optional<FacilityFloor> replayed = service.replay("create-floor", command.idempotencyKey(),
                command.idempotencyPayload(), facilities::findFloor);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String floorCode = FacilitiesMasterDataService.normalize(command.floorCode());
        facilities.findFloorByCode(building.id(), floorCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("floor", floorCode,
                        building.siteCode());
            }
        });

        FacilityFloor saved = facilities.saveFloor(FacilityFloor.create(UUID.randomUUID(), building.id(),
                building.siteCode(), floorCode, command.name(), command.levelNumber(), actor.actorId(),
                service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.FLOOR_CREATED, "FacilityFloor",
                saved.id().toString(), saved.siteCode(), null, saved);
        service.publish("sfl.ifimp.floor-created.v1", "FacilityFloor", saved.id(), saved.siteCode(), actor, saved);
        service.remember("create-floor", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    Building updateBuilding(FacilitiesCommands.UpdateBuilding command) {
        ActorContext actor = command.actor();
        Building building = service.requireBuilding(command.buildingId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, building.siteCode(),
                command.channel(), "Building", building.id().toString());
        building.metadata().requireVersion(command.expectedVersion(), "Building", building.id());

        Building saved = facilities.saveBuilding(building.update(command.name(), command.description(),
                actor.actorId(), service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.BUILDING_UPDATED, "Building",
                saved.id().toString(), saved.siteCode(), building, saved);
        service.publish("sfl.ifimp.building-updated.v1", "Building", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    Building changeBuildingLifecycle(FacilitiesCommands.ChangeBuildingLifecycle command) {
        ActorContext actor = command.actor();
        Building building = service.requireBuilding(command.buildingId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, building.siteCode(),
                command.channel(), "Building", building.id().toString());
        building.metadata().requireVersion(command.expectedVersion(), "Building", building.id());

        Building saved = facilities.saveBuilding(building.changeLifecycle(command.status(), actor.actorId(),
                service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.BUILDING_LIFECYCLE_CHANGED, "Building",
                saved.id().toString(), saved.siteCode(), building.lifecycleStatus(), saved.lifecycleStatus());
        service.publish("sfl.ifimp.building-lifecycle-changed.v1", "Building", saved.id(), saved.siteCode(), actor,
                saved);
        return saved;
    }

    FacilityFloor updateFloor(FacilitiesCommands.UpdateFloor command) {
        ActorContext actor = command.actor();
        FacilityFloor floor = service.requireFloor(command.floorId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, floor.siteCode(),
                command.channel(), "Floor", floor.id().toString());
        floor.metadata().requireVersion(command.expectedVersion(), "Floor", floor.id());

        FacilityFloor saved = facilities.saveFloor(floor.update(command.name(), command.levelNumber(),
                actor.actorId(), service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.FLOOR_UPDATED, "Floor", saved.id().toString(),
                saved.siteCode(), floor, saved);
        service.publish("sfl.ifimp.floor-updated.v1", "Floor", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    FacilityFloor changeFloorLifecycle(FacilitiesCommands.ChangeFloorLifecycle command) {
        ActorContext actor = command.actor();
        FacilityFloor floor = service.requireFloor(command.floorId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, floor.siteCode(),
                command.channel(), "Floor", floor.id().toString());
        floor.metadata().requireVersion(command.expectedVersion(), "Floor", floor.id());

        FacilityFloor saved = facilities.saveFloor(floor.changeLifecycle(command.status(), actor.actorId(),
                service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.FLOOR_LIFECYCLE_CHANGED, "Floor",
                saved.id().toString(), saved.siteCode(), floor.lifecycleStatus(), saved.lifecycleStatus());
        service.publish("sfl.ifimp.floor-lifecycle-changed.v1", "Floor", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }
}
