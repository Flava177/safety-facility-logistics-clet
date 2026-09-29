package gh.edu.clet.sfl.facilities.buildingsystems.domain;

import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * S156's local copy of an AVAMP asset, fed by FTLMP's {@code sfl.avamp.asset-*} events - SRS-SFL-S156-04.
 *
 * <p>AVAMP-Lite lives in another deployable, and a cross-service call on every device registration would
 * make S156 unusable whenever FTLMP is down. So the facts S156 needs - does the asset exist, where does
 * AVAMP think it is, what state is it in - are projected here from events and read locally. The cost is
 * eventual consistency: an asset registered in AVAMP a second ago may not be registrable here yet.
 *
 * <p>{@code avampUpdatedAt} is AVAMP's own timestamp and is what orders updates. Events can arrive out of
 * order after a redelivery; an older location must not overwrite a newer one.
 *
 * <p>There is no retirement or firmware field because AVAMP-Lite publishes none - see {@link BmsDevice}.
 */
public record AvampAssetProjection(
        UUID id,
        String avampAssetId,
        String siteCode,
        String assetCode,
        String name,
        String category,
        String assetStatus,
        String locationType,
        String locationReference,
        String lastEventType,
        Instant avampUpdatedAt,
        RecordMetadata metadata) {

    public AvampAssetProjection {
        Objects.requireNonNull(id, "id is required");
        EstateCodes.require(avampAssetId, "avampAssetId");
        avampAssetId = avampAssetId.strip();
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    /** {@code true} when AVAMP says the asset is in service. Unknown is treated as not. */
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(assetStatus);
    }

    /** Whether an event stamped {@code eventUpdatedAt} is newer than what is held. */
    public boolean isSupersededBy(Instant eventUpdatedAt) {
        return avampUpdatedAt == null || eventUpdatedAt == null || !eventUpdatedAt.isBefore(avampUpdatedAt);
    }
}
