package gh.edu.clet.sfl.facilities.shared.application.vendor;

import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorRejectionReason;
import java.time.Instant;
import java.util.UUID;

/** Persistence for {@code facilities.vendor_inbox_messages} - see V15. */
public interface VendorInboxPort {

    boolean alreadyAccepted(String sourceId, String idempotencyKey);

    /** In the caller's transaction, so a failed domain action releases the claim. */
    UUID recordAccepted(InboxEntry entry);

    /** A replay of an accepted message. Recorded so the vendor's retry pattern is visible. */
    UUID recordDuplicate(InboxEntry entry);

    /**
     * Joins the current transaction. {@link VendorRejectionRecorder} is what gives a rejection a
     * transaction of its own - see there for why that matters.
     */
    UUID recordRejected(InboxEntry entry, VendorRejectionReason reason, String detail);

    record InboxEntry(String sourceId, VendorChannel channel, String messageType, String idempotencyKey,
            String siteCode, String payloadHash, Instant signedAt, Instant receivedAt, String correlationId) {
    }
}
