package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model;

/** Tamper-seal state recorded at each custody handover and at destination receipt. */
public enum SealState {
    INTACT,
    BROKEN,
    REPLACED,
    MISSING;

    /** A seal state other than INTACT is a custody/tamper concern that blocks clean closure. */
    public boolean isCompromised() {
        return this != INTACT;
    }
}
