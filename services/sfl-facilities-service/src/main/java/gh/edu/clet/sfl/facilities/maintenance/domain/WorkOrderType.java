package gh.edu.clet.sfl.facilities.maintenance.domain;

/**
 * Why a work order exists.
 *
 * <p>The distinction is not cosmetic. A {@link #CORRECTIVE} order answers a reported fault and must
 * have one; a {@link #PREVENTIVE} order is generated from a schedule and has none, and closing it is
 * what moves the asset's {@code lastServicedOn} — which is the fact the S152 dashboard's
 * "service overdue" count is derived from. Without the type, closing a repair would look like
 * servicing the asset.
 */
public enum WorkOrderType {

    /** Answers a reported fault. Always linked to one. */
    CORRECTIVE,
    /** Generated from a preventive schedule. Closing it records the service against the asset. */
    PREVENTIVE,
    /** A planned inspection that is not itself a service — a statutory check, a survey. */
    INSPECTION;

    /** {@code true} when closing this order should record a service against its asset. */
    public boolean recordsService() {
        return this == PREVENTIVE;
    }
}
