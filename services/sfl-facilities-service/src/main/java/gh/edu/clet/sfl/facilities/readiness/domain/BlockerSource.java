package gh.edu.clet.sfl.facilities.readiness.domain;

/**
 * Where a readiness blocker came from.
 *
 * <p>Recorded because the resolution path differs by source and an operator needs to know which one
 * they are looking at: a checklist failure is re-assessed, an asset fault is fixed and the asset's
 * status changed, a maintenance blocker clears when its work order closes.
 */
public enum BlockerSource {

    /** A checklist item that failed during an assessment. */
    CHECKLIST_ITEM,
    /** Derived from a facility asset that is degraded or out of service. */
    ASSET,
    /** Derived from an open S153 work order against this space. */
    WORK_ORDER,
    /** Raised by hand by an officer who saw something the checklist does not cover. */
    MANUAL
}
