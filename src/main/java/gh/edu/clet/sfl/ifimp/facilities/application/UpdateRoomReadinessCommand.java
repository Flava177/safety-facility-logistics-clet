package gh.edu.clet.sfl.ifimp.facilities.application;

import java.util.UUID;

import gh.edu.clet.sfl.ifimp.facilities.domain.LocationReadinessStatus;

public record UpdateRoomReadinessCommand(
        UUID roomId,
        LocationReadinessStatus status,
        String notes,
        String actor,
        String correlationId) {
}
