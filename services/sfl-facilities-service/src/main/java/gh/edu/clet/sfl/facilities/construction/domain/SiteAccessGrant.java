package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A contractor's site-access request and what became of it - SRS-SFL-S176-02.
 *
 * <h2>What this record is, and what it is not</h2>
 *
 * The SRS routes contractor site access through Access Control (S160a) and Visitor Management (S160).
 * Both are SSEMP systems in another deployable, and neither consumes anything from S176 yet. So this
 * is S176's own record of the request - checked against compliance here, refused here when compliance
 * has lapsed, suspended here when it lapses later - and the request and every suspension are
 * published to the outbox for S160a to act on when it can.
 *
 * <p>{@link Enforcement#RECORDED_NOT_ENFORCED} is on every row this release writes, and it means
 * exactly what it says: a suspended grant here has not closed a turnstile anywhere. The S176 runbook
 * and gap report both say so, because a suspension that looks enforced and is not is worse than none.
 *
 * <h2>Why a suspended grant is never resumed</h2>
 *
 * Renewing the insurance does not bring a grant back. The suspension was a decision made on a fact
 * that has since changed, and whether the contractor should be back on site is a fresh question -
 * answered by a fresh request, which is checked afresh. The same rule S164 applies to its permits.
 */
public record SiteAccessGrant(
        UUID id,
        UUID contractorId,
        UUID projectId,
        String siteCode,
        String accessScope,
        Instant validFrom,
        Instant validTo,
        Status status,
        String refusalReason,
        Instant suspendedAt,
        String suspensionReason,
        Enforcement enforcement,
        String dispatchProvider,
        String requestedBy,
        Instant requestedAt,
        RecordMetadata metadata) {

    public enum Status {
        /** Granted locally, and published for S160a. */
        ACTIVE,
        /** Automatically suspended when compliance lapsed. Terminal. */
        SUSPENDED,
        /** Refused at request because compliance had already lapsed. Terminal. */
        REFUSED
    }

    /** Whether the grant has reached an access-control system that enforces it. */
    public enum Enforcement {
        /** Recorded here and published; no consumer in S160a/S160 yet. */
        RECORDED_NOT_ENFORCED,
        /** Confirmed by the access-control system. Not reachable in this release. */
        ENFORCED
    }

    public SiteAccessGrant {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(contractorId, "contractorId is required");
        siteCode = EstateCodes.normalize(siteCode);
        EstateCodes.require(accessScope, "accessScope");
        accessScope = accessScope.strip();
        Objects.requireNonNull(validFrom, "validFrom is required");
        Objects.requireNonNull(validTo, "validTo is required");
        if (!validTo.isAfter(validFrom)) {
            throw new FacilitiesException.ValidationFailedException("An access window must end after it starts.");
        }
        Objects.requireNonNull(status, "status is required");
        refusalReason = EstateCodes.blankToNull(refusalReason);
        suspensionReason = EstateCodes.blankToNull(suspensionReason);
        enforcement = enforcement == null ? Enforcement.RECORDED_NOT_ENFORCED : enforcement;
        EstateCodes.require(requestedBy, "requestedBy");
        Objects.requireNonNull(requestedAt, "requestedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (status == Status.REFUSED && refusalReason == null) {
            throw new IllegalArgumentException("a refused grant carries its reason");
        }
        if (status == Status.SUSPENDED && (suspensionReason == null || suspendedAt == null)) {
            throw new IllegalArgumentException("a suspended grant carries its reason and time");
        }
    }

    public static SiteAccessGrant grant(UUID id, Contractor contractor, UUID projectId, String accessScope,
            Instant validFrom, Instant validTo, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new SiteAccessGrant(id, contractor.id(), projectId, contractor.siteCode(), accessScope, validFrom,
                validTo, Status.ACTIVE, null, null, null, Enforcement.RECORDED_NOT_ENFORCED, null, actorId, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Kept, with its reason, because "refused with reason shown" needs the reason to exist afterwards. */
    public static SiteAccessGrant refuse(UUID id, Contractor contractor, UUID projectId, String accessScope,
            Instant validFrom, Instant validTo, String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new SiteAccessGrant(id, contractor.id(), projectId, contractor.siteCode(), accessScope, validFrom,
                validTo, Status.REFUSED, reason, null, null, Enforcement.RECORDED_NOT_ENFORCED, null, actorId, at,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public SiteAccessGrant dispatchedVia(String provider, Enforcement reached) {
        return new SiteAccessGrant(id, contractorId, projectId, siteCode, accessScope, validFrom, validTo, status,
                refusalReason, suspendedAt, suspensionReason, reached, provider, requestedBy, requestedAt, metadata);
    }

    public SiteAccessGrant suspend(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (status != Status.ACTIVE) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only an active site-access grant can be suspended; this one is " + status + ".");
        }
        EstateCodes.require(reason, "reason");
        return new SiteAccessGrant(id, contractorId, projectId, siteCode, accessScope, validFrom, validTo,
                Status.SUSPENDED, refusalReason, at, reason, enforcement, dispatchProvider, requestedBy, requestedAt,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Active and inside its window. A grant whose window has closed is simply not in force. */
    public boolean isInForceAt(Instant at) {
        return status == Status.ACTIVE && !at.isBefore(validFrom) && at.isBefore(validTo);
    }
}
