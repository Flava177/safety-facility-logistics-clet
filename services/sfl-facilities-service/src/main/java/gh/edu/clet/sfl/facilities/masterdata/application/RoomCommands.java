package gh.edu.clet.sfl.facilities.masterdata.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.Optional;
import java.util.UUID;

/**
 * The space (room) commands - split out of {@link FacilitiesMasterDataService} for the same reason
 * the rest of that class's Javadoc gives. Holds a reference to that class to reuse {@code
 * requireRoom}, {@code replay}, {@code remember}, {@code publish}, {@code now} and {@code normalize}.
 */
final class RoomCommands {

    private final FacilitiesMasterDataService service;
    private final FacilitiesRepository facilities;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;

    RoomCommands(FacilitiesMasterDataService service, FacilitiesRepository facilities,
            FacilitiesAuthorization authorization, AuditPort audit) {
        this.service = service;
        this.facilities = facilities;
        this.authorization = authorization;
        this.audit = audit;
    }

    FacilityRoom create(FacilitiesCommands.CreateRoom command) {
        ActorContext actor = command.actor();
        FacilityFloor floor = facilities.findFloor(command.floorId())
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Floor",
                        command.floorId()));
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, floor.siteCode(), command.channel(),
                "FacilityRoom", FacilitiesMasterDataService.normalize(command.roomCode()));

        Optional<FacilityRoom> replayed = service.replay("create-room", command.idempotencyKey(),
                command.idempotencyPayload(), facilities::findRoom);
        if (replayed.isPresent()) {
            return replayed.get();
        }

        String roomCode = FacilitiesMasterDataService.normalize(command.roomCode());
        facilities.findRoomByCode(floor.siteCode(), roomCode).ifPresent(existing -> {
            if (existing.lifecycleStatus().occupiesIdentifier()) {
                throw new FacilitiesException.DuplicateIdentifierException("space", roomCode, floor.siteCode());
            }
        });

        FacilityRoom saved = facilities.saveRoom(FacilityRoom.create(UUID.randomUUID(), floor.id(),
                floor.siteCode(), roomCode, command.name(), command.spaceType(), command.capacity(),
                command.areaSqm(), command.costCentre(), command.bookable(), command.examinationCapable(),
                actor.actorId(), service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ROOM_CREATED, "FacilityRoom",
                saved.id().toString(), saved.siteCode(), null, saved);
        service.publish("sfl.ifimp.room-created.v1", "FacilityRoom", saved.id(), saved.siteCode(), actor, saved);
        service.remember("create-room", command.idempotencyKey(), command.idempotencyPayload(), saved.id(),
                saved.siteCode(), actor);
        return saved;
    }

    FacilityRoom update(FacilitiesCommands.UpdateRoom command) {
        ActorContext actor = command.actor();
        FacilityRoom room = service.requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, room.siteCode(), command.channel(),
                "FacilityRoom", room.id().toString());
        room.metadata().requireVersion(command.expectedVersion(), "Space", room.id());

        FacilityRoom saved = facilities.saveRoom(room.update(command.name(), command.spaceType(),
                command.capacity(), command.areaSqm(), command.costCentre(), command.bookable(),
                command.examinationCapable(), actor.actorId(), service.now(), command.channel(),
                actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ROOM_UPDATED, "FacilityRoom", saved.id().toString(),
                saved.siteCode(), room, saved);
        service.publish("sfl.ifimp.room-updated.v1", "FacilityRoom", saved.id(), saved.siteCode(), actor, saved);
        return saved;
    }

    FacilityRoom changeLifecycle(FacilitiesCommands.ChangeRoomLifecycle command) {
        ActorContext actor = command.actor();
        FacilityRoom room = service.requireRoom(command.roomId());
        authorization.require(actor, SflPermission.FACILITIES_SPACE_MANAGE, room.siteCode(), command.channel(),
                "FacilityRoom", room.id().toString());
        room.metadata().requireVersion(command.expectedVersion(), "Space", room.id());

        FacilityRoom saved = facilities.saveRoom(room.changeLifecycle(command.status(), actor.actorId(),
                service.now(), command.channel(), actor.correlationId()));

        audit.record(actor, command.channel(), AuditAction.ROOM_LIFECYCLE_CHANGED, "FacilityRoom",
                saved.id().toString(), saved.siteCode(), room.lifecycleStatus(), saved.lifecycleStatus());
        service.publish("sfl.ifimp.room-lifecycle-changed.v1", "FacilityRoom", saved.id(), saved.siteCode(), actor,
                saved);
        return saved;
    }
}
