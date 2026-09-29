package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link Contractor}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_contractors", schema = "facilities")
public class ContractorRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "contractor_code", nullable = false, length = 40)
    private String contractorCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "vendor_reference", length = 120)
    private String vendorReference;
    @Column(name = "insurance_provider", length = 200)
    private String insuranceProvider;
    @Column(name = "insurance_policy_reference", length = 120)
    private String insurancePolicyReference;
    @Column(name = "insurance_expires_on", nullable = false)
    private LocalDate insuranceExpiresOn;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ContractorRecord() {
    }

    static ContractorRecord empty() {
        return new ContractorRecord();
    }

    void apply(Contractor contractor) {
        id = contractor.id();
        siteCode = contractor.siteCode();
        contractorCode = contractor.contractorCode();
        name = contractor.name();
        vendorReference = contractor.vendorReference();
        insuranceProvider = contractor.insuranceProvider();
        insurancePolicyReference = contractor.insurancePolicyReference();
        insuranceExpiresOn = contractor.insuranceExpiresOn();
        lifecycleStatus = contractor.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(contractor.metadata());
    }

    Contractor toDomain() {
        return new Contractor(id, siteCode, contractorCode, name, vendorReference, insuranceProvider,
                insurancePolicyReference, insuranceExpiresOn, lifecycleStatus, metadata.toDomain(recordVersion()));
    }
}
