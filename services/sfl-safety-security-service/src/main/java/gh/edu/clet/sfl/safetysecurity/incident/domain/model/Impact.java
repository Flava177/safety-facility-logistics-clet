package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

/** The impact axis of a {@link RiskRating}'s likelihood x impact matrix - SRS §D.9 step 2. */
public enum Impact {
    NEGLIGIBLE,
    MINOR,
    MODERATE,
    MAJOR,
    CATASTROPHIC
}
