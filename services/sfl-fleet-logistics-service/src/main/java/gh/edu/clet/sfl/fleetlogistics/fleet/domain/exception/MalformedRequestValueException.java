package gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception;

import java.util.Map;

/**
 * A request value a controller could not parse into the type it names - an unparsable
 * {@code UUID} or {@code Instant} arriving in a request body field, most often. Raised deliberately
 * at the specific parsing call site rather than left to surface as a raw {@code IllegalArgumentException}
 * or {@code DateTimeParseException}: those exceptions are also thrown by code that has nothing to do
 * with request parsing, and the blanket JDK-exception handlers would reclassify them as this same 400
 * whatever actually threw them, hiding a genuine bug behind a client-error status.
 */
public class MalformedRequestValueException extends FleetDomainException {

    public MalformedRequestValueException(Map<String, Object> details) {
        super(FleetErrorCode.FLEET_MALFORMED_REQUEST_VALUE, details);
    }

    public static MalformedRequestValueException of(String field, String value) {
        return new MalformedRequestValueException(Map.of("field", field, "value", String.valueOf(value)));
    }
}
