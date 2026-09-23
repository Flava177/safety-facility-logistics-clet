package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

/** How a record reached the system (system-managed field, part of the audit record). Module-local
 * copy, following {@code accesscontrol.domain.model.SourceChannel}'s precedent. */
public enum SourceChannel {
    WEB,
    MOBILE,
    API,
    INTEGRATION,
    SCHEDULER,
    SYSTEM,
    IMPORT,
    EDGE,
    MIGRATION
}
