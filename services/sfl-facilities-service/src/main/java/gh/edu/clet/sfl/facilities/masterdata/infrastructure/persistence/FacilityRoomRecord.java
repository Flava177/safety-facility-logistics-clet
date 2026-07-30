package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
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

@Entity
@Table(name = "facility_rooms", schema = "facilities")
public class FacilityRoomRecord {

    @Id
    private UUID id;
    @Column(name = "floor_id", nullable = false)
    private UUID floorId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Column(nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false, length = 40)
    private SpaceType spaceType;
    /**
     * The pre-S152 free-text type, kept in step with {@link #spaceType}.
     *
     * <p>Retained so the facilities dashboard page and any other existing reader keep working. It is
     * written from the space type and never edited independently, so the two cannot disagree.
     */
    @Column(name = "room_type", length = 80)
    private String roomType;
    @Column
    private Integer capacity;
    @Column(name = "area_sqm", precision = 12, scale = 2)
    private BigDecimal areaSqm;
    @Column(name = "cost_centre", length = 60)
    private String costCentre;
    @Column(nullable = false)
    private boolean bookable;
    @Column(name = "examination_capable", nullable = false)
    private boolean examinationCapable;
    @Enumerated(EnumType.STRING)
    @Column(name = "readiness_status", nullable = false, length = 32)
    private LocationReadinessStatus readinessStatus;
    @Column(name = "readiness_notes", length = 1000)
    private String readinessNotes;
    @Column(name = "readiness_updated_at")
    private Instant readinessUpdatedAt;
    @Column(name = "readiness_locked", nullable = false)
    private boolean readinessLocked;
    @Column(name = "readiness_locked_by", length = 160)
    private String readinessLockedBy;
    @Column(name = "readiness_locked_at")
    private Instant readinessLockedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected FacilityRoomRecord() {
    }

    private FacilityRoomRecord(FacilityRoom room) {
        id = room.id();
        floorId = room.floorId();
        siteCode = room.siteCode();
        roomCode = room.roomCode();
        name = room.name();
        spaceType = room.spaceType();
        roomType = room.spaceType().name();
        capacity = room.capacity();
        areaSqm = room.areaSqm();
        costCentre = room.costCentre();
        bookable = room.bookable();
        examinationCapable = room.examinationCapable();
        readinessStatus = room.readinessStatus();
        readinessNotes = room.readinessNotes();
        readinessUpdatedAt = room.readinessUpdatedAt();
        readinessLocked = room.readinessLocked();
        readinessLockedBy = room.readinessLockedBy();
        readinessLockedAt = room.readinessLockedAt();
        lifecycleStatus = room.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(room.metadata());
    }

    public static FacilityRoomRecord from(FacilityRoom room) {
        return new FacilityRoomRecord(room);
    }

    public FacilityRoom toDomain() {
        return new FacilityRoom(id, floorId, siteCode, roomCode, name, spaceType, roomType, capacity, areaSqm,
                costCentre, bookable, examinationCapable, readinessStatus, readinessNotes, readinessUpdatedAt,
                readinessLocked, readinessLockedBy, readinessLockedAt, lifecycleStatus, metadata.toDomain());
    }
}
