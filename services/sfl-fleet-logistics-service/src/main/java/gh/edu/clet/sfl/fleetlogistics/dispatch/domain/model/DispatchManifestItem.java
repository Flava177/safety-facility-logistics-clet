package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A courier item placed on a dispatch manifest, with its expected seal identifier, quantity and return status. */
public record DispatchManifestItem(UUID id, UUID dispatchId, UUID courierItemId, SiteCode siteCode, int sequenceNo,
        String expectedSealId, int expectedQuantity, ReturnStatus returnStatus, Instant returnedAt,
        SealState returnSealState, Instant createdAt) {

    public enum ReturnStatus { PENDING, RETURNED, OUTSTANDING }

    public DispatchManifestItem {
        Objects.requireNonNull(id); Objects.requireNonNull(dispatchId); Objects.requireNonNull(courierItemId);
        Objects.requireNonNull(siteCode); Objects.requireNonNull(createdAt);
        if (expectedQuantity <= 0) throw new IllegalArgumentException("expectedQuantity must be positive");
        if (sequenceNo < 0) throw new IllegalArgumentException("sequenceNo cannot be negative");
    }

    /** Mark a returnable item as awaiting its return leg once the dispatch leaves the warehouse. */
    public DispatchManifestItem markPending() {
        return new DispatchManifestItem(id, dispatchId, courierItemId, siteCode, sequenceNo, expectedSealId,
                expectedQuantity, ReturnStatus.PENDING, returnedAt, returnSealState, createdAt);
    }

    public DispatchManifestItem markReturned(Instant at, SealState sealState) {
        return new DispatchManifestItem(id, dispatchId, courierItemId, siteCode, sequenceNo, expectedSealId,
                expectedQuantity, ReturnStatus.RETURNED, Objects.requireNonNull(at, "returnedAt"),
                Objects.requireNonNull(sealState, "returnSealState"), createdAt);
    }

    public DispatchManifestItem markOutstanding() {
        return new DispatchManifestItem(id, dispatchId, courierItemId, siteCode, sequenceNo, expectedSealId,
                expectedQuantity, ReturnStatus.OUTSTANDING, returnedAt, returnSealState, createdAt);
    }
}
