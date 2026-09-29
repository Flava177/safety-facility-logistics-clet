package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A scope or cost variation against the approved baseline - SRS-SFL-S176-03.
 *
 * <h2>Held is a flag, not a state</h2>
 *
 * A variation that needs escalated approval stays SUBMITTED with {@code escalationRequired} set,
 * rather than moving to a HELD state. It is still a submitted variation awaiting a decision; what has
 * changed is <em>whose</em> decision. It leaves SUBMITTED only by an escalated approval or a
 * rejection, and V21's {@code ck_construction_variations_escalated} refuses an APPROVED row that
 * needed escalation and has no escalated approver.
 *
 * <h2>Only APPROVED touches the budget</h2>
 *
 * "A variation cannot be applied to the budget until its own approval is recorded." The current
 * budget is computed from APPROVED variations alone, so a SUBMITTED one - however large, however long
 * it has waited - is visible and does nothing.
 *
 * @param costDelta signed: positive adds cost, negative removes it. Never zero - a variation with no
 *        cost effect and no scope change is not a variation.
 * @param cumulativePercent the approved cumulative variation as a percentage of the approved baseline,
 *        after this one, stamped when it was assessed for approval
 */
public record VariationOrder(
        UUID id,
        String variationReference,
        UUID projectId,
        String siteCode,
        String changeDescription,
        BigDecimal costDelta,
        String currency,
        String justification,
        Status status,
        boolean escalationRequired,
        String escalationReason,
        String submittedBy,
        Instant submittedAt,
        String decidedBy,
        Instant decidedAt,
        String decisionNote,
        String escalatedApprovedBy,
        Instant escalatedApprovedAt,
        BigDecimal cumulativePercent,
        RecordMetadata metadata) {

    public enum Status {
        SUBMITTED,
        APPROVED,
        REJECTED
    }

    public VariationOrder {
        Objects.requireNonNull(id, "id is required");
        variationReference = EstateCodes.normalize(variationReference);
        Objects.requireNonNull(projectId, "projectId is required");
        siteCode = EstateCodes.normalize(siteCode);
        if (changeDescription == null || changeDescription.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A variation must record the change.");
        }
        changeDescription = changeDescription.strip();
        if (costDelta == null || costDelta.signum() == 0) {
            throw new FacilitiesException.ValidationFailedException(
                    "A variation must record a cost delta other than zero.");
        }
        costDelta = costDelta.setScale(2, RoundingMode.HALF_UP);
        EstateCodes.require(currency, "currency");
        currency = currency.strip().toUpperCase(Locale.ROOT);
        if (justification == null || justification.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A variation must record its justification.");
        }
        justification = justification.strip();
        Objects.requireNonNull(status, "status is required");
        escalationReason = EstateCodes.blankToNull(escalationReason);
        EstateCodes.require(submittedBy, "submittedBy");
        Objects.requireNonNull(submittedAt, "submittedAt is required");
        decisionNote = EstateCodes.blankToNull(decisionNote);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static VariationOrder submit(UUID id, String reference, ConstructionProject project, String change,
            BigDecimal costDelta, String currency, String justification, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new VariationOrder(id, reference, project.id(), project.siteCode(), change, costDelta,
                currency == null || currency.isBlank() ? project.currency() : currency, justification,
                Status.SUBMITTED, false, null, actorId, at, null, null, null, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Whether it is waiting for somebody holding the escalated authority. */
    public boolean awaitingEscalation() {
        return status == Status.SUBMITTED && escalationRequired;
    }

    /** Held for escalated approval. Idempotent: holding a held variation changes nothing. */
    public VariationOrder holdForEscalation(String reason, BigDecimal percent, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        requireSubmitted();
        if (escalationRequired) {
            return this;
        }
        return copy(Status.SUBMITTED, true, reason, decidedBy, decidedAt, decisionNote, escalatedApprovedBy,
                escalatedApprovedAt, percent, actorId, at, channel, correlationId);
    }

    /** An ordinary approval. Refused for a held variation - that needs {@link #approveEscalated}. */
    public VariationOrder approve(String approverId, String note, BigDecimal percent, Instant at,
            SourceChannel channel, String correlationId) {
        requireSubmitted();
        if (escalationRequired) {
            throw new ConstructionRefusal(FacilitiesErrorCode.VARIATION_ESCALATION_REQUIRED, escalationReason);
        }
        return copy(Status.APPROVED, false, null, approverId, at, note, null, null, percent, approverId, at, channel,
                correlationId);
    }

    /** The escalated sign-off. Records the approver as both the decider and the escalated approver. */
    public VariationOrder approveEscalated(String approverId, String note, BigDecimal percent, Instant at,
            SourceChannel channel, String correlationId) {
        requireSubmitted();
        return copy(Status.APPROVED, escalationRequired, escalationReason, approverId, at, note, approverId, at,
                percent, approverId, at, channel, correlationId);
    }

    public VariationOrder reject(String approverId, String reason, Instant at, SourceChannel channel,
            String correlationId) {
        requireSubmitted();
        if (reason == null || reason.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A rejected variation must carry a reason.");
        }
        return copy(Status.REJECTED, escalationRequired, escalationReason, approverId, at, reason, null, null,
                cumulativePercent, approverId, at, channel, correlationId);
    }

    private void requireSubmitted() {
        if (status != Status.SUBMITTED) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Variation " + variationReference + " has already been decided (" + status + ").");
        }
    }

    private VariationOrder copy(Status next, boolean escalation, String reason, String decider, Instant decided,
            String note, String escalatedBy, Instant escalatedAt, BigDecimal percent, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new VariationOrder(id, variationReference, projectId, siteCode, changeDescription, costDelta, currency,
                justification, next, escalation, reason, submittedBy, submittedAt, decider, decided, note,
                escalatedBy, escalatedAt, percent, metadata.modifiedBy(actorId, at, channel, correlationId));
    }
}
