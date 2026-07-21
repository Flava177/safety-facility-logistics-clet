package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowItem;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Persistence image of {@link FleetWorkflowItem}. */
@Entity
@Table(name = "fleet_workflow_items", schema = "fleet_logistics")
public class FleetWorkflowItemEntity {

    @Id
    private UUID id;

    @Column(name = "workflow_number", nullable = false, length = 40)
    private String workflowNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_type", nullable = false, length = 60)
    private FleetWorkflowType workflowType;

    @Column(name = "related_record_type", length = 80)
    private String relatedRecordType;

    @Column(name = "related_record_id", length = 160)
    private String relatedRecordId;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "operating_mode", nullable = false, length = 30)
    private OperatingMode operatingMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FleetWorkflowStatus status;

    @Column(length = 160)
    private String assignee;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Column(name = "response_due_at")
    private Instant responseDueAt;

    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_hold", length = 30)
    private FleetWorkflowStatus statusBeforeHold;

    @Column(name = "hold_reason", length = 1000)
    private String holdReason;

    @Column(name = "closure_reason", length = 1000)
    private String closureReason;

    @Column(name = "closure_evidence_id")
    private UUID closureEvidenceId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 160)
    private String closedBy;

    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_modified_by", nullable = false, length = 160)
    private String lastModifiedBy;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 40)
    private SourceChannel sourceChannel;

    @Column(name = "audit_correlation_id", length = 120)
    private String auditCorrelationId;

    protected FleetWorkflowItemEntity() {
    }

    public static FleetWorkflowItemEntity from(FleetWorkflowItem item) {
        FleetWorkflowItemEntity entity = new FleetWorkflowItemEntity();
        entity.id = item.id();
        entity.applyFrom(item);
        return entity;
    }

    public void applyFrom(FleetWorkflowItem item) {
        this.workflowNumber = item.workflowNumber();
        this.workflowType = item.workflowType();
        this.relatedRecordType = item.relatedRecordType();
        this.relatedRecordId = item.relatedRecordId();
        this.siteCode = item.siteCode().value();
        this.title = item.title();
        this.description = item.description();
        this.priority = item.priority();
        this.severity = item.severity();
        this.operatingMode = item.operatingMode();
        this.status = item.status();
        this.assignee = item.assignee();
        this.slaDueAt = item.slaDueAt();
        this.responseDueAt = item.responseDueAt();
        this.escalationLevel = item.escalationLevel();
        this.firstResponseAt = item.firstResponseAt();
        this.statusBeforeHold = item.statusBeforeHold();
        this.holdReason = item.holdReason();
        this.closureReason = item.closureReason();
        this.closureEvidenceId = item.closureEvidenceId();
        this.closedAt = item.closedAt();
        this.closedBy = item.closedBy();
        this.createdBy = item.metadata().createdBy();
        this.createdAt = item.metadata().createdAt();
        this.lastModifiedBy = item.metadata().lastModifiedBy();
        this.lastModifiedAt = item.metadata().lastModifiedAt();
        this.sourceChannel = item.metadata().sourceChannel();
        this.auditCorrelationId = item.metadata().auditCorrelationId();
    }

    public FleetWorkflowItem toDomain() {
        return new FleetWorkflowItem(id, workflowNumber, workflowType, relatedRecordType, relatedRecordId,
                SiteCode.of(siteCode), title, description, priority, severity, operatingMode, status, assignee,
                slaDueAt, responseDueAt, escalationLevel, firstResponseAt, statusBeforeHold, holdReason,
                closureReason, closureEvidenceId, closedAt, closedBy,
                RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version,
                        sourceChannel, auditCorrelationId));
    }
}
