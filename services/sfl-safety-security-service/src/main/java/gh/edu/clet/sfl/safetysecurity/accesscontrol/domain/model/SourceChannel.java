package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

/** How a record reached the system (system-managed field, part of the audit record). */
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
