package gh.edu.clet.sfl.facilities.eventlogistics.domain;

/**
 * What an event needs, as a structured type rather than a line of free text - SRS-SFL-S173-01
 * ("decompose the hand-off into specific resource requests ... rather than working from free text").
 *
 * <p>Each type is routed to exactly one owning system, fixed here, because the routing is the SRS's
 * decision (S173-02) and not a coordinator's. Two of those routings deserve a second look and are
 * reported, not silently resolved:
 * <ul>
 *   <li>{@link #SECURITY} goes to S159 as a bookable resource because S173-02 says "staging/AV/security
 *       resources from S159". Security <em>staffing</em> is an SSEMP concern in the Phase 1 platform;
 *       what S159 can hold is a named, bookable security team or post. See the gap report.</li>
 *   <li>{@link #CATERING} goes to S172, which is Phase 3. Until it exists every catering request is a
 *       manual coordination item (S173-02 validation).</li>
 * </ul>
 */
public enum EventResourceType {

    /** The space itself. One per set-up task: it is the booking every S159 resource hangs off. */
    VENUE(OwningSystem.S159, false),
    AV(OwningSystem.S159, true),
    STAGING(OwningSystem.S159, true),
    SECURITY(OwningSystem.S159, true),
    SIGNAGE(OwningSystem.S159, true),
    PRE_EVENT_MAINTENANCE(OwningSystem.S153, false),
    CLEANING(OwningSystem.S169, false),
    CATERING(OwningSystem.S172, false);

    private final OwningSystem owningSystem;
    private final boolean bookableResource;

    EventResourceType(OwningSystem owningSystem, boolean bookableResource) {
        this.owningSystem = owningSystem;
        this.bookableResource = bookableResource;
    }

    public OwningSystem owningSystem() {
        return owningSystem;
    }

    /**
     * Whether the request is fulfilled by allocating an S159 bookable resource onto the venue booking.
     * Such a request must name the resource; "some AV" is the free text S173-01 rules out.
     */
    public boolean isBookableResource() {
        return bookableResource;
    }
}
