package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging;

import java.util.UUID;

/**
 * One {@code emergency_notification.outbox_messages} row, as the transport needs to see it.
 *
 * <p>Mirrors {@code OutboxMessage} in facilities/fleet, minus the two columns this service's outbox
 * table does not carry ({@code schema_version}, {@code trace_parent}) - this table predates those and
 * adding them is a separate, larger change than the transport this record exists to support.
 */
public record EmergencyOutboxMessage(
        UUID id,
        String eventType,
        int eventVersion,
        String aggregateType,
        String aggregateId,
        String siteScope,
        String correlationId,
        String causationId,
        String payload) {
}
