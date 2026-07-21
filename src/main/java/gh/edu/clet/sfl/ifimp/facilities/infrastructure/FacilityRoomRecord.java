package gh.edu.clet.sfl.ifimp.facilities.infrastructure;

import java.time.Instant;
import java.util.UUID;

import gh.edu.clet.sfl.ifimp.facilities.domain.FacilityRoom;
import gh.edu.clet.sfl.ifimp.facilities.domain.LocationReadinessStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "facility_rooms", schema = "ifimp")
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
    @Column(name = "room_type", length = 80)
    private String roomType;
    @Column
    private Integer capacity;
    @Enumerated(EnumType.STRING)
    @Column(name = "readiness_status", nullable = false, length = 32)
    private LocationReadinessStatus readinessStatus;
    @Column(name = "readiness_notes", length = 1000)
    private String readinessNotes;
    @Column(name = "readiness_updated_at")
    private Instant readinessUpdatedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FacilityRoomRecord() {
    }

    private FacilityRoomRecord(FacilityRoom room) {
        id = room.id();
        floorId = room.floorId();
        siteCode = room.siteCode();
        roomCode = room.roomCode();
        name = room.name();
        roomType = room.roomType();
        capacity = room.capacity();
        readinessStatus = room.readinessStatus();
        readinessNotes = room.readinessNotes();
        readinessUpdatedAt = room.readinessUpdatedAt();
        createdAt = room.createdAt();
    }

    public static FacilityRoomRecord from(FacilityRoom room) {
        return new FacilityRoomRecord(room);
    }

    public FacilityRoom toDomain() {
        return new FacilityRoom(id, floorId, siteCode, roomCode, name, roomType, capacity,
                readinessStatus, readinessNotes, readinessUpdatedAt, createdAt);
    }
}
