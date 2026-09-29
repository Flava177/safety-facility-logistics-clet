package gh.edu.clet.sfl.facilities.shared.application.vendor;

import java.time.Instant;
import java.util.Map;

/**
 * An inbound vendor message exactly as it arrived, before anything about it is believed.
 *
 * @param rawPayload the body byte-for-byte as received. The signature is over this, not over a
 *        re-serialisation of {@code payload} - a parser that normalised whitespace or key order would
 *        otherwise make every genuine message fail and, worse, could make a forged one pass
 * @param payload the parsed body, for schema checks. Parsed by the API layer; never trusted before
 *        the signature over {@code rawPayload} has been checked
 */
public record SignedVendorMessage(
        String sourceId,
        String messageType,
        String idempotencyKey,
        String siteCode,
        Instant signedAt,
        String signature,
        String rawPayload,
        Map<String, Object> payload) {
}
