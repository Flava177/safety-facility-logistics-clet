package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

/**
 * How long a piece of {@link IncidentEvidence} must be kept - own copy, simplified from facilities'
 * {@code RetentionClass} (no disposal-eligibility calendar in this slice; that lifecycle was a
 * facilities-specific requirement, not something D.9 asks for). Mandatory on every evidence item for
 * the same reason facilities makes it mandatory: an unclassified record cannot be safely disposed of
 * or safely kept, so the classification is required at capture time rather than assumed later.
 */
public enum RetentionClass {
    STANDARD,
    EXTENDED,
    PERMANENT
}
