package gh.edu.clet.sfl.emergencynotification.application.port;

import java.time.Instant;
import java.util.List;

/**
 * Read access to the secure integration inbox.
 *
 * <p>Closes gap 3, which was the most consequential gap on this service. Provider callbacks are the
 * <em>only</em> thing that ever writes {@code delivered}, {@code failed} and {@code acknowledged} on
 * an activation, and none of that path was readable: no inbox endpoint, no processed count, no
 * rejected count, no message list. An activation showing 480 sent and 0 delivered therefore could
 * not be diagnosed from the dashboard at all — "no provider is configured" and "every callback is
 * being rejected for a bad signature" produced identical screens.
 *
 * <p>Read-only by design. A rejected inbound message is not replayable: it failed signature
 * verification or schema validation, so the sending system has to correct and re-send it. Only
 * dead-lettered <em>outbound</em> messages can be replayed, which is what {@link OutboxAdminPort}
 * offers.
 */
public interface InboxAdminPort {

    InboxHealth health(int recentLimit);

    /**
     * Counts by outcome, plus the most recent envelopes.
     *
     * <p>{@code checkedAt} is stamped by the adapter rather than the caller so a stale cached
     * response cannot present itself as current.
     */
    record InboxHealth(long processed, long rejected, long deadLettered, List<InboxMessage> recentMessages,
            Instant checkedAt) {
    }

    /**
     * One inbound envelope, minus its payload.
     *
     * <p>The raw payload is deliberately absent: it is a provider's message about named recipients,
     * and an integration-health screen has no business displaying recipient contact detail. The hash
     * is enough to tell two deliveries of the same message apart.
     */
    record InboxMessage(String id, String sourceSystem, String eventType, String siteScope, String status,
            int attempts, String failureReason, String idempotencyKey, Instant receivedAt, Instant processedAt) {
    }
}
