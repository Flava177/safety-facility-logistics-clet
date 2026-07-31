package gh.edu.clet.sfl.facilities.booking.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A thing that can be booked alongside a room, and can only be in one place at a time.
 *
 * <p>Deliberately distinct from S152 {@code FacilityAsset}. An asset is fixed plant whose condition
 * feeds a space readiness score; a resource is portable, and its scarcity is the point. The same
 * projector can be an asset while bolted to a ceiling and a resource once it is on a trolley, and
 * nothing here tries to reconcile the two registers — {@link #assetId} carries the link where one
 * exists, as a value rather than a foreign key.
 *
 * @param quantity how many of this resource exist. One row for a set of forty chairs, not forty rows.
 * @param homeRoomId where it normally lives. Advisory only: it can be booked anywhere on site.
 */
public record BookableResource(
        UUID id,
        String siteCode,
        String resourceCode,
        String name,
        ResourceCategory category,
        String description,
        int quantity,
        UUID homeRoomId,
        UUID assetId,
        boolean requiresSetup,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public BookableResource {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        resourceCode = EstateCodes.normalize(resourceCode);
        EstateCodes.require(name, "name");
        name = name.strip();
        description = EstateCodes.blankToNull(description);
        Objects.requireNonNull(category, "category is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least one");
        }
    }

    public static BookableResource register(UUID id, String siteCode, String resourceCode, String name,
            ResourceCategory category, String description, int quantity, UUID homeRoomId, UUID assetId,
            boolean requiresSetup, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new BookableResource(id, siteCode, resourceCode, name, category, description, quantity,
                homeRoomId, assetId, requiresSetup, RecordLifecycleStatus.ACTIVE,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public BookableResource update(String newName, String newDescription, Integer newQuantity,
            UUID newHomeRoomId, Boolean newRequiresSetup, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new BookableResource(id, siteCode, resourceCode,
                newName == null || newName.isBlank() ? name : newName,
                category,
                newDescription == null ? description : newDescription,
                newQuantity == null ? quantity : newQuantity,
                newHomeRoomId == null ? homeRoomId : newHomeRoomId,
                assetId,
                newRequiresSetup == null ? requiresSetup : newRequiresSetup,
                lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public BookableResource changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        RecordLifecycleStatus next = lifecycleStatus.transitionTo(target, "Bookable resource");
        return new BookableResource(id, siteCode, resourceCode, name, category, description, quantity,
                homeRoomId, assetId, requiresSetup, next,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code true} when this resource can be allocated to a booking today. */
    public boolean isAllocatable() {
        return lifecycleStatus.isOperational();
    }

    /**
     * {@code true} when there is only one of this thing, so a second booking of it is impossible.
     *
     * <p>Derived rather than declared, because a separate {@code exclusive} flag beside a quantity of
     * one is two facts that can disagree, and the disagreement would be resolved differently by the
     * domain and by the database's exclusion constraint.
     *
     * <p>This is the boundary between what the database guarantees and what the application checks —
     * see {@link ResourceAllocation} for why the two are enforced in different places.
     */
    public boolean isExclusive() {
        return quantity == 1;
    }
}
