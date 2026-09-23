package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

/** The vendor access-control event kinds S160a-01 must ingest, normalised from the vendor's own terms. */
public enum AccessEventKind {
    GRANTED,
    DENIED,
    FORCED_OPEN,
    OVERRIDE,
    TAMPER
}
