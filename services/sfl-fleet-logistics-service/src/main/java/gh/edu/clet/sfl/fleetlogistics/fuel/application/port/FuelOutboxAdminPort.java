package gh.edu.clet.sfl.fleetlogistics.fuel.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read/observe and replay port over the shared transactional outbox, so the fuel
 * integration surface can expose outbound delivery health and privileged dead-letter
 * replay without depending on messaging infrastructure types directly.
 */
public interface FuelOutboxAdminPort {

    /** Outbound delivery health: pending/published/dead-letter counts and recent dead-letters. */
    OutboxHealth health();

    /** Requeue a dead-lettered outbox message for another delivery attempt. Returns false if not dead-lettered. */
    boolean replay(UUID messageId);

    record OutboxHealth(long pending, long published, long deadLettered, List<OutboxEntry> recentDeadLetters) {}

    record OutboxEntry(UUID id, String eventType, String aggregateType, String aggregateId, String status,
            int attemptCount, String failureReason, Instant createdAt) {}
}
