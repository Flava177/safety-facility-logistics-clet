package gh.edu.clet.sfl.facilities.shared.domain.model;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The record lifecycle required by SRS-SFL-S152-01: "Records shall support active, inactive,
 * suspended and archived lifecycle states where applicable."
 *
 * <p>The state machine is declared here rather than checked ad hoc at each call site, because the
 * same four states apply to sites, buildings, floors, spaces, zones, devices and assets. Two rules
 * are worth naming:
 *
 * <ul>
 *   <li><strong>{@link #ARCHIVED} is terminal.</strong> Archival is the closest thing this platform
 *       has to a delete, and §21.2 of the SRS requires records used for examination continuity to be
 *       protected from deletion. Un-archiving would let a record leave and re-enter the estate with
 *       its history implying it never left.</li>
 *   <li><strong>{@link #SUSPENDED} is reversible.</strong> A suspended space is one temporarily out
 *       of service — a flooded hall, a floor under works — and it has to come back.</li>
 * </ul>
 */
public enum RecordLifecycleStatus {

    /** In service and available to every downstream module. */
    ACTIVE,
    /** Retained and readable, but not offered for new operational use. */
    INACTIVE,
    /** Temporarily out of service and expected to return. */
    SUSPENDED,
    /** Permanently retired. Terminal — readable forever, never reactivated. */
    ARCHIVED;

    private static final Map<RecordLifecycleStatus, Set<RecordLifecycleStatus>> ALLOWED = Map.of(
            ACTIVE, EnumSet.of(INACTIVE, SUSPENDED, ARCHIVED),
            INACTIVE, EnumSet.of(ACTIVE, SUSPENDED, ARCHIVED),
            SUSPENDED, EnumSet.of(ACTIVE, INACTIVE, ARCHIVED),
            ARCHIVED, EnumSet.noneOf(RecordLifecycleStatus.class));

    /** {@code true} when this record still participates in operational workflow. */
    public boolean isOperational() {
        return this == ACTIVE;
    }

    /** {@code true} when a duplicate check should consider this record as occupying its identifier. */
    public boolean occupiesIdentifier() {
        return this != ARCHIVED;
    }

    public boolean canTransitionTo(RecordLifecycleStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** Applies a transition, refusing anything the machine does not allow. */
    public RecordLifecycleStatus transitionTo(RecordLifecycleStatus target, String recordType) {
        if (this == target) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    recordType + " is already " + name() + ".");
        }
        if (!canTransitionTo(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    recordType + " cannot move from " + name() + " to " + target + ".");
        }
        return target;
    }
}
