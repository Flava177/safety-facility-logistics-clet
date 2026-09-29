package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.PermitRecord;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link PermitRecord}, the S164 projection. Column names match V21 exactly. */
@Entity
@Table(name = "construction_permit_projection", schema = "facilities")
public class PermitProjectionRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "permit_id", nullable = false, length = 80)
    private String permitId;
    @Column(name = "permit_reference", length = 80)
    private String permitReference;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "work_type", length = 60)
    private String workType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermitRecord.Status status;
    @Column(name = "valid_from")
    private Instant validFrom;
    @Column(name = "valid_to")
    private Instant validTo;
    @Column(name = "contractor_reference", length = 120)
    private String contractorReference;
    @Column(name = "origin_reference", length = 120)
    private String originReference;
    @Column(name = "last_event_type", nullable = false, length = 120)
    private String lastEventType;
    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;
    @Column(name = "last_message_id")
    private UUID lastMessageId;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected PermitProjectionRecord() {
    }

    static PermitProjectionRecord empty() {
        return new PermitProjectionRecord();
    }

    void apply(PermitRecord permit) {
        id = permit.id();
        permitId = permit.permitId();
        permitReference = permit.permitReference();
        siteCode = permit.siteCode();
        workType = permit.workType();
        status = permit.status();
        validFrom = permit.validFrom();
        validTo = permit.validTo();
        contractorReference = permit.contractorReference();
        originReference = permit.originReference();
        lastEventType = permit.lastEventType();
        lastEventAt = permit.lastEventAt();
        lastMessageId = permit.lastMessageId();
        metadata = RecordMetadataEmbeddable.from(permit.metadata());
    }

    PermitRecord toDomain() {
        return new PermitRecord(id, permitId, permitReference, siteCode, workType, status, validFrom, validTo,
                contractorReference, originReference, lastEventType, lastEventAt, lastMessageId,
                metadata.toDomain(recordVersion()));
    }
}
