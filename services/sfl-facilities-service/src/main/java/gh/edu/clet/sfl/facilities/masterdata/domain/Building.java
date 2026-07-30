package gh.edu.clet.sfl.facilities.masterdata.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A building within a site (SRS-SFL-S152-01).
 *
 * <p>Carries {@code siteCode} alongside {@code siteId} — denormalised on purpose. Site scope is
 * checked on every read and write, and resolving the site row to learn its code on each of those
 * checks would turn one query into two for no gain. The pair is written together and never
 * separately, because a building cannot move between sites: that would be a demolition and a new
 * registration, not an edit.
 */
public record Building(
        UUID id,
        UUID siteId,
        String siteCode,
        String buildingCode,
        String name,
        String description,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public Building {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteId, "siteId is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(buildingCode, "buildingCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static Building create(UUID id, UUID siteId, String siteCode, String buildingCode, String name,
            String description, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new Building(id, siteId, Site.normalizeCode(siteCode), Site.normalizeCode(buildingCode),
                name.strip(), Site.blankToNull(description), RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** A null or blank field leaves the current value alone — this is a PATCH, not a replace. */
    public Building update(String name, String description, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new Building(id, siteId, siteCode, buildingCode,
                name == null || name.isBlank() ? this.name : name.strip(),
                description == null ? this.description : Site.blankToNull(description),
                lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public Building changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new Building(id, siteId, siteCode, buildingCode, name, description,
                lifecycleStatus.transitionTo(target, "Building"),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Creation time, preserved for the pre-S152 API shape. */
    public Instant createdAt() {
        return metadata.createdAt();
    }
}
