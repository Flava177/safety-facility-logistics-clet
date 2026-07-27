package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

/**
 * A registered mailroom / courier item (inbound or outbound). Confidential and examination materials
 * are flagged for chain-of-custody handling. The lifecycle advances only through the explicit
 * transitions below and closes only on a clean terminal outcome.
 */
public record CourierItem(UUID id, String itemNumber, SiteCode siteCode, Direction direction, Type itemType,
        Sensitivity sensitivity, boolean chainOfCustodyRequired, String origin, String destination, String sender,
        String recipient, String assignedHandler, Status status, String acknowledgedBy, Instant acknowledgedAt,
        UUID acknowledgementEvidenceId, String distributionReference, String misrouteReason, boolean undelivered,
        String exceptionReason, RecordMetadata metadata) {

    public enum Direction { INBOUND, OUTBOUND }
    public enum Type { CONFIDENTIAL_CORRESPONDENCE, CERTIFICATE, SEALED_MATERIAL, EXAMINATION_PAPER, SEALED_BAG,
        EXAMINATION_DEVICE, ORDINARY_MAIL }
    public enum Sensitivity { ORDINARY, CONFIDENTIAL, SECRET }
    public enum Status { RECEIVED, STAGED, DISPATCHED, IN_TRANSIT, DELIVERED, RETURNED, EXCEPTION, CLOSED }

    private static final EnumSet<Type> CUSTODY_TYPES = EnumSet.of(Type.CONFIDENTIAL_CORRESPONDENCE, Type.CERTIFICATE,
            Type.SEALED_MATERIAL, Type.EXAMINATION_PAPER, Type.SEALED_BAG, Type.EXAMINATION_DEVICE);

    public CourierItem {
        Objects.requireNonNull(id); Objects.requireNonNull(siteCode); Objects.requireNonNull(direction);
        Objects.requireNonNull(itemType); Objects.requireNonNull(sensitivity); Objects.requireNonNull(status);
        Objects.requireNonNull(metadata);
        itemNumber = require(itemNumber, "itemNumber");
        origin = require(origin, "origin");
        destination = require(destination, "destination");
    }

    /** Confidential/secret sensitivity or a sealed/examination/certificate type mandates chain-of-custody. */
    public static boolean custodyRequired(Type type, Sensitivity sensitivity) {
        return sensitivity == Sensitivity.CONFIDENTIAL || sensitivity == Sensitivity.SECRET
                || CUSTODY_TYPES.contains(type);
    }

    public boolean active() { return status != Status.CLOSED && status != Status.EXCEPTION; }

    public CourierItem stage(RecordMetadata changed) {
        requireState(Status.RECEIVED);
        return copy(Status.STAGED, assignedHandler, acknowledgedBy, acknowledgedAt, acknowledgementEvidenceId,
                distributionReference, misrouteReason, undelivered, exceptionReason, changed);
    }

    public CourierItem dispatched(RecordMetadata changed) {
        if (direction != Direction.OUTBOUND) throw new IllegalStateException("Only outbound items are dispatched");
        requireState(Status.RECEIVED, Status.STAGED);
        return copy(Status.DISPATCHED, assignedHandler, acknowledgedBy, acknowledgedAt, acknowledgementEvidenceId,
                distributionReference, misrouteReason, undelivered, exceptionReason, changed);
    }

    public CourierItem inTransit(RecordMetadata changed) {
        requireState(Status.DISPATCHED);
        return copy(Status.IN_TRANSIT, assignedHandler, acknowledgedBy, acknowledgedAt, acknowledgementEvidenceId,
                distributionReference, misrouteReason, undelivered, exceptionReason, changed);
    }

    public CourierItem delivered(RecordMetadata changed) {
        requireState(Status.DISPATCHED, Status.IN_TRANSIT);
        return copy(Status.DELIVERED, assignedHandler, acknowledgedBy, acknowledgedAt, acknowledgementEvidenceId,
                distributionReference, misrouteReason, undelivered, exceptionReason, changed);
    }

    public CourierItem returnedToOrigin(RecordMetadata changed) {
        requireState(Status.DELIVERED);
        return copy(Status.RETURNED, assignedHandler, acknowledgedBy, acknowledgedAt, acknowledgementEvidenceId,
                distributionReference, misrouteReason, undelivered, exceptionReason, changed);
    }

    /** Inbound distribution: acknowledged (signature or scan) delivery that closes the inbound leg. */
    public CourierItem distribute(String acknowledgedByActor, Instant at, UUID evidenceId, String reference,
            RecordMetadata changed) {
        if (direction != Direction.INBOUND) throw new IllegalStateException("Only inbound items are distributed");
        requireState(Status.RECEIVED, Status.STAGED);
        require(acknowledgedByActor, "acknowledgedBy");
        if (evidenceId == null && (reference == null || reference.isBlank())) {
            throw new IllegalArgumentException("A distribution acknowledgement (signature or scan) is required");
        }
        Objects.requireNonNull(at, "acknowledgedAt");
        return copy(Status.DELIVERED, assignedHandler, acknowledgedByActor.strip(), at, evidenceId,
                trim(reference), misrouteReason, false, null, changed);
    }

    public CourierItem close(RecordMetadata changed) {
        requireState(Status.DELIVERED, Status.RETURNED);
        return copy(Status.CLOSED, assignedHandler, acknowledgedBy, acknowledgedAt, acknowledgementEvidenceId,
                distributionReference, misrouteReason, undelivered, exceptionReason, changed);
    }

    public CourierItem markException(String reason, RecordMetadata changed) {
        if (status == Status.CLOSED) throw new IllegalStateException("A closed item cannot enter exception");
        return copy(Status.EXCEPTION, assignedHandler, acknowledgedBy, acknowledgedAt, acknowledgementEvidenceId,
                distributionReference, misrouteReason, undelivered, require(reason, "exception reason"), changed);
    }

    public CourierItem markUndelivered(String reason, RecordMetadata changed) {
        if (status == Status.CLOSED || status == Status.DELIVERED || status == Status.RETURNED) {
            throw new IllegalStateException("Only an open item can be flagged undelivered");
        }
        return copy(Status.EXCEPTION, assignedHandler, acknowledgedBy, acknowledgedAt, acknowledgementEvidenceId,
                distributionReference, misrouteReason, true, require(reason, "undelivered reason"), changed);
    }

    public CourierItem reroute(String reason, String handler, RecordMetadata changed) {
        if (!active()) throw new IllegalStateException("Only an active item can be rerouted");
        return copy(status, handler == null || handler.isBlank() ? assignedHandler : handler.strip(), acknowledgedBy,
                acknowledgedAt, acknowledgementEvidenceId, distributionReference, require(reason, "reroute reason"),
                undelivered, exceptionReason, changed);
    }

    private CourierItem copy(Status next, String handler, String ackBy, Instant ackAt, UUID ackEvidence,
            String distribution, String misroute, boolean undeliveredFlag, String exception, RecordMetadata changed) {
        return new CourierItem(id, itemNumber, siteCode, direction, itemType, sensitivity, chainOfCustodyRequired,
                origin, destination, sender, recipient, handler, next, ackBy, ackAt, ackEvidence, distribution,
                misroute, undeliveredFlag, exception, changed);
    }

    private void requireState(Status... allowed) {
        for (Status s : allowed) if (s == status) return;
        throw new IllegalStateException("transition not allowed from " + status);
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
