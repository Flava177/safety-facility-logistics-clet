package gh.edu.clet.sfl.facilities.masterdata.application;

import java.util.UUID;

import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;

public record UpdateRoomReadinessCommand(
        UUID roomId,
        LocationReadinessStatus status,
        String notes,
        String actor,
        String correlationId) {
}
