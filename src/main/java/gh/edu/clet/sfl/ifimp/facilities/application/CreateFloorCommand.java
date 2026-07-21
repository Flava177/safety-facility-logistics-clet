package gh.edu.clet.sfl.ifimp.facilities.application;

import java.util.UUID;

public record CreateFloorCommand(
        UUID buildingId,
        String siteCode,
        String floorCode,
        String name,
        Integer levelNumber,
        String actor,
        String correlationId) {
}
