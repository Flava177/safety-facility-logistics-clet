package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/**
 * The evidence record is real; the file behind it is not held here.
 *
 * <p>Not a 404 on the evidence - the metadata exists and the caller was authorised to read it. This
 * is the older state showing through: every reference registered before the file store carries a
 * {@code local-demo://} key pointing at a document store Release 1 never had. Distinguishing the two
 * matters, because "no such evidence" and "this evidence predates uploads" call for different
 * responses from whoever hits it.
 */
public class EvidenceContentMissingException extends FleetDomainException {

    public EvidenceContentMissingException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_EVIDENCE_CONTENT_MISSING, details);
    }
}
