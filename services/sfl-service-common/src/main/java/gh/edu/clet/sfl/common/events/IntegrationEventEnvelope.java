package gh.edu.clet.sfl.common.events;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Envelope wrapping every integration event published across services. {@code correlationId},
 * {@code causationId}, {@code siteScope}, {@code actorId} and {@code traceParent} are optional
 * propagation metadata and may be null; every other field is mandatory so a malformed envelope
 * fails fast at construction rather than corrupting a downstream consumer's event stream.
 */
public record IntegrationEventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String causationId,
        String sourceService,
        String siteScope,
        String actorId,
        String traceParent,
        Map<String, Object> payload) {

    public IntegrationEventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        requireNonBlank(eventType, "eventType");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1, was " + eventVersion);
        }
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        requireNonBlank(sourceService, "sourceService");
        Objects.requireNonNull(payload, "payload must not be null");
        // Copied rather than stored as-is: a caller mutating the map it passed in (or received back
        // from payload()) must not be able to reach into an envelope already handed to another
        // service - an event, once built, is a fact, not a shared mutable buffer. LinkedHashMap over
        // Map.copyOf because a payload legitimately may carry a null value and Map.copyOf rejects one.
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
    }

    /** Builder for named-argument construction, avoiding the transposition risk of the 11-arg canonical constructor. */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID eventId;
        private String eventType;
        private int eventVersion = 1;
        private Instant occurredAt;
        private String correlationId;
        private String causationId;
        private String sourceService;
        private String siteScope;
        private String actorId;
        private String traceParent;
        private Map<String, Object> payload = Map.of();

        private Builder() {
        }

        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder eventVersion(int eventVersion) {
            this.eventVersion = eventVersion;
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder causationId(String causationId) {
            this.causationId = causationId;
            return this;
        }

        public Builder sourceService(String sourceService) {
            this.sourceService = sourceService;
            return this;
        }

        public Builder siteScope(String siteScope) {
            this.siteScope = siteScope;
            return this;
        }

        public Builder actorId(String actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder traceParent(String traceParent) {
            this.traceParent = traceParent;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
            return this;
        }

        public IntegrationEventEnvelope build() {
            return new IntegrationEventEnvelope(
                    eventId == null ? UUID.randomUUID() : eventId,
                    eventType,
                    eventVersion,
                    occurredAt == null ? Instant.now() : occurredAt,
                    correlationId,
                    causationId,
                    sourceService,
                    siteScope,
                    actorId,
                    traceParent,
                    payload == null ? Map.of() : payload);
        }
    }
}

