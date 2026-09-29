package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AvampAssetProjection;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link AvampAssetProjection}. Column names match V16. */
@Entity
@Table(name = "bms_avamp_asset_projection", schema = "facilities")
public class AvampAssetProjectionRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "avamp_asset_id", nullable = false, length = 120)
    private String avampAssetId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "asset_code", length = 120)
    private String assetCode;
    @Column(length = 200)
    private String name;
    @Column(length = 60)
    private String category;
    @Column(name = "asset_status", length = 40)
    private String assetStatus;
    @Column(name = "location_type", length = 40)
    private String locationType;
    @Column(name = "location_reference", length = 200)
    private String locationReference;
    @Column(name = "last_event_type", length = 120)
    private String lastEventType;
    @Column(name = "avamp_updated_at")
    private Instant avampUpdatedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected AvampAssetProjectionRecord() {
    }

    static AvampAssetProjectionRecord empty() {
        return new AvampAssetProjectionRecord();
    }

    void apply(AvampAssetProjection asset) {
        id = asset.id();
        avampAssetId = asset.avampAssetId();
        siteCode = asset.siteCode();
        assetCode = asset.assetCode();
        name = asset.name();
        category = asset.category();
        assetStatus = asset.assetStatus();
        locationType = asset.locationType();
        locationReference = asset.locationReference();
        lastEventType = asset.lastEventType();
        avampUpdatedAt = asset.avampUpdatedAt();
        metadata = RecordMetadataEmbeddable.from(asset.metadata());
    }

    AvampAssetProjection toDomain() {
        return new AvampAssetProjection(id, avampAssetId, siteCode, assetCode, name, category, assetStatus,
                locationType, locationReference, lastEventType, avampUpdatedAt, metadata.toDomain(recordVersion()));
    }
}
