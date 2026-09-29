package gh.edu.clet.sfl.facilities.construction.domain;

import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;

/**
 * An S176 gate refusing an act, after the refusal itself has been recorded.
 *
 * <p>Its own type for one reason: the application services declare
 * {@code @Transactional(noRollbackFor = ConstructionRefusal.class)}. S176's error states are all of the
 * "refused, and the refusal is evidence" kind - {@code PROJECT_START_REFUSED} is audited, a lapsed
 * contractor's refused access request is kept with its reason, a variation held for escalation stays
 * held, an incomplete handover stays flagged. If the refusal rolled back with the exception, every one
 * of those records would vanish with it and the SRS's "flagged, not silently accepted" would be a
 * message nobody could find afterwards.
 *
 * <p>So a service records what it refused and then throws this; the transaction commits the record
 * and the caller still gets the error state. Anything else thrown - a validation failure, a version
 * conflict, an S152 refusal inside a handover - rolls back as usual.
 *
 * <p>The message is the SRS wording followed by the specific reason, because S176-02 says "refused
 * with reason shown" and a bare "Compliance Lapsed" does not tell a site supervisor which certificate
 * to chase.
 */
public class ConstructionRefusal extends FacilitiesException {

    public ConstructionRefusal(FacilitiesErrorCode code, String reason) {
        super(code, reason == null || reason.isBlank()
                ? code.defaultMessage()
                : code.defaultMessage() + " " + reason.strip());
    }
}
