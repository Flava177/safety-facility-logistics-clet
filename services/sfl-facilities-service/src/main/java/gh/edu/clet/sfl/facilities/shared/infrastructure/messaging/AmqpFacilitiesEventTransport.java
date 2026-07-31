package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ transport: exchange {@code sfl.events}, routing key
 * {@code {platform}.{event-name}.v{version}}, dead-letter exchange {@code sfl.events.dlx} — the
 * Phase 1 topology from the event catalogue, and byte-for-byte the envelope the fleet transport sends
 * so a consumer can bind both without a per-service special case.
 */
final class AmqpFacilitiesEventTransport implements FacilitiesEventTransport {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    AmqpFacilitiesEventTransport(RabbitTemplate rabbitTemplate, String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    @Override
    public void send(OutboxMessage outboxMessage) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(outboxMessage.id().toString());
        properties.setType(outboxMessage.eventType());
        properties.setCorrelationId(outboxMessage.correlationId());
        properties.setHeader("eventType", outboxMessage.eventType());
        properties.setHeader("eventVersion", outboxMessage.eventVersion());
        properties.setHeader("schemaVersion", outboxMessage.schemaVersion());
        properties.setHeader("aggregateType", outboxMessage.aggregateType());
        properties.setHeader("aggregateId", outboxMessage.aggregateId());
        properties.setHeader("siteCode", outboxMessage.siteScope());
        properties.setHeader("causationId", outboxMessage.causationId());
        properties.setHeader("sourceModule", "SFL.IFIMP");
        if (outboxMessage.traceParent() != null) {
            properties.setHeader("traceparent", outboxMessage.traceParent());
        }

        Message message = MessageBuilder
                .withBody(outboxMessage.payload().getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();

        rabbitTemplate.send(exchange, routingKeyOf(outboxMessage.eventType()), message);
    }

    @Override
    public String name() {
        return "rabbitmq";
    }

    /** {@code sfl.ifimp.work-order-created.v1} becomes routing key {@code ifimp.work-order-created.v1}. */
    private static String routingKeyOf(String eventType) {
        return eventType.startsWith("sfl.") ? eventType.substring("sfl.".length()) : eventType;
    }
}
