package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Proves the transport only reports success once the broker has actually confirmed the publish - see
 * the class Javadoc on {@link AmqpFleetEventTransport}, ported from
 * {@code AmqpFacilitiesEventTransportTest}.
 *
 * <p>{@code RabbitTemplate} is mocked rather than run against a broker: what is under test here is the
 * transport's reaction to the {@link CorrelationData} future, not RabbitMQ itself.
 */
class AmqpFleetEventTransportTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(100);

    @Test
    void an_acknowledged_publish_with_no_return_succeeds() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        acknowledgeOnSend(rabbitTemplate, true, null, null);
        AmqpFleetEventTransport transport = new AmqpFleetEventTransport(rabbitTemplate, "sfl.events", SHORT_TIMEOUT);

        transport.send(message());
    }

    @Test
    void a_publish_the_broker_never_confirms_is_not_marked_sent() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        // send does nothing to the CorrelationData's future - exactly what a lost connection or a
        // broker that silently drops the frame looks like.
        AmqpFleetEventTransport transport = new AmqpFleetEventTransport(rabbitTemplate, "sfl.events", SHORT_TIMEOUT);

        assertThatThrownBy(() -> transport.send(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timed out");
    }

    @Test
    void a_nacked_publish_is_not_marked_sent() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        acknowledgeOnSend(rabbitTemplate, false, "channel closed", null);
        AmqpFleetEventTransport transport = new AmqpFleetEventTransport(rabbitTemplate, "sfl.events", SHORT_TIMEOUT);

        assertThatThrownBy(() -> transport.send(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not acknowledge");
    }

    @Test
    void a_returned_unroutable_message_is_not_marked_sent_even_though_the_broker_acked_it() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ReturnedMessage returned = new ReturnedMessage(MessageBuilder.withBody(new byte[0]).build(), 312, "NO_ROUTE",
                "sfl.events", "ftlmp.vehicle-created.v1");
        // The broker still acks a mandatory-returned message once it has processed the publish; the
        // return is the only signal that it was never routed to a queue.
        acknowledgeOnSend(rabbitTemplate, true, null, returned);
        AmqpFleetEventTransport transport = new AmqpFleetEventTransport(rabbitTemplate, "sfl.events", SHORT_TIMEOUT);

        assertThatThrownBy(() -> transport.send(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returned as unroutable");
    }

    /**
     * Stubs {@code send} to settle the {@link CorrelationData} the way {@code PublisherCallbackChannelImpl}
     * does: {@code setReturned} first (if any), then complete the confirm future.
     */
    private static void acknowledgeOnSend(RabbitTemplate rabbitTemplate, boolean ack, String reason,
            ReturnedMessage returned) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            if (returned != null) {
                correlationData.setReturned(returned);
            }
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private static OutboxMessageEntity message() {
        return new OutboxMessageEntity(UUID.randomUUID(), "sfl.ftlmp.vehicle-created.v1", 1, "Vehicle", "veh-1",
                "MAIN", "corr-1", "cause-1", "actor-1", null, 1, "{\"probe\":true}", Instant.now());
    }
}
