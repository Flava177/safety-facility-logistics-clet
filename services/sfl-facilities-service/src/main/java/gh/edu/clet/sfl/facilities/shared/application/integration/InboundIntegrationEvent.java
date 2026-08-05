package gh.edu.clet.sfl.facilities.shared.application.integration;

import java.util.Map;
import java.util.UUID;

/**
 * An event that arrived from another SFL service, in the only shape a handler should need.
 *
 * <p>Framework-free on purpose: no {@code JsonNode}, no AMQP {@code Message}, nothing that names a
 * broker. A handler written against this compiles unchanged whether the message came from RabbitMQ
 * today or the enterprise Kafka (S217) later - which is the whole point of having a shape at all.
 * The transport is what changes; the fact does not.
 *
 * @param messageId the publisher's outbox row id, and the key the inbox dedups on
 * @param eventType canonical name, e.g. {@code sfl.ftlmp.vehicle-service-due.v1}
 * @param aggregateType the kind of record the event is about, e.g. {@code Vehicle}
 * @param aggregateId that record's id in the publishing service - held by value, never resolved
 *     against the publisher's tables
 * @param siteCode the site the event belongs to, so a handler can scope without parsing the payload
 * @param correlationId links this back to whatever started it, across services
 * @param payload the event body, flattened
 */
public record InboundIntegrationEvent(
        UUID messageId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String siteCode,
        String correlationId,
        String causationId,
        Map<String, Object> payload) {

    /** A payload field as text, or null. Absent and null read the same, which is what callers want. */
    public String text(String field) {
        Object value = payload == null ? null : payload.get(field);
        return value == null ? null : String.valueOf(value);
    }
}
