package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
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

/** JPA mapping for {@link DefectItem}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_defects", schema = "facilities")
public class DefectItemRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "defect_reference", nullable = false, length = 40)
    private String defectReference;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "contractor_id", nullable = false)
    private UUID contractorId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(nullable = false, length = 4000)
    private String description;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "location_code", length = 80)
    private String locationCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DefectItem.Priority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DefectItem.Status status;
    @Column(name = "work_order_id")
    private UUID workOrderId;
    @Column(name = "work_order_number", length = 40)
    private String workOrderNumber;
    @Column(name = "fault_number", length = 40)
    private String faultNumber;
    @Column(name = "work_order_status", length = 30)
    private String workOrderStatus;
    @Column(name = "deferral_reason", length = 2000)
    private String deferralReason;
    @Column(name = "resolved_by", length = 160)
    private String resolvedBy;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "raised_by", nullable = false, length = 160)
    private String raisedBy;
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected DefectItemRecord() {
    }

    static DefectItemRecord empty() {
        return new DefectItemRecord();
    }

    void apply(DefectItem defect) {
        id = defect.id();
        defectReference = defect.defectReference();
        projectId = defect.projectId();
        contractorId = defect.contractorId();
        siteCode = defect.siteCode();
        description = defect.description();
        roomId = defect.roomId();
        locationCode = defect.locationCode();
        priority = defect.priority();
        status = defect.status();
        workOrderId = defect.workOrderId();
        workOrderNumber = defect.workOrderNumber();
        faultNumber = defect.faultNumber();
        workOrderStatus = defect.workOrderStatus();
        deferralReason = defect.deferralReason();
        resolvedBy = defect.resolvedBy();
        resolvedAt = defect.resolvedAt();
        raisedBy = defect.raisedBy();
        raisedAt = defect.raisedAt();
        metadata = RecordMetadataEmbeddable.from(defect.metadata());
    }

    DefectItem toDomain() {
        return new DefectItem(id, defectReference, projectId, contractorId, siteCode, description, roomId, locationCode,
                priority, status, workOrderId, workOrderNumber, faultNumber, workOrderStatus, deferralReason, resolvedBy,
                resolvedAt, raisedBy, raisedAt, metadata.toDomain(recordVersion()));
    }
}
