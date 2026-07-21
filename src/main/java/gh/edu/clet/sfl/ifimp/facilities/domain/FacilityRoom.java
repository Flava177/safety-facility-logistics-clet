package gh.edu.clet.sfl.ifimp.facilities.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FacilityRoom(
        UUID id,
        UUID floorId,
        String siteCode,
        String roomCode,
        String name,
        String roomType,
        Integer capacity,
        LocationReadinessStatus readinessStatus,
        String readinessNotes,
        Instant readinessUpdatedAt,
        Instant createdAt) {

    public FacilityRoom {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(floorId, "floorId is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(roomCode, "roomCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(readinessStatus, "readinessStatus is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (capacity != null && capacity < 0) {
            throw new IllegalArgumentException("capacity cannot be negative");
        }
    }

    public static FacilityRoom create(UUID id, UUID floorId, String siteCode, String roomCode,
            String name, String roomType, Integer capacity, Instant createdAt) {
        return new FacilityRoom(id, floorId, Site.normalizeCode(siteCode), Site.normalizeCode(roomCode),
                name.strip(), Site.blankToNull(roomType), capacity, LocationReadinessStatus.UNKNOWN,
                null, null, createdAt);
    }

    public FacilityRoom updateReadiness(LocationReadinessStatus status, String notes, Instant updatedAt) {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        return new FacilityRoom(id, floorId, siteCode, roomCode, name, roomType, capacity,
                status, Site.blankToNull(notes), updatedAt, createdAt);
    }
}
