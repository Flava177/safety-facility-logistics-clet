package gh.edu.clet.sfl.assetvisibility.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import gh.edu.clet.sfl.assetvisibility.domain.AssetCategory;
import gh.edu.clet.sfl.assetvisibility.domain.AssetReference;
import gh.edu.clet.sfl.assetvisibility.domain.AssetStatus;
import gh.edu.clet.sfl.assetvisibility.domain.LocationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "asset_references", schema = "asset_visibility")
public class AssetReferenceRecord {

    @Id
    private UUID id;
    @Column(name = "asset_code", nullable = false, length = 80, unique = true)
    private String assetCode;
    @Column(nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AssetCategory category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AssetStatus status;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 40)
    private LocationType locationType;
    @Column(name = "location_reference", nullable = false, length = 120)
    private String locationReference;
    @Column(name = "custodian_reference", length = 160)
    private String custodianReference;
    @Column(name = "external_reference", length = 160)
    private String externalReference;
    @Column(name = "evidence_reference", length = 180)
    private String evidenceReference;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AssetReferenceRecord() {
    }

    private AssetReferenceRecord(AssetReference assetReference) {
        id = assetReference.id();
        assetCode = assetReference.assetCode();
        name = assetReference.name();
        category = assetReference.category();
        status = assetReference.status();
        siteCode = assetReference.siteCode();
        locationType = assetReference.locationType();
        locationReference = assetReference.locationReference();
        custodianReference = assetReference.custodianReference();
        externalReference = assetReference.externalReference();
        evidenceReference = assetReference.evidenceReference();
        createdAt = assetReference.createdAt();
        updatedAt = assetReference.updatedAt();
    }

    public static AssetReferenceRecord from(AssetReference assetReference) {
        return new AssetReferenceRecord(assetReference);
    }

    public AssetReference toDomain() {
        return new AssetReference(id, assetCode, name, category, status, siteCode, locationType, locationReference,
                custodianReference, externalReference, evidenceReference, createdAt, updatedAt);
    }
}