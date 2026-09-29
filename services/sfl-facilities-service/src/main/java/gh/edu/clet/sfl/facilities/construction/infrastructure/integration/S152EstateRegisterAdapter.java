package gh.edu.clet.sfl.facilities.construction.infrastructure.integration;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.application.ports.EstateRegisterPort;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesCommands;
import gh.edu.clet.sfl.facilities.masterdata.application.FacilitiesMasterDataService;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityFloor;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * S176's handover writing into the S152 register - the one class in S176 that names S152.
 *
 * <p>Writes go through {@link FacilitiesMasterDataService} as the calling actor: S152 authorises the
 * change ({@code FACILITIES_SPACE_MANAGE}), enforces its own uniqueness rule, audits
 * {@code ROOM_CREATED}/{@code ROOM_UPDATED} and publishes its own room event, exactly as for a manual
 * edit. Reads use the repository directly, because validating a handover is not an S152 read the
 * caller needs a permission for.
 */
@Component
public class S152EstateRegisterAdapter implements EstateRegisterPort {

    private final FacilitiesMasterDataService estate;
    private final FacilitiesRepository facilities;

    public S152EstateRegisterAdapter(FacilitiesMasterDataService estate, FacilitiesRepository facilities) {
        this.estate = estate;
        this.facilities = facilities;
    }

    @Override
    public Optional<RoomView> findRoom(UUID roomId) {
        return facilities.findRoom(roomId).map(S152EstateRegisterAdapter::view);
    }

    @Override
    public Optional<String> siteOfFloor(UUID floorId) {
        return facilities.findFloor(floorId).map(FacilityFloor::siteCode);
    }

    @Override
    public boolean siteExists(String siteCode) {
        return facilities.findSiteByCode(siteCode).isPresent();
    }

    @Override
    public RoomView createRoom(UUID floorId, String roomCode, String name, SpaceType spaceType, Integer capacity,
            BigDecimal areaSqm, String costCentre, Boolean bookable, Boolean examinationCapable, ActorContext actor,
            SourceChannel channel, String idempotencyKey) {
        return view(estate.createRoom(new FacilitiesCommands.CreateRoom(floorId, roomCode, name, spaceType, capacity,
                areaSqm, costCentre, bookable, examinationCapable, actor, channel, idempotencyKey)));
    }

    @Override
    public RoomView updateRoom(UUID roomId, String name, SpaceType spaceType, Integer capacity, BigDecimal areaSqm,
            String costCentre, Boolean bookable, Boolean examinationCapable, ActorContext actor,
            SourceChannel channel) {
        return view(estate.updateRoom(new FacilitiesCommands.UpdateRoom(roomId, name, spaceType, capacity, areaSqm,
                costCentre, bookable, examinationCapable, null, actor, channel)));
    }

    private static RoomView view(FacilityRoom room) {
        return new RoomView(room.id(), room.siteCode(), room.roomCode(), room.name(), room.metadata().version());
    }
}
