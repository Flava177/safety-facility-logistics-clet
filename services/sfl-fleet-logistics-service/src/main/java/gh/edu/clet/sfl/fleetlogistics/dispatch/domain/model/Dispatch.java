package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A dispatch manifest carrying courier items under seal, optionally on an S166 trip. Closure is blocked
 * while a related exception or unresolved custody gap exists (enforced by the application service through
 * {@link gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.DispatchClosurePolicy}); the domain here
 * enforces the source-state rules of each transition.
 */
public record Dispatch(UUID id, String manifestNumber, SiteCode siteCode, String route, String assignedHandler,
        String destinationCentre, String examinationContext, UUID tripId, UUID vehicleId, UUID driverId,
        int itemCount, List<String> sealIds, Status status, Instant dispatchedAt, Instant receivedAt,
        Instant reconciledAt, String closureReason, RecordMetadata metadata) {

    public enum Status { DRAFT, SEALED, DISPATCHED, IN_TRANSIT, RECEIVED, RETURNED, RECONCILED, CLOSED, EXCEPTION }

    public Dispatch {
        Objects.requireNonNull(id); Objects.requireNonNull(siteCode); Objects.requireNonNull(status);
        Objects.requireNonNull(metadata);
        manifestNumber = require(manifestNumber, "manifestNumber");
        route = require(route, "route");
        assignedHandler = require(assignedHandler, "assignedHandler");
        sealIds = sealIds == null ? List.of() : List.copyOf(sealIds);
        if (itemCount < 0) throw new IllegalArgumentException("itemCount cannot be negative");
    }

    public boolean active() { return status != Status.CLOSED && status != Status.EXCEPTION; }

    /** Update manifest composition (item count and seal identifiers) while still a draft. */
    public Dispatch updateManifest(int count, List<String> seals, RecordMetadata changed) {
        requireState(Status.DRAFT);
        if (count < 0) throw new IllegalArgumentException("itemCount cannot be negative");
        return new Dispatch(id, manifestNumber, siteCode, route, assignedHandler, destinationCentre,
                examinationContext, tripId, vehicleId, driverId, count, seals, status, dispatchedAt, receivedAt,
                reconciledAt, closureReason, changed);
    }

    /** Bind the optional carrying S166 trip/vehicle/driver soft references before sealing. */
    public Dispatch assignTrip(UUID trip, UUID vehicle, UUID driver, RecordMetadata changed) {
        requireState(Status.DRAFT, Status.SEALED);
        return new Dispatch(id, manifestNumber, siteCode, route, assignedHandler, destinationCentre,
                examinationContext, trip, vehicle, driver, itemCount, sealIds, status, dispatchedAt, receivedAt,
                reconciledAt, closureReason, changed);
    }

    public Dispatch seal(RecordMetadata changed) {
        requireState(Status.DRAFT);
        if (itemCount <= 0) throw new IllegalStateException("A dispatch cannot be sealed with no items");
        if (sealIds.isEmpty()) throw new IllegalStateException("At least one seal identifier is required to seal");
        return copy(Status.SEALED, dispatchedAt, receivedAt, reconciledAt, closureReason, changed);
    }

    public Dispatch dispatch(Instant at, RecordMetadata changed) {
        requireState(Status.SEALED);
        return copy(Status.DISPATCHED, Objects.requireNonNull(at, "dispatchedAt"), receivedAt, reconciledAt,
                closureReason, changed);
    }

    public Dispatch inTransit(RecordMetadata changed) {
        requireState(Status.DISPATCHED);
        return copy(Status.IN_TRANSIT, dispatchedAt, receivedAt, reconciledAt, closureReason, changed);
    }

    public Dispatch received(Instant at, RecordMetadata changed) {
        requireState(Status.DISPATCHED, Status.IN_TRANSIT);
        return copy(Status.RECEIVED, dispatchedAt, Objects.requireNonNull(at, "receivedAt"), reconciledAt,
                closureReason, changed);
    }

    public Dispatch onReturnLeg(RecordMetadata changed) {
        requireState(Status.RECEIVED);
        return copy(Status.RETURNED, dispatchedAt, receivedAt, reconciledAt, closureReason, changed);
    }

    public Dispatch reconciled(Instant at, RecordMetadata changed) {
        requireState(Status.RECEIVED, Status.RETURNED);
        return copy(Status.RECONCILED, dispatchedAt, receivedAt, Objects.requireNonNull(at, "reconciledAt"),
                closureReason, changed);
    }

    public Dispatch close(String reason, RecordMetadata changed) {
        requireState(Status.RECEIVED, Status.RETURNED, Status.RECONCILED);
        return copy(Status.CLOSED, dispatchedAt, receivedAt, reconciledAt, require(reason, "closure reason"), changed);
    }

    public Dispatch markException(RecordMetadata changed) {
        if (status == Status.CLOSED) throw new IllegalStateException("A closed dispatch cannot enter exception");
        return copy(Status.EXCEPTION, dispatchedAt, receivedAt, reconciledAt, closureReason, changed);
    }

    public Dispatch resolveException(Status restoreTo, RecordMetadata changed) {
        requireState(Status.EXCEPTION);
        if (restoreTo == Status.CLOSED || restoreTo == Status.EXCEPTION) {
            throw new IllegalArgumentException("Exception must resolve to an operational status");
        }
        return copy(restoreTo, dispatchedAt, receivedAt, reconciledAt, closureReason, changed);
    }

    private Dispatch copy(Status next, Instant dispatched, Instant received, Instant reconciled, String closure,
            RecordMetadata changed) {
        return new Dispatch(id, manifestNumber, siteCode, route, assignedHandler, destinationCentre,
                examinationContext, tripId, vehicleId, driverId, itemCount, sealIds, next, dispatched, received,
                reconciled, closure, changed);
    }

    private void requireState(Status... allowed) {
        for (Status s : allowed) if (s == status) return;
        throw new IllegalStateException("transition not allowed from " + status);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
