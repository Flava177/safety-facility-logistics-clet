package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/** SRS-SFL-S166-01 Duplicate Identifier: an active record with this identifier already exists for the site. */
public class DuplicateActiveIdentifierException extends FleetDomainException {

    public DuplicateActiveIdentifierException() {
        super(FleetErrorCode.FLEET_DUPLICATE_IDENTIFIER);
    }

    public DuplicateActiveIdentifierException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_DUPLICATE_IDENTIFIER, details);
    }

    public static DuplicateActiveIdentifierException of(String objectType, String identifierName,
            String identifier, String siteCode) {
        return new DuplicateActiveIdentifierException(Map.of(
                "objectType", objectType,
                "identifierName", identifierName,
                "identifier", identifier,
                "siteCode", siteCode));
    }
}
