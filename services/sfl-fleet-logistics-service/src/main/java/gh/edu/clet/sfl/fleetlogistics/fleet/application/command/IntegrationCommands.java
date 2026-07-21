package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Commands for secure fleet integrations (SRS-SFL-S166-04). */
public final class IntegrationCommands {

    private IntegrationCommands() {
    }

    public record ReceiveIntegrationMessage(
            String sourceSystem,
            String idempotencyKey,
            String eventType,
            String siteCode,
            Instant occurredAt,
            String signature,
            Instant signatureTimestamp,
            String rawPayload,
            Map<String, Object> payload,
            ActorContext actor,
            SourceChannel sourceChannel) implements FleetCommand {
    }

    public record ReplayIntegrationMessage(UUID messageId, ActorContext actor, SourceChannel sourceChannel)
            implements FleetCommand {
    }
}
