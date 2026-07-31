package gh.edu.clet.sfl.facilities.booking.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.booking.domain.BookingWindow;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for {@link ResourceAllocation}. Column names match V10 exactly.
 *
 * <p>{@code isExclusive} is copied from the resource at allocation time rather than joined, because
 * the partial exclusion constraint's {@code WHERE} clause can only name columns on this table. See
 * {@link ResourceAllocation} for why the single-instance and pooled cases are enforced in different
 * places.
 */
@Entity
@Table(name = "booking_resource_allocations", schema = "facilities")
public class ResourceAllocationRecord {

    @Id
    private UUID id;
    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;
    @Column(name = "resource_code", nullable = false, length = 80)
    private String resourceCode;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
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
    @Column(nullable = false)
    private int quantity;
    @Column(name = "is_exclusive", nullable = false)
    private boolean exclusive;
    @Column(name = "released_with_booking", nullable = false)
    private boolean releasedWithBooking;
    @Column(name = "allocated_by", nullable = false, length = 160)
    private String allocatedBy;
    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt;

    protected ResourceAllocationRecord() {
    }

    public void apply(ResourceAllocation allocation) {
        id = allocation.id();
        bookingId = allocation.bookingId();
        resourceId = allocation.resourceId();
        resourceCode = allocation.resourceCode();
        siteCode = allocation.siteCode();
        startsAt = allocation.window().start();
        endsAt = allocation.window().end();
        setupMinutes = allocation.window().setupMinutes();
        teardownMinutes = allocation.window().teardownMinutes();
        BookingWindow occupied = allocation.window().occupied();
        occupiedFrom = occupied.start();
        occupiedTo = occupied.end();
        quantity = allocation.quantity();
        exclusive = allocation.exclusive();
        releasedWithBooking = allocation.releasedWithBooking();
        allocatedBy = allocation.allocatedBy();
        allocatedAt = allocation.allocatedAt();
    }

    public ResourceAllocation toDomain() {
        return new ResourceAllocation(id, bookingId, resourceId, resourceCode, siteCode,
                new BookingWindow(startsAt, endsAt, setupMinutes, teardownMinutes), quantity, exclusive,
                releasedWithBooking, allocatedBy, allocatedAt);
    }
}
