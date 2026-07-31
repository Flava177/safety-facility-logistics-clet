package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.NoShowRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for {@link NoShowRecord}. Column names match V10 exactly.
 *
 * <p>Named {@code ...Entity} rather than {@code ...Record} only because the domain type is already
 * called {@code NoShowRecord} and two types differing by package would be read wrong at a glance.
 */
@Entity
@Table(name = "booking_no_shows", schema = "facilities")
public class NoShowRecordEntity {

    @Id
    private UUID id;
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
    @Column(name = "booking_reference", nullable = false, length = 40)
    private String bookingReference;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "room_code", nullable = false, length = 80)
    private String roomCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingPurpose purpose;
    @Column(name = "window_start", nullable = false)
    private Instant windowStart;
    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;
    @Column(name = "minutes_held_unused", nullable = false)
    private long minutesHeldUnused;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected NoShowRecordEntity() {
    }

    public void apply(NoShowRecord record) {
        id = record.id();
        bookingId = record.bookingId();
        bookingReference = record.bookingReference();
        siteCode = record.siteCode();
        roomId = record.roomId();
        roomCode = record.roomCode();
        purpose = record.purpose();
        windowStart = record.windowStart();
        windowEnd = record.windowEnd();
        minutesHeldUnused = record.minutesHeldUnused();
        requestedBy = record.requestedBy();
        recordedAt = record.recordedAt();
    }

    public NoShowRecord toDomain() {
        return new NoShowRecord(id, bookingId, bookingReference, siteCode, roomId, roomCode, purpose,
                windowStart, windowEnd, minutesHeldUnused, requestedBy, recordedAt);
    }
}
