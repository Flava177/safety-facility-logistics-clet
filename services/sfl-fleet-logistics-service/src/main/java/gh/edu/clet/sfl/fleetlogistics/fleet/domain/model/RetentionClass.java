package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Period;

/**
 * Retention class for evidence and compliance records (SRS-SFL-S166-03 system-managed fields,
 * SRS §23.6 Data Retention and Privacy).
 *
 * <p>Gap report C-08: the SRS enumerates mandatory retention classes for CCTV, visitor, biometric,
 * incident and dispatch evidence and does not name fleet evidence explicitly. This implementation
 * requires a retention class on <em>every</em> fleet evidence record, which cannot under-comply. The
 * concrete periods below are the working assumption and need owner confirmation.
 */
public enum RetentionClass {

    /** Operational records with no statutory retention driver. */
    OPERATIONAL_SHORT(Period.ofYears(1)),
    /** Default for trip, inspection and service evidence. */
    OPERATIONAL_STANDARD(Period.ofYears(3)),
    /** Compliance documents and their evidence. */
    COMPLIANCE(Period.ofYears(6)),
    /** Evidence attached to an incident, defect or dispute. */
    INCIDENT(Period.ofYears(7)),
    /** Statutory or audit evidence with the longest retention. */
    STATUTORY(Period.ofYears(10)),
    /** Held indefinitely under legal hold; never purged while the hold stands. */
    LEGAL_HOLD(null);

    private final Period retentionPeriod;

    RetentionClass(Period retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
    }

    /** {@code null} means "no automatic expiry" — only {@link #LEGAL_HOLD}. */
    public Period retentionPeriod() {
        return retentionPeriod;
    }

    public boolean isIndefinite() {
        return retentionPeriod == null;
    }
}
