package gh.edu.clet.sfl.ifimp.maintenance.infrastructure;

import java.time.Instant;
import java.util.UUID;

import gh.edu.clet.sfl.ifimp.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.ifimp.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.ifimp.maintenance.domain.FaultPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "facility_faults", schema = "ifimp")
public class FacilityFaultRecord {

    @Id
    private UUID id;
    @Column(name = "fault_number", nullable = false, length = 40, unique = true)
    private String faultNumber;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "location_code", nullable = false, length = 80)
    private String locationCode;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 4000)
    private String description;
    @Column(length = 120)
    private String category;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FaultPriority priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FacilityFaultStatus status;
    @Column(name = "reported_by", nullable = false, length = 160)
    private String reportedBy;
    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;
    @Column(name = "work_order_id")
    private UUID workOrderId;

    protected FacilityFaultRecord() {
    }

    private FacilityFaultRecord(FacilityFault fault) {
        id = fault.id();
        faultNumber = fault.faultNumber();
        siteCode = fault.siteCode();
        locationCode = fault.locationCode();
        title = fault.title();
        description = fault.description();
        category = fault.category();
        priority = fault.priority();
        status = fault.status();
        reportedBy = fault.reportedBy();
        reportedAt = fault.reportedAt();
        workOrderId = fault.workOrderId();
    }

    public static FacilityFaultRecord from(FacilityFault fault) {
        return new FacilityFaultRecord(fault);
    }

    public FacilityFault toDomain() {
        return new FacilityFault(id, faultNumber, siteCode, locationCode, title, description, category,
                priority, status, reportedBy, reportedAt, workOrderId);
    }
}

