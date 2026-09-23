package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

/** Current state of a door reader/controller, as reported by the vendor system (S160a-04). */
public enum ReaderHealthStatus {
    ONLINE,
    OFFLINE,
    TAMPERED,
    DEGRADED
}
