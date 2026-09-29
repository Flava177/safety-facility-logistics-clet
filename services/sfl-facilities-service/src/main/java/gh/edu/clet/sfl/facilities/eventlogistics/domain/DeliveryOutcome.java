package gh.edu.clet.sfl.facilities.eventlogistics.domain;

/**
 * What actually arrived against one resource request - SRS-SFL-S173-04 post-event reconciliation.
 */
public enum DeliveryOutcome {
    DELIVERED,
    /** Some of it - forty chairs of the eighty, one of the two security posts. */
    PARTIAL,
    NOT_DELIVERED;

    /** A gap is what feeds back into the category's event template. */
    public boolean isGap() {
        return this != DELIVERED;
    }
}
