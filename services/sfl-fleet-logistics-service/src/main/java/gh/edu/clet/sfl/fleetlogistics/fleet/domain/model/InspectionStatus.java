package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

/**
 * Inspection record state.
 *
 * <p>A submitted inspection is immutable: it is evidence, and evidence that can be edited after the
 * fact is not evidence. Corrections are made by recording a new inspection.
 */
public enum InspectionStatus {
    DRAFT,
    SUBMITTED,
    ACCEPTED,
    REJECTED;

    public boolean isImmutable() {
        return this != DRAFT;
    }
}
