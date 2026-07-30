package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCategory;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetCriticality;
import gh.edu.clet.sfl.facilities.masterdata.domain.AssetOperationalStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityAsset;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "facility_assets", schema = "facilities")
public class FacilityAssetRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "asset_code", nullable = false, length = 80)
    private String assetCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AssetCategory category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetCriticality criticality;
    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 30)
    private AssetOperationalStatus operationalStatus;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "location_code", length = 120)
    private String locationCode;
    @Column(length = 160)
    private String manufacturer;
    @Column(name = "model_number", length = 120)
    private String modelNumber;
    @Column(name = "serial_number", length = 120)
    private String serialNumber;
    @Column(name = "installed_on")
    private LocalDate installedOn;
    @Column(name = "warranty_expires_on")
    private LocalDate warrantyExpiresOn;
    @Column(name = "service_interval_days")
    private Integer serviceIntervalDays;
    @Column(name = "last_serviced_on")
    private LocalDate lastServicedOn;
    @Column(length = 160)
    private String custodian;
    @Column(name = "device_reference_id")
    private UUID deviceReferenceId;
    /** AVAMP-Lite's identifier for the same physical thing. A value, never a foreign key. */
    @Column(name = "asset_reference_id")
    private UUID assetReferenceId;
    @Column(name = "status_notes", length = 1000)
    private String statusNotes;
    @Column(name = "status_changed_at")
    private Instant statusChangedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected FacilityAssetRecord() {
    }

    private FacilityAssetRecord(FacilityAsset asset) {
        id = asset.id();
        siteCode = asset.siteCode();
        assetCode = asset.assetCode();
        name = asset.name();
        category = asset.category();
        criticality = asset.criticality();
        operationalStatus = asset.operationalStatus();
        roomId = asset.roomId();
        locationCode = asset.locationCode();
        manufacturer = asset.manufacturer();
        modelNumber = asset.modelNumber();
        serialNumber = asset.serialNumber();
        installedOn = asset.installedOn();
        warrantyExpiresOn = asset.warrantyExpiresOn();
        serviceIntervalDays = asset.serviceIntervalDays();
        lastServicedOn = asset.lastServicedOn();
        custodian = asset.custodian();
        deviceReferenceId = asset.deviceReferenceId();
        assetReferenceId = asset.assetReferenceId();
        statusNotes = asset.statusNotes();
        statusChangedAt = asset.statusChangedAt();
        lifecycleStatus = asset.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(asset.metadata());
    }

    public static FacilityAssetRecord from(FacilityAsset asset) {
        return new FacilityAssetRecord(asset);
    }

    public FacilityAsset toDomain() {
        return new FacilityAsset(id, siteCode, assetCode, name, category, criticality, operationalStatus, roomId,
                locationCode, manufacturer, modelNumber, serialNumber, installedOn, warrantyExpiresOn,
                serviceIntervalDays, lastServicedOn, custodian, deviceReferenceId, assetReferenceId, statusNotes,
                statusChangedAt, lifecycleStatus, metadata.toDomain());
    }
}
