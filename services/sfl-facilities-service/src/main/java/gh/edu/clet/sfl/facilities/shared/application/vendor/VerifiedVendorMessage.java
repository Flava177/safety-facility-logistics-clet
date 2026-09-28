package gh.edu.clet.sfl.facilities.shared.application.vendor;

import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A vendor message that passed every check. The only type a domain service accepts from a vendor.
 *
 * @param duplicate {@code true} when this source already delivered this idempotency key. The module
 *        must then do nothing and answer as it did the first time.
 */
public record VerifiedVendorMessage(
        UUID inboxId,
        String sourceId,
        VendorChannel channel,
        String messageType,
        String idempotencyKey,
        String siteCode,
        Map<String, Object> payload,
        String payloadHash,
        Instant receivedAt,
        boolean duplicate) {

    public String text(String field) {
        Object value = payload.get(field);
        return value == null ? null : String.valueOf(value);
    }
}
