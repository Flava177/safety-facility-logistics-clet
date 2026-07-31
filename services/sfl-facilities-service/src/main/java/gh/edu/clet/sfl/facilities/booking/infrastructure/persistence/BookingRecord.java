package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.BookingStatus;
import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.ReadinessHoldReason;
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

/**
 * JPA mapping for {@link Booking}. Column names match V10 exactly.
 *
 * <h2>The two columns that are not domain state</h2>
 *
 * {@code occupiedFrom} and {@code occupiedTo} are derived — {@code window.occupied()} — and are stored
 * anyway. That is the whole mechanism by which the estate cannot be double-booked: the {@code GIST}
 * exclusion constraint has to range over columns on this table, and a constraint cannot call a Java
 * method or follow a join.
 *
 * <p>They are written in {@link #apply}, from the aggregate, and never read back into it, so there is
 * exactly one place they can go wrong and a {@code CHECK} in V10 catches even that: {@code occupied_from
 * <= starts_at AND occupied_to >= ends_at}. A future change that widens the buffers but forgets these
 * fails at the database rather than silently letting the next booking start early.
 *
 * <p>They are not {@code GENERATED ALWAYS} columns, which would have been tidier, because
 * {@code timestamptz - interval} is {@code STABLE} rather than {@code IMMUTABLE} in PostgreSQL — it
 * depends on the session time zone — and a stored generated column may only use immutable expressions.
 */
@Entity
@Table(name = "bookings", schema = "facilities")
public class BookingRecord {

    @Id
    private UUID id;
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
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 4000)
    private String description;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(name = "setup_minutes", nullable = false)
    private int setupMinutes;
    @Column(name = "teardown_minutes", nullable = false)
    private int teardownMinutes;
    @Column(name = "occupied_from", nullable = false)
    private Instant occupiedFrom;
    @Column(name = "occupied_to", nullable = false)
    private Instant occupiedTo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;
    @Column(name = "expected_attendees", nullable = false)
    private int expectedAttendees;
    @Column(name = "requested_by", nullable = false, length = 160)
    private String requestedBy;
    @Column(name = "requested_for", length = 200)
    private String requestedFor;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;
    @Column(name = "approval_id")
    private UUID approvalId;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "closure_reason", length = 2000)
    private String closureReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "readiness_hold_reason", length = 30)
    private ReadinessHoldReason readinessHoldReason;
    @Column(name = "readiness_held_at")
    private Instant readinessHeldAt;
    @Column(name = "override_reason", length = 2000)
    private String overrideReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected BookingRecord() {
    }

    public static BookingRecord from(Booking booking) {
        BookingRecord record = new BookingRecord();
        record.apply(booking);
        return record;
    }

    public void apply(Booking booking) {
        id = booking.id();
        bookingReference = booking.bookingReference();
        siteCode = booking.siteCode();
        roomId = booking.roomId();
        roomCode = booking.roomCode();
        purpose = booking.purpose();
        title = booking.title();
        description = booking.description();
        startsAt = booking.window().start();
        endsAt = booking.window().end();
        setupMinutes = booking.window().setupMinutes();
        teardownMinutes = booking.window().teardownMinutes();
        BookingWindow occupied = booking.window().occupied();
        occupiedFrom = occupied.start();
        occupiedTo = occupied.end();
        status = booking.status();
        expectedAttendees = booking.expectedAttendees();
        requestedBy = booking.requestedBy();
        requestedFor = booking.requestedFor();
        requestedAt = booking.requestedAt();
        approvalRequired = booking.approvalRequired();
        approvalId = booking.approvalId();
        confirmedAt = booking.confirmedAt();
        startedAt = booking.startedAt();
        completedAt = booking.completedAt();
        closureReason = booking.closureReason();
        readinessHoldReason = booking.readinessHoldReason();
        readinessHeldAt = booking.readinessHeldAt();
        overrideReason = booking.overrideReason();
        lifecycleStatus = booking.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(booking.metadata());
    }

    public Booking toDomain() {
        return new Booking(id, bookingReference, siteCode, roomId, roomCode, purpose, title, description,
                new BookingWindow(startsAt, endsAt, setupMinutes, teardownMinutes), status,
                expectedAttendees, requestedBy, requestedFor, requestedAt, approvalRequired, approvalId,
                confirmedAt, startedAt, completedAt, closureReason, readinessHoldReason, readinessHeldAt,
                overrideReason, lifecycleStatus, metadata.toDomain());
    }

    public UUID getId() {
        return id;
    }
}
