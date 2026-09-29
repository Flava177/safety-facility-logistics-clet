package gh.edu.clet.sfl.facilities.construction.application.ports;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * The S152 facilities register, as handover writes into it - SRS-SFL-S176-04 "handover updates S152".
 *
 * <p>Changes are applied <em>as the handover actor</em>, through {@code FacilitiesMasterDataService},
 * so S152's own authorisation, duplicate-identifier rule, audit entry and room event all apply
 * exactly as they would to a manual edit. A facilities officer who may hand a project over but may
 * not manage spaces is refused by S152, not waved through by S176.
 */
public interface EstateRegisterPort {

    Optional<RoomView> findRoom(UUID roomId);

    Optional<String> siteOfFloor(UUID floorId);

    boolean siteExists(String siteCode);

    RoomView createRoom(UUID floorId, String roomCode, String name, SpaceType spaceType, Integer capacity,
            BigDecimal areaSqm, String costCentre, Boolean bookable, Boolean examinationCapable, ActorContext actor,
            SourceChannel channel, String idempotencyKey);

    RoomView updateRoom(UUID roomId, String name, SpaceType spaceType, Integer capacity, BigDecimal areaSqm,
            String costCentre, Boolean bookable, Boolean examinationCapable, ActorContext actor,
            SourceChannel channel);

    /** What S176 needs to know about a room - enough to validate a handover and record what changed. */
    record RoomView(UUID id, String siteCode, String roomCode, String name, long version) {
    }
}
