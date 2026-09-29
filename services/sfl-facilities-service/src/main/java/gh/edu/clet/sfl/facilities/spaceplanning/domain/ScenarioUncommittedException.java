package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;

/**
 * "Uncommitted Scenario Referenced - an operational workflow attempts to treat a draft scenario as current
 * state; refused." - SRS-SFL-S158-01.
 *
 * <p>Its own subclass, rather than a {@link FacilitiesException} thrown with the code, so the services
 * that raise it can declare {@code noRollbackFor} it: the refusal is audited in the caller's transaction,
 * nothing else has been written when it is thrown, and an audit record that rolled back with the
 * refusal it documents would leave no trace of the attempt. {@code recordDenial}'s
 * {@code REQUIRES_NEW} is not used for this because it would wait on the audit chain lock whenever the
 * caller (S176 at handover, say) had already appended in the same transaction.
 */
public class ScenarioUncommittedException extends FacilitiesException {

    private static final long serialVersionUID = 1L;

    public ScenarioUncommittedException(String detail) {
        super(FacilitiesErrorCode.SPACE_SCENARIO_UNCOMMITTED,
                FacilitiesErrorCode.SPACE_SCENARIO_UNCOMMITTED.defaultMessage() + " " + detail);
    }
}
