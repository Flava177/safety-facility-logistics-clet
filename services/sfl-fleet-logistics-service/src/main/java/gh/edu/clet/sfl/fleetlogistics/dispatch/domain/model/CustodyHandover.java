package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, append-only chain-of-custody handover. Each hop records the transferring and receiving
 * custodian, the time and the seal state, and is never mutated after recording; the ordered set of
 * handovers reconstructs the custody chain for audit.
 */
public record CustodyHandover(UUID id, UUID dispatchId, SiteCode siteCode, CustodyHop hop, int sequenceNo,
        String transferringCustodian, String receivingCustodian, Instant occurredAt, SealState sealState,
        Integer verifiedCount, String notes, UUID evidenceId, String createdBy, Instant createdAt,
        SourceChannel sourceChannel, String correlationId) {

    public CustodyHandover {
        Objects.requireNonNull(id); Objects.requireNonNull(dispatchId); Objects.requireNonNull(siteCode);
        Objects.requireNonNull(hop); Objects.requireNonNull(occurredAt); Objects.requireNonNull(sealState);
        Objects.requireNonNull(sourceChannel); Objects.requireNonNull(createdAt);
        transferringCustodian = require(transferringCustodian, "transferringCustodian");
        receivingCustodian = require(receivingCustodian, "receivingCustodian");
        createdBy = require(createdBy, "createdBy");
        if (sequenceNo < 0) throw new IllegalArgumentException("sequenceNo cannot be negative");
        if (verifiedCount != null && verifiedCount < 0) throw new IllegalArgumentException("verifiedCount cannot be negative");
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.strip();
    }
}
