package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

/** Where a command originated - own copy per module, mirroring {@code visitor.domain.model.SourceChannel}. */
public enum SourceChannel {
    WEB,
    MOBILE,
    EDGE,
    API,
    SYSTEM
}
