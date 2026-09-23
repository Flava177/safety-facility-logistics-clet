package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

/** The exception rules S160a-04 evaluates access events against, and the reader-health/S160a-06 rule
 * that shares the same SOC queue. */
public enum ExceptionRuleCode {
    REPEATED_DENIAL,
    FORCED_OPEN,
    TAILGATING,
    OUT_OF_HOURS,
    RESTRICTED_ZONE,
    READER_OFFLINE,
    ANTI_PASSBACK
}
