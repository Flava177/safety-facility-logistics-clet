package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A reference recorded as known to Vendor Master (S133), pending a real integration. Read only by
 * {@code RecordedVendorMasterAdapter}; see its Javadoc and V19 for why this table exists at all.
 */
@Entity
@Table(name = "cleaning_vendor_master_references", schema = "facilities")
public class CleaningVendorMasterReferenceRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "vendor_master_reference", nullable = false, length = 80)
    private String vendorMasterReference;
    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "evidence_note", length = 1000)
    private String evidenceNote;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningVendorMasterReferenceRecord() {
    }

    public static CleaningVendorMasterReferenceRecord create(String siteCode, String reference, String legalName,
            String evidenceNote, String actorId, Instant at, SourceChannel channel, String correlationId) {
        CleaningVendorMasterReferenceRecord record = new CleaningVendorMasterReferenceRecord();
        record.id = UUID.randomUUID();
        record.siteCode = siteCode;
        record.vendorMasterReference = reference;
        record.legalName = legalName;
        record.active = true;
        record.evidenceNote = evidenceNote;
        record.metadata = RecordMetadataEmbeddable.from(RecordMetadata.createdBy(actorId, at, channel, correlationId));
        return record;
    }

    public String reference() {
        return vendorMasterReference;
    }

    public String legalName() {
        return legalName;
    }

    public boolean active() {
        return active;
    }
}
