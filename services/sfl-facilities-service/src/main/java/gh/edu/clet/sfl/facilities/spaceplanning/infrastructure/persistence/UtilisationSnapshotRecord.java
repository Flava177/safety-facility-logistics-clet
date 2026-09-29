package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link UtilisationSnapshot}. */
@Entity
@Table(name = "space_utilisation_snapshots", schema = "facilities")
public class UtilisationSnapshotRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Column(name = "period_start", nullable = false)
    private Instant periodStart;
    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;
    private Integer capacity;
    @Column(nullable = false)
    private boolean bookable;
    @Column(name = "booking_count", nullable = false)
    private int bookingCount;
    @Column(name = "taken_up_count", nullable = false)
    private int takenUpCount;
    @Column(name = "no_show_count", nullable = false)
    private int noShowCount;
    @Column(name = "booked_minutes", nullable = false)
    private long bookedMinutes;
    @Column(name = "used_minutes", nullable = false)
    private long usedMinutes;
    @Column(name = "available_minutes", nullable = false)
    private long availableMinutes;
    @Column(name = "frequency_rate", precision = 8, scale = 4)
    private BigDecimal frequencyRate;
    @Column(name = "occupancy_rate", precision = 8, scale = 4)
    private BigDecimal occupancyRate;
    @Column(name = "utilisation_rate", precision = 8, scale = 4)
    private BigDecimal utilisationRate;
    @Column(name = "planned_headcount", nullable = false)
    private int plannedHeadcount;
    @Column(name = "planned_occupancy_rate", precision = 8, scale = 4)
    private BigDecimal plannedOccupancyRate;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected UtilisationSnapshotRecord() {
    }

    void apply(UtilisationSnapshot s) {
        id = s.id();
        siteCode = s.siteCode();
        roomId = s.roomId();
        roomCode = s.roomCode();
        periodStart = s.periodStart();
        periodEnd = s.periodEnd();
        capacity = s.capacity();
        bookable = s.bookable();
        bookingCount = s.bookingCount();
        takenUpCount = s.takenUpCount();
        noShowCount = s.noShowCount();
        bookedMinutes = s.bookedMinutes();
        usedMinutes = s.usedMinutes();
        availableMinutes = s.availableMinutes();
        frequencyRate = s.frequencyRate();
        occupancyRate = s.occupancyRate();
        utilisationRate = s.utilisationRate();
        plannedHeadcount = s.plannedHeadcount();
        plannedOccupancyRate = s.plannedOccupancyRate();
        recordedAt = s.recordedAt();
        metadata = RecordMetadataEmbeddable.from(s.metadata());
    }

    UtilisationSnapshot toDomain() {
        return new UtilisationSnapshot(id, siteCode, roomId, roomCode, periodStart, periodEnd, capacity, bookable,
                bookingCount, takenUpCount, noShowCount, bookedMinutes, usedMinutes, availableMinutes, frequencyRate,
                occupancyRate, utilisationRate, plannedHeadcount, plannedOccupancyRate, recordedAt,
                metadata.toDomain(recordVersion()));
    }
}
