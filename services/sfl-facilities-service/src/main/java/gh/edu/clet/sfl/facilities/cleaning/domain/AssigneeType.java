package gh.edu.clet.sfl.facilities.cleaning.domain;

/** Who a task is assigned to - SRS-SFL-S169-01 "assigned to cleaning staff/vendor". */
public enum AssigneeType {
    /** An in-house cleaner, by their identity-provider subject. */
    STAFF,
    /** A technician of a registered cleaning vendor; the vendor's SLA terms then apply. */
    VENDOR
}
