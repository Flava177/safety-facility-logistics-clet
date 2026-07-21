package gh.edu.clet.sfl.facilities.masterdata.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Zone(
        UUID id,
        String siteCode,
        String zoneCode,
        String name,
        String purpose,
        Instant createdAt) {

    public Zone {
        Objects.requireNonNull(id, "id is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(zoneCode, "zoneCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static Zone create(UUID id, String siteCode, String zoneCode, String name, String purpose, Instant createdAt) {
        return new Zone(id, Site.normalizeCode(siteCode), Site.normalizeCode(zoneCode), name.strip(),
                Site.blankToNull(purpose), createdAt);
    }
}
