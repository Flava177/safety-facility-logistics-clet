package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DateTimeRange;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.Trip;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripAcknowledgement;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripAcknowledgementState;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.TripStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Persistence image of {@link Trip}. */
@Entity
@Table(name = "trips", schema = "fleet_logistics")
public class TripEntity {

    @Id
    private UUID id;

    @Column(name = "trip_number", nullable = false, length = 40)
    private String tripNumber;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Column(nullable = false, length = 200)
    private String origin;

    @Column(nullable = false, length = 200)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "operating_mode", nullable = false, length = 30)
    private OperatingMode operatingMode;

    @Column(name = "planned_start", nullable = false)
    private Instant plannedStart;

    @Column(name = "planned_end", nullable = false)
    private Instant plannedEnd;

    @Column(name = "actual_start")
    private Instant actualStart;

    @Column(name = "actual_end")
    private Instant actualEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_hold", length = 30)
    private TripStatus statusBeforeHold;

    @Column(name = "hold_reason", length = 1000)
    private String holdReason;

    @Column(name = "cancellation_reason", length = 1000)
    private String cancellationReason;

    @Column(name = "closure_reason", length = 1000)
    private String closureReason;

    @Column(name = "closure_evidence_id")
    private UUID closureEvidenceId;

    @Column(name = "start_odometer")
    private Long startOdometer;

    @Column(name = "end_odometer")
    private Long endOdometer;

    /** The assigned driver's answer. Independent of {@link #status} — see TripAcknowledgementState. */
    @Enumerated(EnumType.STRING)
    @Column(name = "acknowledgement_state", nullable = false, length = 20)
    private TripAcknowledgementState acknowledgementState;

    @Column(name = "acknowledgement_reason", length = 1000)
    private String acknowledgementReason;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by", length = 160)
    private String acknowledgedBy;

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

    protected TripEntity() {
    }

    public static TripEntity from(Trip trip) {
        TripEntity entity = new TripEntity();
        entity.id = trip.id();
        entity.applyFrom(trip);
        return entity;
    }

    public void applyFrom(Trip trip) {
        this.tripNumber = trip.tripNumber();
        this.vehicleId = trip.vehicleId();
        this.driverId = trip.driverId();
        this.siteCode = trip.siteCode().value();
        this.purpose = trip.purpose();
        this.origin = trip.origin();
        this.destination = trip.destination();
        this.operatingMode = trip.operatingMode();
        this.plannedStart = trip.plannedPeriod().start();
        this.plannedEnd = trip.plannedPeriod().end();
        this.actualStart = trip.actualStart();
        this.actualEnd = trip.actualEnd();
        this.status = trip.status();
        this.statusBeforeHold = trip.statusBeforeHold();
        this.holdReason = trip.holdReason();
        this.cancellationReason = trip.cancellationReason();
        this.closureReason = trip.closureReason();
        this.closureEvidenceId = trip.closureEvidenceId();
        this.startOdometer = trip.startOdometer();
        this.endOdometer = trip.endOdometer();
        this.acknowledgementState = trip.acknowledgement().state();
        this.acknowledgementReason = trip.acknowledgement().reason();
        this.acknowledgedAt = trip.acknowledgement().answeredAt();
        this.acknowledgedBy = trip.acknowledgement().answeredBy();
        this.createdBy = trip.metadata().createdBy();
        this.createdAt = trip.metadata().createdAt();
        this.lastModifiedBy = trip.metadata().lastModifiedBy();
        this.lastModifiedAt = trip.metadata().lastModifiedAt();
        this.sourceChannel = trip.metadata().sourceChannel();
        this.auditCorrelationId = trip.metadata().auditCorrelationId();
    }

    public Trip toDomain() {
        return new Trip(id, tripNumber, vehicleId, driverId, SiteCode.of(siteCode), purpose, origin, destination,
                operatingMode, DateTimeRange.of(plannedStart, plannedEnd), actualStart, actualEnd, status,
                statusBeforeHold, holdReason, cancellationReason, closureReason, closureEvidenceId, startOdometer,
                endOdometer,
                new TripAcknowledgement(
                        acknowledgementState == null ? TripAcknowledgementState.PENDING : acknowledgementState,
                        acknowledgementReason, acknowledgedAt, acknowledgedBy),
                RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version,
                        sourceChannel, auditCorrelationId));
    }
}
