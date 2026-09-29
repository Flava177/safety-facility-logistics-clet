package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * S176's projection of one S164 Permit-to-Work, as S164's events have described it.
 *
 * <h2>Why a projection, and why it fails closed</h2>
 *
 * S164 lives in SSEMP, another deployable. S176 cannot call it, and should not hold a copy of the
 * permit anybody can edit - so it holds only what S164 has published: issued, suspended, extended,
 * closed. A permit is <em>current</em> only if the last thing S164 said about it was that it is issued,
 * and now is inside its validity window.
 *
 * <p>S164 is not built, so nothing publishes those events and this projection is empty. Every project
 * whose work type requires a permit is therefore refused the start with {@code PROJECT_PERMIT_MISSING}.
 * That is the correct behaviour for this release, not a defect: the alternative - letting a project
 * manager type in a permit number and trusting it - is the paper permit S164 exists to replace.
 *
 * <h2>Ordering</h2>
 *
 * Events are applied only if they are not older than the last one applied ({@code lastEventAt}), so a
 * redelivered "issued" cannot resurrect a permit S164 has since suspended. CLOSED is terminal whatever
 * arrives after it. A suspended permit is reissued only by a fresh {@code permit-issued} event - S164's
 * own rule (SRS-SFL-S164-03) that resumption needs a fresh approval, never an un-suspend.
 *
 * @param permitId S164's identifier, held by value; S164's id format is not assumed to be a UUID
 * @param workType the S164 permit type, matched against the project's work types
 * @param contractorReference the S176 contractor id or S133 vendor reference the permit names
 */
public record PermitRecord(
        UUID id,
        String permitId,
        String permitReference,
        String siteCode,
        String workType,
        Status status,
        Instant validFrom,
        Instant validTo,
        String contractorReference,
        String originReference,
        String lastEventType,
        Instant lastEventAt,
        UUID lastMessageId,
        RecordMetadata metadata) {

    public enum Status {
        ISSUED,
        SUSPENDED,
        CLOSED
    }

    public PermitRecord {
        Objects.requireNonNull(id, "id is required");
        EstateCodes.require(permitId, "permitId");
        permitId = permitId.strip();
        permitReference = EstateCodes.blankToNull(permitReference);
        siteCode = EstateCodes.normalize(siteCode);
        workType = workType == null || workType.isBlank() ? null : EstateCodes.normalize(workType);
        Objects.requireNonNull(status, "status is required");
        contractorReference = EstateCodes.blankToNull(contractorReference);
        originReference = EstateCodes.blankToNull(originReference);
        EstateCodes.require(lastEventType, "lastEventType");
        Objects.requireNonNull(lastEventAt, "lastEventAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    /** Issued, and now is inside the validity window S164 last stated. */
    public boolean isCurrentAt(Instant at) {
        return status == Status.ISSUED && validFrom != null && validTo != null
                && !at.isBefore(validFrom) && at.isBefore(validTo);
    }

    /**
     * Applies one S164 notice, returning the projection as it now stands - or this record unchanged,
     * when the notice is stale or the permit is already closed.
     */
    public PermitRecord apply(PermitNotice notice, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        if (status == Status.CLOSED || notice.occurredAt().isBefore(lastEventAt)) {
            return this;
        }
        Status next = switch (notice.kind()) {
            case ISSUED -> Status.ISSUED;
            case SUSPENDED -> Status.SUSPENDED;
            case EXTENDED -> status;
            case CLOSED -> Status.CLOSED;
        };
        Instant from = notice.validFrom() != null && notice.kind() == PermitNotice.Kind.ISSUED
                ? notice.validFrom() : validFrom;
        Instant to = notice.validTo() != null
                && (notice.kind() == PermitNotice.Kind.ISSUED || notice.kind() == PermitNotice.Kind.EXTENDED)
                ? notice.validTo() : validTo;
        return new PermitRecord(id, permitId, notice.permitReference() == null ? permitReference
                : notice.permitReference(), siteCode, notice.workType() == null ? workType : notice.workType(),
                next, from, to, notice.contractorReference() == null ? contractorReference
                        : notice.contractorReference(),
                notice.originReference() == null ? originReference : notice.originReference(), notice.eventType(),
                notice.occurredAt(), notice.messageId(), metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** The first notice S176 has seen about a permit, whichever kind it is. */
    public static PermitRecord first(PermitNotice notice, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        Status status = switch (notice.kind()) {
            case ISSUED, EXTENDED -> Status.ISSUED;
            case SUSPENDED -> Status.SUSPENDED;
            case CLOSED -> Status.CLOSED;
        };
        // An extension of a permit never seen issued is treated as an issue: S164 would not extend a
        // permit it had not issued, and the validity window it states is the one in force.
        return new PermitRecord(UUID.randomUUID(), notice.permitId(), notice.permitReference(), notice.siteCode(),
                notice.workType(), status, notice.validFrom(), notice.validTo(), notice.contractorReference(),
                notice.originReference(), notice.eventType(), notice.occurredAt(), notice.messageId(),
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }
}
