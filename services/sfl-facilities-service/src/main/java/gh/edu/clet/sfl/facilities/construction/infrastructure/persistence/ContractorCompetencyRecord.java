package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.ContractorCompetency;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link ContractorCompetency}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_contractor_competencies", schema = "facilities")
public class ContractorCompetencyRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "contractor_id", nullable = false)
    private UUID contractorId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "certification_code", nullable = false, length = 60)
    private String certificationCode;
    @Column(length = 500)
    private String description;
    @Column(name = "certificate_reference", length = 120)
    private String certificateReference;
    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ContractorCompetencyRecord() {
    }

    static ContractorCompetencyRecord empty() {
        return new ContractorCompetencyRecord();
    }

    void apply(ContractorCompetency competency) {
        id = competency.id();
        contractorId = competency.contractorId();
        siteCode = competency.siteCode();
        certificationCode = competency.certificationCode();
        description = competency.description();
        certificateReference = competency.certificateReference();
        expiresOn = competency.expiresOn();
        metadata = RecordMetadataEmbeddable.from(competency.metadata());
    }

    ContractorCompetency toDomain() {
        return new ContractorCompetency(id, contractorId, siteCode, certificationCode, description, certificateReference,
                expiresOn, metadata.toDomain(recordVersion()));
    }
}
