package gh.edu.clet.sfl.facilities.energy.domain;

/**
 * Where a reading stands relative to the consumption record.
 *
 * <p>Only {@link #POSTED} readings are in the consumption record. {@link #HELD} is SRS-SFL-S157-01's
 * "Implausible Reading - held for supervisor verification, not silently accepted": it exists, it is
 * visible, and it counts towards nothing until somebody other than the enterer decides. The only
 * transitions are out of HELD; a posted reading is corrected by a new reading, never by editing it.
 */
public enum ReadingStatus {
    POSTED,
    HELD,
    REJECTED;

    public boolean canTransitionTo(ReadingStatus target) {
        return this == HELD && (target == POSTED || target == REJECTED);
    }
}
