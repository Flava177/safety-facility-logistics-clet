package gh.edu.clet.sfl.facilities.masterdata.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A floor within a building (SRS-SFL-S152-01).
 *
 * <p>{@code levelNumber} is nullable and signed. Signed because basements and lower-ground floors are
 * real and sort below zero; nullable because mezzanines, roof plant decks and annexes do not always
 * have one, and inventing a number for them would make the ordering wrong rather than absent.
 */
public record FacilityFloor(
        UUID id,
        UUID buildingId,
        String siteCode,
        String floorCode,
        String name,
        Integer levelNumber,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public FacilityFloor {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(buildingId, "buildingId is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(floorCode, "floorCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static FacilityFloor create(UUID id, UUID buildingId, String siteCode, String floorCode, String name,
            Integer levelNumber, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new FacilityFloor(id, buildingId, Site.normalizeCode(siteCode), Site.normalizeCode(floorCode),
                name.strip(), levelNumber, RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** A null or blank field leaves the current value alone — this is a PATCH, not a replace. */
    public FacilityFloor update(String name, Integer levelNumber, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new FacilityFloor(id, buildingId, siteCode, floorCode,
                name == null || name.isBlank() ? this.name : name.strip(),
                levelNumber == null ? this.levelNumber : levelNumber,
                lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public FacilityFloor changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new FacilityFloor(id, buildingId, siteCode, floorCode, name, levelNumber,
                lifecycleStatus.transitionTo(target, "Floor"),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Creation time, preserved for the pre-S152 API shape. */
    public Instant createdAt() {
        return metadata.createdAt();
    }
}
