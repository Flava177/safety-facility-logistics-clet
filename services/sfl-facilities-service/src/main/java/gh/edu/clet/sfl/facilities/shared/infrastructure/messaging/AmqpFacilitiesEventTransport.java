package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

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
 * RabbitMQ transport: exchange {@code sfl.events}, routing key
 * {@code {platform}.{event-name}.v{version}}, byte-for-byte the envelope the fleet transport sends so a
 * consumer can bind both without a per-service special case.
 *
 * <p><strong>No broker-level dead-letter exchange exists.</strong> {@code sfl.events.dlx} is named in
 * the event catalog as the intended Phase 2 topology, but no code in this repository declares that
 * exchange, a queue bound to it, or the {@code x-dead-letter-exchange} queue argument that would route
 * to it - provisioning it is an operational/infrastructure decision, not something this transport does
 * on its own. What exists today is application-level: the outbox drainer retries with backoff and marks
 * a row {@code DEAD_LETTERED} after {@code max-attempts}, which stops retries and surfaces the row for
 * an operator, but never places the message on a broker queue an operator could inspect or replay from
 * at the broker.
 *
 * <p><strong>Waits for the broker, not the socket.</strong> A bare {@code RabbitTemplate.send} returns
 * once the message is written to the connection's local buffer, which is before the broker has done
 * anything with it - a dropped connection or an unroutable message would still look like success to the
 * drainer, which would then mark the outbox row {@code PUBLISHED} for an event nobody received. With
 * {@code publisher-confirm-type: correlated} and {@code publisher-returns: true} (application.yml), the
 * per-message {@link CorrelationData} carries a future that only completes once the broker acks or
 * nacks the publish, and a {@code returnedMessage} that is set if the message was mandatory-returned as
 * unroutable before the ack. {@link #send} waits on that future and treats anything short of an ack with
 * no return as a failure, which sends it back through the drainer's existing retry/backoff/dead-letter
 * path exactly like a thrown transport exception always has.
 */
final class AmqpFacilitiesEventTransport implements FacilitiesEventTransport {

    private static final Duration DEFAULT_CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final Duration confirmTimeout;

    AmqpFacilitiesEventTransport(RabbitTemplate rabbitTemplate, String exchange) {
        this(rabbitTemplate, exchange, DEFAULT_CONFIRM_TIMEOUT);
    }

    AmqpFacilitiesEventTransport(RabbitTemplate rabbitTemplate, String exchange, Duration confirmTimeout) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.confirmTimeout = confirmTimeout;
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

        CorrelationData correlationData = new CorrelationData(outboxMessage.id().toString());
        rabbitTemplate.send(exchange, routingKeyOf(outboxMessage.eventType()), message, correlationData);
        awaitConfirmation(outboxMessage, correlationData);
    }

    private void awaitConfirmation(OutboxMessage outboxMessage, CorrelationData correlationData) {
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

    /** {@code sfl.ifimp.work-order-created.v1} becomes routing key {@code ifimp.work-order-created.v1}. */
    private static String routingKeyOf(String eventType) {
        return eventType.startsWith("sfl.") ? eventType.substring("sfl.".length()) : eventType;
    }
}
