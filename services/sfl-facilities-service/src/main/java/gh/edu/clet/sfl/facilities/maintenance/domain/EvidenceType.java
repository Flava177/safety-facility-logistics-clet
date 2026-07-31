package gh.edu.clet.sfl.facilities.maintenance.domain;

/** What a piece of evidence is, so a reviewer can tell a before-photo from a signed certificate. */
public enum EvidenceType {
    /** The condition before work started. */
    BEFORE_PHOTO,
    /** The condition after work finished. */
    AFTER_PHOTO,
    /** A vendor's service report or job sheet. */
    SERVICE_REPORT,
    /** A test or inspection certificate. */
    CERTIFICATE,
    /** A parts invoice or delivery note. */
    INVOICE,
    /** Anything else, described in the evidence's own notes. */
    OTHER
}
