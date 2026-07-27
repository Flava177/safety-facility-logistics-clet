package gh.edu.clet.sfl.emergencynotification.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read/observe and privileged replay over the transactional outbox (integration health). */
public interface OutboxAdminPort {

    OutboxHealth health();

    /** Requeue a dead-lettered message for another delivery attempt. Returns false if not dead-lettered. */
    boolean replay(UUID messageId);

    record OutboxHealth(long pending, long published, long deadLettered, List<OutboxEntry> recentDeadLetters) {
    }

    record OutboxEntry(UUID id, String eventType, String aggregateType, String aggregateId, String status,
            int attemptCount, String failureReason, Instant createdAt) {
    }
}
