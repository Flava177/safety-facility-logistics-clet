package gh.edu.clet.sfl.facilities.eventlogistics.application.ports;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import java.util.Objects;

/**
 * What an owning system said about one resource request, in S173's vocabulary.
 *
 * <p>Every owning-system adapter answers in this one shape so the routing service has one place that
 * turns an answer into a status change and an audit record, whichever of S159, S153, S169 or S172 gave
 * it.
 *
 * @param reference the owning system's record id, by value
 * @param parentReference the enclosing record where there is one - the S159 booking of an allocation
 * @param detail what a coordinator reads: "BK-MAIN-000042 awaiting approval", "WO-MAIN-000007"
 * @param competingCommitment set only for a conflict - the owning system's own naming of what holds it
 */
public record GatewayOutcome(ResourceRequestStatus status, String reference, String parentReference,
        String detail, String competingCommitment) {

    public GatewayOutcome {
        Objects.requireNonNull(status, "status is required");
    }

    public static GatewayOutcome of(ResourceRequestStatus status, String reference, String detail) {
        return new GatewayOutcome(status, reference, null, detail, null);
    }

    public static GatewayOutcome conflict(String competingCommitment) {
        return new GatewayOutcome(ResourceRequestStatus.CONFLICTED, null, null,
                "Refused by the owning system: " + competingCommitment, competingCommitment);
    }

    /** The owning system cannot be reached as a system; coordinate off-system (S173-02 error state). */
    public static GatewayOutcome unavailable(String why) {
        return new GatewayOutcome(ResourceRequestStatus.MANUAL_COORDINATION, null, null, why, null);
    }
}
