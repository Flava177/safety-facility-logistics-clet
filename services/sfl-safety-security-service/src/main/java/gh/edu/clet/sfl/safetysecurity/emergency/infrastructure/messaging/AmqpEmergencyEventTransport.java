package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ transport: exchange {@code sfl.events}, routing key {@code {platform}.{event-name}.v{version}}
 * - the same shared Phase 1 topology facilities and fleet already publish to. {@code EmergencyEventType}
 * already names every event {@code sfl.ssemp.{event-name}.v1} for exactly this routing key, and
 * facilities' own inbound topology already binds {@code ssemp.#} waiting to receive it - this class is
 * the missing producer, not a new contract.
 *
 * <p><strong>Waits for the broker, not the socket.</strong> Byte-for-byte the same reasoning and the
 * same mechanism as {@code AmqpFacilitiesEventTransport}: a bare {@code RabbitTemplate.send} returns
 * once the message reaches the connection's local buffer, before the broker has done anything with it.
 * With {@code publisher-confirm-type: correlated} and {@code publisher-returns: true}
 * (application.yml), the per-message {@link CorrelationData} carries a future that only completes once
 * the broker acks or nacks the publish, and a {@code returnedMessage} set if the message was
 * mandatory-returned as unroutable before the ack. {@link #send} waits on that future and treats
 * anything short of an unreturned ack as a failure, sending it back through the drainer's existing
 * retry/dead-letter path exactly like a thrown transport exception always has.
 */
final class AmqpEmergencyEventTransport implements EmergencyEventTransport {

    private static final Duration DEFAULT_CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final Duration confirmTimeout;

    AmqpEmergencyEventTransport(RabbitTemplate rabbitTemplate, String exchange) {
        this(rabbitTemplate, exchange, DEFAULT_CONFIRM_TIMEOUT);
    }

    AmqpEmergencyEventTransport(RabbitTemplate rabbitTemplate, String exchange, Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.confirmTimeout = confirmTimeout;
    }

    @Override
    public void send(EmergencyOutboxMessage outboxMessage) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setMessageId(outboxMessage.id().toString());
        properties.setType(outboxMessage.eventType());
        properties.setCorrelationId(outboxMessage.correlationId());
        properties.setHeader("eventType", outboxMessage.eventType());
        properties.setHeader("eventVersion", outboxMessage.eventVersion());
        properties.setHeader("aggregateType", outboxMessage.aggregateType());
        properties.setHeader("aggregateId", outboxMessage.aggregateId());
        properties.setHeader("siteCode", outboxMessage.siteScope());
        properties.setHeader("causationId", outboxMessage.causationId());
        properties.setHeader("sourceModule", "SFL.SSEMP");

        Message message = MessageBuilder
                .withBody(outboxMessage.payload().getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();

        CorrelationData correlationData = new CorrelationData(outboxMessage.id().toString());
        rabbitTemplate.send(exchange, routingKeyOf(outboxMessage.eventType()), message, correlationData);
        awaitConfirmation(outboxMessage, correlationData);
    }

    private void awaitConfirmation(EmergencyOutboxMessage outboxMessage, CorrelationData correlationData) {
        CorrelationData.Confirm confirm;
        try {
            confirm = correlationData.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted waiting for broker confirmation of " + outboxMessage.id(), exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "Failed waiting for broker confirmation of " + outboxMessage.id(), exception.getCause());
        } catch (TimeoutException exception) {
            throw new IllegalStateException(
                    "Timed out after " + confirmTimeout + " waiting for broker confirmation of "
                            + outboxMessage.id());
        }

        if (correlationData.getReturned() != null) {
            throw new IllegalStateException("Message " + outboxMessage.id() + " (" + outboxMessage.eventType()
                    + ") was returned as unroutable: " + correlationData.getReturned().getReplyText());
        }
        if (confirm == null || !confirm.isAck()) {
            throw new IllegalStateException("Broker did not acknowledge publish of " + outboxMessage.id() + " ("
                    + outboxMessage.eventType() + "): "
                    + (confirm == null ? "no confirmation received" : confirm.getReason()));
        }
    }

    @Override
    public String name() {
        return "rabbitmq";
    }

    /** {@code sfl.ssemp.emergency-notification-activated.v1} becomes {@code ssemp.emergency-notification-activated.v1}. */
    private static String routingKeyOf(String eventType) {
        return eventType.startsWith("sfl.") ? eventType.substring("sfl.".length()) : eventType;
    }
}
