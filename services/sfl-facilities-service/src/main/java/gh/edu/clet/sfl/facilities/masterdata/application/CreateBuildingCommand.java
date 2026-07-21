package gh.edu.clet.sfl.facilities.masterdata.application;

import java.util.UUID;

public record CreateBuildingCommand(
        UUID siteId,
        String siteCode,
        String buildingCode,
        String name,
        String description,
        String actor,
        String correlationId) {
}
