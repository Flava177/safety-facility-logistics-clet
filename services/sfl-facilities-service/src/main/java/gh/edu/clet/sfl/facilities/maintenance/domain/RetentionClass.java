package gh.edu.clet.sfl.facilities.maintenance.domain;

import java.time.LocalDate;
import java.time.Period;

/**
 * How long a piece of evidence must be kept, and therefore when it may be disposed of.
 *
 * <p>SRS-SFL-S153-03 makes the retention class mandatory on evidence and says so twice — once in the
 * system-managed fields and once in the validation rules. It is mandatory because disposal is the
 * irreversible half of retention: evidence with no class attached has no defensible date on which
 * anybody may delete it, so in practice it is either kept forever or deleted by whoever is clearing
 * space. Both are failures, and only one of them is visible.
 *
 * <p>The periods are the floor, not the ceiling. A legal hold overrides all of them, which is why it
 * is a flag on the evidence rather than another class here — a hold is a temporary state that must
 * be lifted, not a reclassification.
 */
public enum RetentionClass {

    /** Routine maintenance evidence: a photograph of a replaced part. */
    OPERATIONAL(Period.ofYears(1)),
    /** Evidence supporting a statutory or contractual compliance claim. */
    COMPLIANCE(Period.ofYears(7)),
    /** Evidence attached to work on a life-safety asset — fire, egress, emergency power. */
    SAFETY_CRITICAL(Period.ofYears(10)),
    /** Evidence relating to examination continuity, which §21.2 protects from deletion. */
    EXAMINATION(Period.ofYears(7)),
    /** Evidence forming part of an incident, dispute or investigation record. */
    LEGAL(Period.ofYears(10));

    private final Period minimumRetention;

    RetentionClass(Period minimumRetention) {
        this.minimumRetention = minimumRetention;
    }

    public Period minimumRetention() {
        return minimumRetention;
    }

    /** The earliest date this evidence may be disposed of, absent a legal hold. */
    public LocalDate disposalEligibleFrom(LocalDate uploadedOn) {
        return uploadedOn == null ? null : uploadedOn.plus(minimumRetention);
    }
}
