package gh.edu.clet.sfl.emergencynotification.domain.model;

/** How a record or message reached the system (system-managed field, part of the audit record). */
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
