package gh.edu.clet.sfl.facilities.eventlogistics.application.ports;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * S173's view of Room &amp; Resource Booking (S159) - SRS-SFL-S173-02: "Resource requests are raised
 * against Room &amp; Resource Booking (S159) for the venue/AV/setup tasks".
 *
 * <p>The venue is a booking; AV, staging, security and signage are bookable resources allocated onto
 * that booking. That is S159's own model - a resource is booked <em>with</em> a space - and it means an
 * AV line cannot be routed until its venue is booked, which the routing service says in so many words
 * rather than inventing a second booking for the same hall.
 *
 * <p>The adapter is the only class in S173 that imports S159, and it calls the real in-process
 * service: S159 is in the same deployable, so there is nothing to simulate.
 */
public interface BookingGatewayPort {

    GatewayOutcome requestVenue(VenueRequest request);

    GatewayOutcome allocate(AllocationRequest request);

    /** The booking's current answer, mapped to S173's statuses. Empty if S159 has no such booking. */
    Optional<GatewayOutcome> bookingState(String bookingId);

    void cancelBooking(String bookingId, String reason, String correlationId);

    void releaseAllocation(String bookingId, String allocationId, String correlationId);

    /**
     * @param requestedFor the coordinator the booking is for - S159 records it beside the platform
     *        account that places it
     * @param idempotencyKey derived from the request and its routing attempt, so a retry replays and a
     *        deliberate re-route after a cancellation does not
     */
    record VenueRequest(String siteCode, UUID roomId, String title, Instant startsAt, Instant endsAt,
            int expectedAttendance, String requestedFor, String originReference, String idempotencyKey,
            String correlationId) {
    }

    record AllocationRequest(String bookingId, UUID resourceId, int quantity, String correlationId) {
    }
}
