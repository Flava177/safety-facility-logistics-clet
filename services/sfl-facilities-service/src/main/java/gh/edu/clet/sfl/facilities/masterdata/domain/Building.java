package gh.edu.clet.sfl.facilities.masterdata.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Building(
        UUID id,
        UUID siteId,
        String siteCode,
        String buildingCode,
        String name,
        String description,
        Instant createdAt) {

    public Building {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteId, "siteId is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(buildingCode, "buildingCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static Building create(UUID id, UUID siteId, String siteCode, String buildingCode,
            String name, String description, Instant createdAt) {
        return new Building(id, siteId, Site.normalizeCode(siteCode), Site.normalizeCode(buildingCode),
                name.strip(), Site.blankToNull(description), createdAt);
    }
}
