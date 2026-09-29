package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreach;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreachType;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link SlaBreach}. */
@Entity
@Table(name = "cleaning_sla_breaches", schema = "facilities")
public class CleaningSlaBreachRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;
    @Column(name = "task_id", nullable = false)
    private UUID taskId;
    @Column(name = "sla_terms_id", nullable = false)
    private UUID slaTermsId;
    @Enumerated(EnumType.STRING)
    @Column(name = "breach_type", nullable = false, length = 20)
    private SlaBreachType breachType;
    @Column(name = "contracted_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal contractedValue;
    @Column(name = "actual_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal actualValue;
    @Column(nullable = false, length = 500)
    private String basis;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningSlaBreachRecord() {
    }

    static CleaningSlaBreachRecord empty() {
        return new CleaningSlaBreachRecord();
    }

    public void apply(SlaBreach breach) {
        id = breach.id();
        siteCode = breach.siteCode();
        vendorId = breach.vendorId();
        taskId = breach.taskId();
        slaTermsId = breach.slaTermsId();
        breachType = breach.type();
        contractedValue = breach.contractedValue();
        actualValue = breach.actualValue();
        basis = breach.basis().length() > 500 ? breach.basis().substring(0, 500) : breach.basis();
        recordedAt = breach.recordedAt();
        metadata = RecordMetadataEmbeddable.from(breach.metadata());
    }

    public SlaBreach toDomain() {
        return new SlaBreach(id, siteCode, vendorId, taskId, slaTermsId, breachType, contractedValue, actualValue,
                basis, recordedAt, metadata.toDomain(recordVersion()));
    }
}
