package gh.edu.clet.sfl.facilities.energy.domain;

/**
 * A meter is live or retired. There is no archive-and-reuse: a retired meter keeps its AVAMP identity
 * and its history, because the identity belongs to the physical device (S157-04).
 */
public enum MeterStatus {
    ACTIVE,
    RETIRED
}
