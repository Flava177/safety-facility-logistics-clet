package gh.edu.clet.sfl.facilities.cleaning.domain;

/** Whether a registered cleaning vendor may be given new work. */
public enum VendorStatus {
    ACTIVE,
    /** Kept on file with its scorecard; no new assignment. */
    SUSPENDED
}
