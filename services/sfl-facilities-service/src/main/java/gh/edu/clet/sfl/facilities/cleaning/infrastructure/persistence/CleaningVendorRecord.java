package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA mapping for {@link CleaningVendor}. */
@Entity
@Table(name = "cleaning_vendors", schema = "facilities")
public class CleaningVendorRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "vendor_master_reference", nullable = false, length = 80)
    private String vendorMasterReference;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorStatus status;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningVendorRecord() {
    }

    static CleaningVendorRecord empty() {
        return new CleaningVendorRecord();
    }

    public void apply(CleaningVendor vendor) {
        id = vendor.id();
        siteCode = vendor.siteCode();
        vendorMasterReference = vendor.vendorMasterReference();
        name = vendor.name();
        status = vendor.status();
        metadata = RecordMetadataEmbeddable.from(vendor.metadata());
    }

    public CleaningVendor toDomain() {
        return new CleaningVendor(id, siteCode, vendorMasterReference, name, status, metadata.toDomain(recordVersion()));
    }
}
