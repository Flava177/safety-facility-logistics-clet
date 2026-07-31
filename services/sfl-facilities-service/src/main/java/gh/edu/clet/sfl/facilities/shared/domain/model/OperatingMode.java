package gh.edu.clet.sfl.facilities.shared.domain.model;

/**
 * The site operating mode (NFR 23.3, and an input to SRS-SFL-S152-02 and -05).
 *
 * <p>"Platform mode changes, such as Routine to Examination Mode, must be explicit, audited and
 * reversible only by authorised roles." Mode is a property of the <em>site</em>, not of a space:
 * an examination is declared over a centre, and every space in it inherits the stricter rules.
 *
 * <p>What the mode actually changes in S152:
 * <ul>
 *   <li>{@link #EXAMINATION} makes readiness locks meaningful — a locked space refuses attribute and
 *       readiness changes without an override permission.</li>
 *   <li>The dashboard reports examination-readiness risk separately from routine readiness, because a
 *       space that is fine for a meeting may not be fine for an examination.</li>
 *   <li>SLA and escalation rules are evaluated against the mode active at evaluation time.</li>
 * </ul>
 */
public enum OperatingMode {
    /** Normal operations. */
    ROUTINE,
    /** An examination is in progress or being set up at this site. Stricter readiness applies. */
    EXAMINATION
}
