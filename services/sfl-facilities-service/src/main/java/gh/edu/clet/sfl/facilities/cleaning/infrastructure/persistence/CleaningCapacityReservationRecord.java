package gh.edu.clet.sfl.facilities.cleaning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.cleaning.domain.CapacityReservation;
import gh.edu.clet.sfl.facilities.cleaning.domain.ReservationStatus;
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

/** JPA mapping for {@link CapacityReservation}. */
@Entity
@Table(name = "cleaning_capacity_reservations", schema = "facilities")
public class CleaningCapacityReservationRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "location_code", length = 80)
    private String locationCode;
    @Column(name = "window_from", nullable = false)
    private Instant windowFrom;
    @Column(name = "window_to", nullable = false)
    private Instant windowTo;
    @Column(length = 500)
    private String scope;
    @Column(name = "event_reference", nullable = false, length = 120)
    private String eventReference;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;
    @Column(name = "task_id")
    private UUID taskId;
    @Column(name = "competing_task_id")
    private UUID competingTaskId;
    @Column(name = "competing_commitment", length = 600)
    private String competingCommitment;
    @Column(name = "competing_from")
    private Instant competingFrom;
    @Column(name = "competing_to")
    private Instant competingTo;
    @Column(name = "released_at")
    private Instant releasedAt;
    @Column(name = "release_reason", length = 1000)
    private String releaseReason;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected CleaningCapacityReservationRecord() {
    }

    static CleaningCapacityReservationRecord empty() {
        return new CleaningCapacityReservationRecord();
    }

    public void apply(CapacityReservation reservation) {
        id = reservation.id();
        siteCode = reservation.siteCode();
        roomId = reservation.roomId();
        locationCode = reservation.locationCode();
        windowFrom = reservation.windowFrom();
        windowTo = reservation.windowTo();
        scope = reservation.scope();
        eventReference = reservation.eventReference();
        requestedBy = reservation.requestedBy();
        status = reservation.status();
        taskId = reservation.taskId();
        competingTaskId = reservation.competingTaskId();
        competingCommitment = reservation.competingCommitment();
        competingFrom = reservation.competingFrom();
        competingTo = reservation.competingTo();
        releasedAt = reservation.releasedAt();
        releaseReason = reservation.releaseReason();
        metadata = RecordMetadataEmbeddable.from(reservation.metadata());
    }

    public CapacityReservation toDomain() {
        return new CapacityReservation(id, siteCode, roomId, locationCode, windowFrom, windowTo, scope, eventReference,
                requestedBy, status, taskId, competingTaskId, competingCommitment, competingFrom, competingTo,
                releasedAt, releaseReason, metadata.toDomain(recordVersion()));
    }
}
