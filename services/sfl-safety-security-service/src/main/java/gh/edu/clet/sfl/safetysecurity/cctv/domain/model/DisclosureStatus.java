package gh.edu.clet.sfl.safetysecurity.cctv.domain.model;

/** SRS-SFL-S161-05: a governed disclosure request's decision state. {@code APPROVED} is the release
 * event itself - "footage released by reference and hashed" happens at the moment of approval, so no
 * separate disclosed state is needed for Phase 1. */
public enum DisclosureStatus {
    REQUESTED,
    APPROVED,
    REJECTED
}
