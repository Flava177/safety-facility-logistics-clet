package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.VendorSlaTerms;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link VendorSlaTerms}. */
@Entity
@Table(name = "cleaning_vendor_sla_terms", schema = "facilities")
public class CleaningVendorSlaTermsRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Column(name = "terms_version", nullable = false)
    private int termsVersion;
    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;
    @Column(name = "completion_minutes", nullable = false)
    private int completionMinutes;
    @Column(name = "quality_floor", nullable = false, precision = 3, scale = 2)
    private BigDecimal qualityFloor;
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;
    @Column(name = "effective_to")
    private Instant effectiveTo;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningVendorSlaTermsRecord() {
    }

    static CleaningVendorSlaTermsRecord empty() {
        return new CleaningVendorSlaTermsRecord();
    }

    public void apply(VendorSlaTerms terms) {
        id = terms.id();
        siteCode = terms.siteCode();
        vendorId = terms.vendorId();
        termsVersion = terms.version();
        responseMinutes = terms.responseMinutes();
        completionMinutes = terms.completionMinutes();
        qualityFloor = terms.qualityFloor();
        effectiveFrom = terms.effectiveFrom();
        effectiveTo = terms.effectiveTo();
        metadata = RecordMetadataEmbeddable.from(terms.metadata());
    }

    public VendorSlaTerms toDomain() {
        return new VendorSlaTerms(id, siteCode, vendorId, termsVersion, responseMinutes, completionMinutes,
                qualityFloor, effectiveFrom, effectiveTo, metadata.toDomain(recordVersion()));
    }
}
