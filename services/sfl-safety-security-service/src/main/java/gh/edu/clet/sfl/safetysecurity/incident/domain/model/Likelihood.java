package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

/** The likelihood axis of a {@link RiskRating}'s likelihood x impact matrix - SRS §D.9 step 2. */
public enum Likelihood {
    RARE,
    UNLIKELY,
    POSSIBLE,
    LIKELY,
    ALMOST_CERTAIN
}
