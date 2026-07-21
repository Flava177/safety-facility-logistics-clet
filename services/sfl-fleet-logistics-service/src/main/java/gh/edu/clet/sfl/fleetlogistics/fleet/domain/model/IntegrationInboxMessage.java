package gh.edu.clet.sfl.fleetlogistics.fleet.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Append-only inbound message ledger for secure fleet integrations (SRS-SFL-S166-04). */
public record IntegrationInboxMessage(
        UUID id,
        String sourceSystem,
        String idempotencyKey,
        String eventType,
        SiteCode siteCode,
        String correlationId,
        Instant occurredAt,
        String payloadHash,
        String rawPayload,
        IntegrationMessageStatus status,
        int attempts,
        String failureReason,
        Instant receivedAt,
        Instant processedAt) {

    public IntegrationInboxMessage {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(siteCode, "siteCode is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(receivedAt, "receivedAt is required");
        sourceSystem = requireText(sourceSystem, "sourceSystem").toUpperCase(java.util.Locale.ROOT);
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        eventType = requireText(eventType, "eventType");
        payloadHash = requireText(payloadHash, "payloadHash");
        rawPayload = requireText(rawPayload, "rawPayload");
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts cannot be negative");
        }
    }

    public static IntegrationInboxMessage accept(UUID id, String sourceSystem, String idempotencyKey,
            String eventType, SiteCode siteCode, String correlationId, Instant occurredAt, String payloadHash,
            String rawPayload, Instant receivedAt) {
        return new IntegrationInboxMessage(id, sourceSystem, idempotencyKey, eventType, siteCode, correlationId,
                occurredAt, payloadHash, rawPayload, IntegrationMessageStatus.ACCEPTED, 0, null, receivedAt, null);
    }

    public IntegrationInboxMessage processed(Instant now) {
        return new IntegrationInboxMessage(id, sourceSystem, idempotencyKey, eventType, siteCode, correlationId,
                occurredAt, payloadHash, rawPayload, IntegrationMessageStatus.PROCESSED, attempts + 1,
                failureReason, receivedAt, now);
    }

    public IntegrationInboxMessage deadLetter(String reason, Instant now) {
        return new IntegrationInboxMessage(id, sourceSystem, idempotencyKey, eventType, siteCode, correlationId,
                occurredAt, payloadHash, rawPayload, IntegrationMessageStatus.DEAD_LETTER, attempts + 1,
                requireText(reason, "reason"), receivedAt, now);
    }

    public Map<String, Object> auditImage() {
        return Map.ofEntries(
                Map.entry("messageId", id.toString()),
                Map.entry("sourceSystem", sourceSystem),
                Map.entry("idempotencyKey", idempotencyKey),
                Map.entry("eventType", eventType),
                Map.entry("siteCode", siteCode.value()),
                Map.entry("correlationId", correlationId == null ? "" : correlationId),
                Map.entry("occurredAt", occurredAt.toString()),
                Map.entry("payloadHash", payloadHash),
                Map.entry("status", status.name()),
                Map.entry("attempts", attempts),
                Map.entry("failureReason", failureReason == null ? "" : failureReason));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }
}
