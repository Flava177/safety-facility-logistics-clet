package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One typed line of an event's needs, routed to the system that owns it - SRS-SFL-S173-01, -02.
 *
 * @param externalReference the owning system's record, by value: an S159 booking id (VENUE), an S159
 *        allocation id (AV / STAGING / SECURITY / SIGNAGE), an S153 work order id, an S169 reservation id
 * @param externalParentReference the S159 booking an allocation sits on, so it can be released
 * @param competingCommitment the owning system's own words for what holds the capacity - S169-04 and
 *        S159 both name it, and S173 shows it verbatim (S169 error state: "returned to S173 with the
 *        competing commitment named")
 * @param routeAttempts how many times the line has been sent to its owning system. Part of the
 *        idempotency key a re-route uses, so a request re-sent after a cancellation is a new request
 *        and not a replay of the cancelled one
 * @param manualAcceptedBy the named person who accepted responsibility for a manual coordination item
 * @param manualArrangedWith who, off-system, agreed to provide it - the caterer, the contractor
 * @param templateLineId the event-template line that pre-populated this request, when one did
 */
public record EventResourceRequest(
        UUID id,
        String siteCode,
        UUID setupTaskId,
        EventResourceType resourceType,
        OwningSystem owningSystem,
        String description,
        int quantity,
        UUID bookableResourceId,
        Instant neededFrom,
        Instant neededTo,
        ResourceRequestStatus status,
        String externalReference,
        String externalParentReference,
        String statusDetail,
        String competingCommitment,
        int routeAttempts,
        String manualAcceptedBy,
        String manualArrangedWith,
        Instant manualAcceptedAt,
        UUID templateLineId,
        String requestedBy,
        RecordMetadata metadata) {

    public EventResourceRequest {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(setupTaskId, "setupTaskId is required");
        Objects.requireNonNull(resourceType, "resourceType is required");
        Objects.requireNonNull(status, "status is required");
        // owningSystem is derived from resourceType, never chosen independently - a caller (there is
        // exactly one, EventResourceRequest itself) that passed anything else has a bug worth failing
        // loudly on, rather than a field this constructor silently overwrites.
        if (owningSystem != resourceType.owningSystem()) {
            throw new IllegalArgumentException(
                    "owningSystem must be " + resourceType.owningSystem() + " for a " + resourceType + " request.");
        }
        if (quantity < 1) {
            throw new FacilitiesException.ValidationFailedException("A resource request needs a quantity of at least 1.");
        }
        if (description == null || description.isBlank()) {
            throw new FacilitiesException.ValidationFailedException(
                    "Describe what is needed for the " + resourceType + " request.");
        }
        if (resourceType.isBookableResource() && bookableResourceId == null) {
            // S173-01: typed requests, not free text. An AV line must say which S159 resource.
            throw new FacilitiesException.ValidationFailedException(
                    "A " + resourceType + " request must name the S159 bookable resource it needs.");
        }
        Objects.requireNonNull(neededFrom, "neededFrom is required");
        Objects.requireNonNull(neededTo, "neededTo is required");
        if (!neededTo.isAfter(neededFrom)) {
            throw new FacilitiesException.ValidationFailedException("A resource is needed for a window that ends after it starts.");
        }
    }

    public static EventResourceRequest raise(UUID id, EventSetupTask task, EventResourceType type,
            String description, int quantity, UUID bookableResourceId, Instant neededFrom, Instant neededTo,
            UUID templateLineId, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new EventResourceRequest(id, task.siteCode(), task.id(), type, type.owningSystem(),
                description == null ? null : description.strip(), quantity, bookableResourceId,
                neededFrom == null ? task.details().startsAt() : neededFrom,
                neededTo == null ? task.details().endsAt() : neededTo, ResourceRequestStatus.REQUESTED, null, null,
                null, null, 0, null, null, null, templateLineId, actorId,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** The owning system's answer to a routing attempt. */
    public EventResourceRequest routed(ResourceRequestStatus outcome, String reference, String parentReference,
            String detail, String competing, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requireTransition(outcome);
        return new EventResourceRequest(id, siteCode, setupTaskId, resourceType, owningSystem, description, quantity,
                bookableResourceId, neededFrom, neededTo, outcome, reference, parentReference, detail, competing,
                routeAttempts + 1, null, null, null, templateLineId, requestedBy,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** A later change reported by the owning system - approval, completion, withdrawal. */
    public EventResourceRequest withStatus(ResourceRequestStatus next, String detail, String competing,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        requireTransition(next);
        return new EventResourceRequest(id, siteCode, setupTaskId, resourceType, owningSystem, description, quantity,
                bookableResourceId, neededFrom, neededTo, next, externalReference, externalParentReference, detail,
                competing, routeAttempts, manualAcceptedBy, manualArrangedWith, manualAcceptedAt, templateLineId,
                requestedBy, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * A named person takes responsibility for a manual coordination item (S173-04: "an explicitly
     * accepted manual-coordination status"). The status stays MANUAL_COORDINATION - see
     * {@link ResourceRequestStatus} for why it never becomes FULFILLED.
     */
    public EventResourceRequest acceptManualCoordination(String acceptedBy, String arrangedWith, Instant at,
            SourceChannel channel, String correlationId) {
        if (status != ResourceRequestStatus.MANUAL_COORDINATION) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only a manual coordination item can be accepted; this request is " + status + ".");
        }
        if (arrangedWith == null || arrangedWith.isBlank()) {
            throw new FacilitiesException.ValidationFailedException(
                    "Say who agreed to provide it off-system before accepting a manual coordination item.");
        }
        return new EventResourceRequest(id, siteCode, setupTaskId, resourceType, owningSystem, description, quantity,
                bookableResourceId, neededFrom, neededTo, status, externalReference, externalParentReference,
                statusDetail, competingCommitment, routeAttempts, acceptedBy, arrangedWith.strip(), at,
                templateLineId, requestedBy, metadata.modifiedBy(acceptedBy, at, channel, correlationId));
    }

    public EventResourceRequest cancel(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requireTransition(ResourceRequestStatus.CANCELLED);
        return new EventResourceRequest(id, siteCode, setupTaskId, resourceType, owningSystem, description, quantity,
                bookableResourceId, neededFrom, neededTo, ResourceRequestStatus.CANCELLED, externalReference,
                externalParentReference, reason, competingCommitment, routeAttempts, manualAcceptedBy,
                manualArrangedWith, manualAcceptedAt, templateLineId, requestedBy,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Moves a request back to REQUESTED after its event moved, dropping the stale commitment. */
    public EventResourceRequest requeue(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        requireTransition(ResourceRequestStatus.REQUESTED);
        return new EventResourceRequest(id, siteCode, setupTaskId, resourceType, owningSystem, description, quantity,
                bookableResourceId, neededFrom, neededTo, ResourceRequestStatus.REQUESTED, null, null, reason, null,
                routeAttempts, null, null, null, templateLineId, requestedBy,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isManualCoordinationAccepted() {
        return status == ResourceRequestStatus.MANUAL_COORDINATION && manualAcceptedBy != null;
    }

    /**
     * S173-04: before the event every request must be "confirmed or an explicitly accepted
     * manual-coordination status". Anything else still live - requested, conflicted, a manual item
     * nobody has accepted - is unresolved and is what escalates.
     */
    public boolean isUnresolved() {
        return switch (status) {
            case CONFIRMED, FULFILLED, CANCELLED -> false;
            case MANUAL_COORDINATION -> manualAcceptedBy == null;
            case REQUESTED, CONFLICTED -> true;
        };
    }

    /** Whether the line has been sent to its owning system and holds something there. */
    public boolean holdsExternalCommitment() {
        return externalReference != null && status.isLive() && status != ResourceRequestStatus.CONFLICTED;
    }

    private void requireTransition(ResourceRequestStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    resourceType + " request cannot move from " + status + " to " + target + ".");
        }
    }
}
