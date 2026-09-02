package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

/**
 * Where a {@link SecurityIncident} originated - SRS §11.6: "Incident from report, CCTV, access,
 * fire, intrusion, or HSE". {@code SecurityIncident} is the SRS's shared incident-case aggregate for
 * the whole SSEMP platform (§12.3), not an S163-only type, so this discriminator already reserves a
 * slot for each sibling module's seed even though only {@link #REPORTED} and {@link #HSE} have a
 * producer today - S160a/S161/S162/S162a do not exist yet to seed one.
 */
public enum IncidentSource {
    /** A person reported it directly - the general case, D.9 step 1. */
    REPORTED,
    /** Raised through an HSE-specific channel (e.g. a hazard/near-miss form). */
    HSE,
    /** Reserved for S161 (CCTV/VMS) once built. */
    CCTV_SEED,
    /** Reserved for S160a (physical access control) once built. */
    ACCESS_SEED,
    /** Reserved for S162 (intrusion detection) once built. */
    INTRUSION_SEED,
    /** Reserved for S162a (fire/life-safety) once built. */
    FIRE_SEED
}
