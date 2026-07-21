package gh.edu.clet.sfl.ifimp.facilities.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FacilityFloor(
        UUID id,
        UUID buildingId,
        String siteCode,
        String floorCode,
        String name,
        Integer levelNumber,
        Instant createdAt) {

    public FacilityFloor {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(buildingId, "buildingId is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(floorCode, "floorCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static FacilityFloor create(UUID id, UUID buildingId, String siteCode, String floorCode,
            String name, Integer levelNumber, Instant createdAt) {
        return new FacilityFloor(id, buildingId, Site.normalizeCode(siteCode), Site.normalizeCode(floorCode),
                name.strip(), levelNumber, createdAt);
    }
}
