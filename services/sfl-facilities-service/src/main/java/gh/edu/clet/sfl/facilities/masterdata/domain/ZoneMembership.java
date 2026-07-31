package gh.edu.clet.sfl.facilities.masterdata.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One estate record's membership of a zone. */
public record ZoneMembership(
        UUID id,
        UUID zoneId,
        ZoneMemberType memberType,
        UUID memberId,
        String siteCode,
        String addedBy,
        Instant addedAt) {

    public ZoneMembership {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(zoneId, "zoneId is required");
        Objects.requireNonNull(memberType, "memberType is required");
        Objects.requireNonNull(memberId, "memberId is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(addedBy, "addedBy");
        Objects.requireNonNull(addedAt, "addedAt is required");
    }

    public static ZoneMembership of(UUID zoneId, ZoneMemberType memberType, UUID memberId, String siteCode,
            String actorId, Instant at) {
        return new ZoneMembership(UUID.randomUUID(), zoneId, memberType, memberId, Site.normalizeCode(siteCode),
                actorId, at);
    }
}
