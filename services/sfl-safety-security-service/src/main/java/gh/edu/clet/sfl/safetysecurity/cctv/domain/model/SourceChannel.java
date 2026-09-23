package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

/** How a change reached S161 - mirrors {@code accesscontrol.domain.model.SourceChannel}; each module in
 * this service keeps its own copy of this plumbing rather than sharing one across module boundaries. */
public enum SourceChannel {
    WEB,
    INTEGRATION,
    SCHEDULER
}
