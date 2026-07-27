package gh.edu.clet.sfl.emergencynotification.domain.model;

/** Lifecycle of a master-data operational record (SRS-SFL-S174-01). */
public enum RecordLifecycle {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    ARCHIVED;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
