package gh.edu.clet.sfl.facilities.masterdata.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A named grouping of estate records within a site (SRS-SFL-S152-01).
 *
 * <p>Zones are how the safety and emergency systems address the estate: S162a life-safety events
 * arrive per zone, S174 broadcasts target recipient zones, S160a access policy is written per zone.
 * The pre-S152 model was a code and a name with nothing in it, which named a zone without being able
 * to say what it covered. {@link ZoneMembership} closes that.
 *
 * <p>{@code parentZoneId} supports nesting — a building's evacuation zone inside a campus-wide one —
 * without a separate hierarchy table. Membership resolution walks it; the walk is bounded by the
 * cycle check in the application service, because the domain cannot see its own siblings.
 */
public record Zone(
        UUID id,
        String siteCode,
        String zoneCode,
        String name,
        String purpose,
        UUID parentZoneId,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public Zone {
        Objects.requireNonNull(id, "id is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(zoneCode, "zoneCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (parentZoneId != null && parentZoneId.equals(id)) {
            throw new IllegalArgumentException("a zone cannot be its own parent");
        }
    }

    public static Zone create(UUID id, String siteCode, String zoneCode, String name, String purpose,
            UUID parentZoneId, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new Zone(id, Site.normalizeCode(siteCode), Site.normalizeCode(zoneCode), name.strip(),
                Site.blankToNull(purpose), parentZoneId, RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** A null or blank field leaves the current value alone — this is a PATCH, not a replace. */
    public Zone update(String name, String purpose, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new Zone(id, siteCode, zoneCode,
                name == null || name.isBlank() ? this.name : name.strip(),
                purpose == null ? this.purpose : Site.blankToNull(purpose),
                parentZoneId, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public Zone changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new Zone(id, siteCode, zoneCode, name, purpose, parentZoneId,
                lifecycleStatus.transitionTo(target, "Zone"),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Creation time, preserved for the pre-S152 API shape. */
    public Instant createdAt() {
        return metadata.createdAt();
    }
}
