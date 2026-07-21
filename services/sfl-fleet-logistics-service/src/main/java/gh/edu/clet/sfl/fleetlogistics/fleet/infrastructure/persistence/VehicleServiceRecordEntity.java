package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceOutcome;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.ServiceType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleServiceRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Persistence image of {@link VehicleServiceRecord}. */
@Entity
@Table(name = "vehicle_service_records", schema = "fleet_logistics")
public class VehicleServiceRecordEntity {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 60)
    private ServiceType serviceType;

    @Column(name = "performed_on", nullable = false)
    private LocalDate performedOn;

    @Column(name = "odometer_at_service", nullable = false)
    private long odometerAtService;

    @Column(name = "next_due_on")
    private LocalDate nextDueOn;

    @Column(name = "next_due_odometer")
    private Long nextDueOdometer;

    @Column(name = "provider_reference", length = 160)
    private String providerReference;

    @Column(name = "work_summary", nullable = false, length = 2000)
    private String workSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ServiceOutcome outcome;

    @Column(name = "evidence_id")
    private UUID evidenceId;

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

    protected VehicleServiceRecordEntity() {
    }

    public static VehicleServiceRecordEntity from(VehicleServiceRecord record) {
        VehicleServiceRecordEntity entity = new VehicleServiceRecordEntity();
        entity.id = record.id();
        entity.applyFrom(record);
        return entity;
    }

    public void applyFrom(VehicleServiceRecord record) {
        this.vehicleId = record.vehicleId();
        this.siteCode = record.siteCode().value();
        this.serviceType = record.serviceType();
        this.performedOn = record.performedOn();
        this.odometerAtService = record.odometerAtService();
        this.nextDueOn = record.nextDueOn();
        this.nextDueOdometer = record.nextDueOdometer();
        this.providerReference = record.providerReference();
        this.workSummary = record.workSummary();
        this.outcome = record.outcome();
        this.evidenceId = record.evidenceId();
        this.createdBy = record.metadata().createdBy();
        this.createdAt = record.metadata().createdAt();
        this.lastModifiedBy = record.metadata().lastModifiedBy();
        this.lastModifiedAt = record.metadata().lastModifiedAt();
        this.sourceChannel = record.metadata().sourceChannel();
        this.auditCorrelationId = record.metadata().auditCorrelationId();
    }

    public VehicleServiceRecord toDomain() {
        return new VehicleServiceRecord(id, vehicleId, SiteCode.of(siteCode), serviceType, performedOn,
                odometerAtService, nextDueOn, nextDueOdometer, providerReference, workSummary, outcome, evidenceId,
                RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version,
                        sourceChannel, auditCorrelationId));
    }
}
