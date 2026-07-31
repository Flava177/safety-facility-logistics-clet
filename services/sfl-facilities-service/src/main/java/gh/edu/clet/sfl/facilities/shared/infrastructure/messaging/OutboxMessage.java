package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

import java.util.UUID;

/**
 * One outbox row, as the transport needs to see it.
 *
 * <p>Read through {@code JdbcTemplate} rather than the JPA entity because the drainer claims rows with
 * {@code FOR UPDATE SKIP LOCKED} and writes back the V5 delivery-state columns
 * ({@code attempt_count}, {@code next_attempt_at}, {@code dead_lettered_at}) that
 * {@code OutboxMessageRecord} does not map. Mapping them onto the entity would put retry bookkeeping
 * into the aggregate's persistence model, where nothing in the domain has any business reading it.
 */
public record OutboxMessage(
        UUID id,
        String eventType,
        int eventVersion,
        int schemaVersion,
        String aggregateType,
        String aggregateId,
        String siteScope,
        String correlationId,
        String causationId,
        String traceParent,
        String payload) {
}
