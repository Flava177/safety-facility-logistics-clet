package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link FacilityFault}. Column names match V9 exactly. */
@Entity
@Table(name = "facility_faults", schema = "facilities")
public class FacilityFaultRecord {

    @Id
    private UUID id;
    @Column(name = "fault_number", nullable = false, length = 40)
    private String faultNumber;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "location_code", length = 120)
    private String locationCode;
    @Column(name = "asset_id")
    private UUID assetId;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 4000)
    private String description;
    @Column(length = 120)
    private String category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FaultPriority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FacilityFaultStatus status;
    @Column(name = "reported_by", nullable = false, length = 160)
    private String reportedBy;
    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;
    @Column(name = "triaged_by", length = 160)
    private String triagedBy;
    @Column(name = "triaged_at")
    private Instant triagedAt;
    @Column(name = "triage_notes", length = 2000)
    private String triageNotes;
    @Column(name = "duplicate_of_fault_id")
    private UUID duplicateOfFaultId;
    @Column(name = "work_order_id")
    private UUID workOrderId;
    @Column(name = "sla_due_at")
    private Instant slaDueAt;
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel;
    @Column(name = "escalated_at")
    private Instant escalatedAt;
    @Column(name = "blocker_raised", nullable = false)
    private boolean blockerRaised;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected FacilityFaultRecord() {
    }

    public static FacilityFaultRecord from(FacilityFault fault) {
        FacilityFaultRecord record = new FacilityFaultRecord();
        record.apply(fault);
        return record;
    }

    /** Copies the aggregate onto this row. Used by the adapter so an update reuses the managed entity. */
    public void apply(FacilityFault fault) {
        id = fault.id();
        faultNumber = fault.faultNumber();
        siteCode = fault.siteCode();
        roomId = fault.roomId();
        locationCode = fault.locationCode();
        assetId = fault.assetId();
        title = fault.title();
        description = fault.description();
        category = fault.category();
        priority = fault.priority();
        status = fault.status();
        reportedBy = fault.reportedBy();
        reportedAt = fault.reportedAt();
        triagedBy = fault.triagedBy();
        triagedAt = fault.triagedAt();
        triageNotes = fault.triageNotes();
        duplicateOfFaultId = fault.duplicateOfFaultId();
        workOrderId = fault.workOrderId();
        slaDueAt = fault.slaDueAt();
        escalationLevel = fault.escalationLevel();
        escalatedAt = fault.escalatedAt();
        blockerRaised = fault.blockerRaised();
        resolvedAt = fault.resolvedAt();
        resolutionNotes = fault.resolutionNotes();
        lifecycleStatus = fault.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(fault.metadata());
    }

    public FacilityFault toDomain() {
        return new FacilityFault(id, faultNumber, siteCode, roomId, locationCode, assetId, title, description,
                category, priority, status, reportedBy, reportedAt, triagedBy, triagedAt, triageNotes,
                duplicateOfFaultId, workOrderId, slaDueAt, escalationLevel, escalatedAt, blockerRaised,
                resolvedAt, resolutionNotes, lifecycleStatus, metadata.toDomain());
    }

    public UUID getId() {
        return id;
    }
}
