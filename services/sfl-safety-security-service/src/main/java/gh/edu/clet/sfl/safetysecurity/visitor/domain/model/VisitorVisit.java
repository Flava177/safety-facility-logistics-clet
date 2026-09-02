package gh.edu.clet.sfl.safetysecurity.visitor.domain.model;

import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A visitor's visit to a site - SRS-SFL-S160-01. Pre-registration, host approval, badge/access-zone
 * assignment, check-in, check-out - see {@code docs/.../SFL_Phase1_System_Architecture_...md} §11.3.
 *
 * <h2>Why pre-registration already holds a slot</h2>
 *
 * {@link VisitStatus#PRE_REGISTERED} occupies a badge/host slot for its expected window exactly as
 * {@link VisitStatus#CONFIRMED} does - the same "holding, not merely asked-for" design {@code Booking}
 * uses for room requests, applied to a visitor slot instead of a room.
 *
 * @param watchlistFlagged resolved at pre-registration time from {@code WatchlistCheckPort} and
 *        stored, so a later change to watchlist data does not retrospectively alter a decision
 *        already made. If {@code true}, {@link #confirm} requires a non-null override reason.
 * @param approvalRequired resolved at pre-registration time from {@link VisitPurpose#requiresHostApproval()}
 *        and stored, for the same reason.
 */
public record VisitorVisit(
        UUID id,
        String siteCode,
        String visitorName,
        String visitorOrganization,
        String visitorContact,
        String hostId,
        String hostName,
        VisitPurpose purpose,
        VisitStatus status,
        Instant expectedArrival,
        Instant expectedDeparture,
        boolean approvalRequired,
        UUID approvalId,
        boolean watchlistFlagged,
        String watchlistOverrideReason,
        String badgeNumber,
        List<String> accessZones,
        Instant checkedInAt,
        Instant checkedOutAt,
        String closureReason,
        RecordMetadata metadata) {

    public VisitorVisit {
        Objects.requireNonNull(id, "id is required");
        siteCode = normalizeSite(siteCode);
        require(visitorName, "visitorName");
        visitorName = visitorName.strip();
        visitorOrganization = blankToNull(visitorOrganization);
        visitorContact = blankToNull(visitorContact);
        require(hostId, "hostId");
        hostId = hostId.strip();
        hostName = blankToNull(hostName);
        Objects.requireNonNull(purpose, "purpose is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(expectedArrival, "expectedArrival is required");
        watchlistOverrideReason = blankToNull(watchlistOverrideReason);
        badgeNumber = blankToNull(badgeNumber);
        accessZones = accessZones == null ? List.of() : List.copyOf(accessZones);
        closureReason = blankToNull(closureReason);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    /** A newly registered visit. Already holding a slot - see {@link VisitStatus}. */
    public static VisitorVisit preRegister(UUID id, String siteCode, String visitorName,
            String visitorOrganization, String visitorContact, String hostId, String hostName,
            VisitPurpose purpose, Instant expectedArrival, Instant expectedDeparture, boolean watchlistFlagged,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new VisitorVisit(id, siteCode, visitorName, visitorOrganization, visitorContact, hostId,
                hostName, purpose, VisitStatus.PRE_REGISTERED, expectedArrival, expectedDeparture,
                purpose.requiresHostApproval(), null, watchlistFlagged, null, null, List.of(), null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Confirms the visit.
     *
     * <p>{@code approvalId} is null for a visit that needed none, mirroring {@code Booking.confirm}.
     * A watchlist match must carry an override reason to confirm through it - the domain invariant
     * that stands in for a real watchlist product in this build.
     */
    public VisitorVisit confirm(UUID approval, String overrideReason, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        if (approvalRequired && approval == null) {
            throw new VisitorException(VisitorErrorCode.VISITOR_UNAUTHORIZED_APPROVAL);
        }
        if (watchlistFlagged && (overrideReason == null || overrideReason.isBlank())) {
            throw new VisitorException(VisitorErrorCode.VISITOR_WATCHLIST_MATCH);
        }
        VisitStatus next = status.transitionTo(VisitStatus.CONFIRMED);
        return copy(next, approval, blankToNull(overrideReason), badgeNumber, accessZones, checkedInAt,
                checkedOutAt, closureReason, actorId, at, channel, correlationId);
    }

    /** Refused by the host. The reason is required and is what the requester will read. */
    public VisitorVisit reject(UUID approval, String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        require(reason, "reason");
        VisitStatus next = status.transitionTo(VisitStatus.REJECTED);
        return copy(next, approval, watchlistOverrideReason, badgeNumber, accessZones, checkedInAt, checkedOutAt,
                reason.strip(), actorId, at, channel, correlationId);
    }

    /**
     * Assigns a badge and access zones. Not itself a status transition - refused unless the visit is
     * {@link VisitStatus#CONFIRMED}, since a badge means nothing for a visit not yet decided or
     * already over.
     */
    public VisitorVisit assignBadge(String badge, List<String> zones, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        if (status != VisitStatus.CONFIRMED) {
            throw new VisitorException(VisitorErrorCode.VISITOR_INVALID_STATE_TRANSITION,
                    java.util.Map.of("reason", "A badge can only be assigned to a confirmed visit."));
        }
        require(badge, "badgeNumber");
        return copy(status, approvalId, watchlistOverrideReason, badge.strip(), zones, checkedInAt, checkedOutAt,
                closureReason, actorId, at, channel, correlationId);
    }

    /**
     * The visitor has arrived. Requires a badge to already be assigned.
     *
     * <p>The state-transition check runs before the badge check, deliberately: a visit that is not
     * {@code CONFIRMED} should report {@code VISITOR_INVALID_STATE_TRANSITION}, not {@code
     * VISITOR_BADGE_NOT_ASSIGNED} - a rejected or cancelled visit was never going to have a badge, and
     * that fact should not mask the more fundamental reason check-in is refused.
     */
    public VisitorVisit checkIn(String actorId, Instant at, SourceChannel channel, String correlationId) {
        VisitStatus next = status.transitionTo(VisitStatus.CHECKED_IN);
        if (badgeNumber == null) {
            throw new VisitorException(VisitorErrorCode.VISITOR_BADGE_NOT_ASSIGNED);
        }
        return copy(next, approvalId, watchlistOverrideReason, badgeNumber, accessZones, at, checkedOutAt,
                closureReason, actorId, at, channel, correlationId);
    }

    /** The visitor has left. */
    public VisitorVisit checkOut(String actorId, Instant at, SourceChannel channel, String correlationId) {
        VisitStatus next = status.transitionTo(VisitStatus.CHECKED_OUT);
        return copy(next, approvalId, watchlistOverrideReason, badgeNumber, accessZones, checkedInAt, at,
                closureReason, actorId, at, channel, correlationId);
    }

    /** Withdrawn before arrival. A reason is required whoever cancels and however late. */
    public VisitorVisit cancel(String reason, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        require(reason, "reason");
        VisitStatus next = status.transitionTo(VisitStatus.CANCELLED);
        return copy(next, approvalId, watchlistOverrideReason, badgeNumber, accessZones, checkedInAt, checkedOutAt,
                reason.strip(), actorId, at, channel, correlationId);
    }

    /**
     * Marks the visit as never arrived. Reached only from a scheduled sweep (not yet built - see the
     * implementation plan's deferred scope); package-visible reasoning mirrors {@code
     * Booking.markNoShow}: "they did not turn up" is an observation, not something a person asserts.
     */
    public VisitorVisit markNoShow(String actorId, Instant at, SourceChannel channel, String correlationId) {
        VisitStatus next = status.transitionTo(VisitStatus.NO_SHOW);
        return copy(next, approvalId, watchlistOverrideReason, badgeNumber, accessZones, checkedInAt, checkedOutAt,
                "No arrival recorded before the expected window closed.", actorId, at, channel, correlationId);
    }

    /** {@code true} when this visit currently holds a badge/access slot. */
    public boolean holdsTheSlot() {
        return status.holdsTheSlot();
    }

    /** {@code true} when the visitor is currently on site - the roll-call predicate. */
    public boolean isOnSite() {
        return status == VisitStatus.CHECKED_IN;
    }

    private VisitorVisit copy(VisitStatus newStatus, UUID newApprovalId, String newWatchlistOverrideReason,
            String newBadgeNumber, List<String> newAccessZones, Instant newCheckedInAt, Instant newCheckedOutAt,
            String newClosureReason, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new VisitorVisit(id, siteCode, visitorName, visitorOrganization, visitorContact, hostId, hostName,
                purpose, newStatus, expectedArrival, expectedDeparture, approvalRequired, newApprovalId,
                watchlistFlagged, newWatchlistOverrideReason, newBadgeNumber, newAccessZones, newCheckedInAt,
                newCheckedOutAt, newClosureReason, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private static String normalizeSite(String siteCode) {
        require(siteCode, "siteCode");
        return siteCode.strip().toUpperCase(Locale.ROOT);
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
