package gh.edu.clet.sfl.facilities.masterdata.application;

import java.util.UUID;

public record CreateRoomCommand(
        UUID floorId,
        String siteCode,
        String roomCode,
        String name,
        String roomType,
        Integer capacity,
        String actor,
        String correlationId) {
}
