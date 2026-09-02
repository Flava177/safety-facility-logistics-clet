package gh.edu.clet.sfl.safetysecurity.visitor.domain.model;

import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The life of a visit - SRS-SFL-S160-01, following the same shape as {@code BookingStatus} in the
 * facilities module.
 *
 * <h2>Why there is no separate APPROVED state</h2>
 *
 * Approval is an <em>event</em> - recorded as a {@link VisitorApproval}, with who and why - not a
 * state a visit sits in. A visit that has been approved is confirmed; there is nothing further to do
 * to it. Visits that need no host approval (see {@link VisitPurpose#requiresHostApproval()}) go
 * {@code PRE_REGISTERED -> CONFIRMED} directly, and the absence of a {@code VisitorApproval} record
 * is what says so.
 *
 * <h2>NO_SHOW is terminal and reached only by a scheduled sweep</h2>
 *
 * Not modelled by hand-set transitions in this slice - no sweep exists yet - but the transition is
 * declared here so the state machine already has a home for it when one is added.
 */
public enum VisitStatus {

    /** Registered and awaiting either a host decision or, where none is needed, confirmation. */
    PRE_REGISTERED,
    /** Approved, or needed no approval. */
    CONFIRMED,
    /** The visitor has arrived and been badged in. */
    CHECKED_IN,
    /** The visitor has left. Terminal. */
    CHECKED_OUT,
    /** Refused by a host, with a reason. Terminal. */
    REJECTED,
    /** Withdrawn before arrival, by the requester or reception. Terminal. */
    CANCELLED,
    /** Confirmed, never arrived. Terminal. */
    NO_SHOW;

    private static final Map<VisitStatus, Set<VisitStatus>> ALLOWED = Map.of(
            PRE_REGISTERED, EnumSet.of(CONFIRMED, REJECTED, CANCELLED),
            CONFIRMED, EnumSet.of(CHECKED_IN, CANCELLED, NO_SHOW),
            CHECKED_IN, EnumSet.of(CHECKED_OUT),
            CHECKED_OUT, EnumSet.noneOf(VisitStatus.class),
            REJECTED, EnumSet.noneOf(VisitStatus.class),
            CANCELLED, EnumSet.noneOf(VisitStatus.class),
            NO_SHOW, EnumSet.noneOf(VisitStatus.class));

    /** {@code true} while the visit still holds a badge/access slot for the visitor. */
    public boolean holdsTheSlot() {
        return this == PRE_REGISTERED || this == CONFIRMED || this == CHECKED_IN;
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    public boolean canTransitionTo(VisitStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public VisitStatus transitionTo(VisitStatus target) {
        if (!canTransitionTo(target)) {
            throw new VisitorException(VisitorErrorCode.VISITOR_INVALID_STATE_TRANSITION,
                    Map.of("from", name(), "to", target == null ? "" : target.name()));
        }
        return target;
    }
}
