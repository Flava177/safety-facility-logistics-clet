package gh.edu.clet.sfl.safetysecurity.visitor.domain.model;

/** Why a visitor is on site - SRS-SFL-S160-01. Drives whether host approval is required. */
public enum VisitPurpose {

    MEETING(true),
    INTERVIEW(true),
    EVENT(true),
    DELIVERY(false),
    MAINTENANCE_CONTRACTOR(false),
    OTHER(true);

    private final boolean requiresHostApproval;

    VisitPurpose(boolean requiresHostApproval) {
        this.requiresHostApproval = requiresHostApproval;
    }

    /**
     * Whether a visit for this purpose needs a host's decision before it is confirmed.
     *
     * <p>Deliveries and contractor visits are logged and badged but nobody is "receiving" them the
     * way a meeting has a host who must agree to it, so they go straight from pre-registration to
     * confirmed - the same "no separate APPROVED state" reasoning {@code VisitStatus} documents.
     */
    public boolean requiresHostApproval() {
        return requiresHostApproval;
    }
}
