package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Proves {@link AmqpEmergencyEventTransport} against a real broker, not a mock - mirrors {@code
 * AmqpFleetEventTransportBrokerIntegrationTest} exactly, since the two transports share the same
 * confirm/return contract by design (see this class's own Javadoc). {@link
 * AmqpEmergencyEventTransportTest} already proves the transport's reaction to the {@code
 * CorrelationData} contract with a mocked {@code RabbitTemplate}; this proves a real RabbitMQ 3.13
 * broker actually fulfils that contract end to end.
 *
 * <p>Skipped, not failed, when no broker is reachable - point at one with {@value #HOST_PROPERTY}/
 * {@value #PORT_PROPERTY} (or {@value #USERNAME_PROPERTY}/{@value #PASSWORD_PROPERTY}); defaults to
 * {@code localhost:5672} with the {@code sfl}/{@code sfl} credentials the platform's own compose files
 * and dev containers use.
 */
class AmqpEmergencyEventTransportBrokerIntegrationTest {

    private static final String HOST_PROPERTY = "SFL_TEST_RABBITMQ_HOST";
    private static final String PORT_PROPERTY = "SFL_TEST_RABBITMQ_PORT";
    private static final String USERNAME_PROPERTY = "SFL_TEST_RABBITMQ_USERNAME";
    private static final String PASSWORD_PROPERTY = "SFL_TEST_RABBITMQ_PASSWORD";

    private static final String HOST = property(HOST_PROPERTY, "localhost");
    private static final int PORT = Integer.parseInt(property(PORT_PROPERTY, "5672"));
    private static final String USERNAME = property(USERNAME_PROPERTY, "sfl");
    private static final String PASSWORD = property(PASSWORD_PROPERTY, "sfl");

    private CachingConnectionFactory connectionFactory;

    static boolean brokerAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 500);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    static String unavailableReason() {
        return "No RabbitMQ broker reachable at " + HOST + ":" + PORT + ". Start one (docker start "
                + "sfl-broker, or docker run -d -p 5672:5672 -e RABBITMQ_DEFAULT_USER=sfl -e "
                + "RABBITMQ_DEFAULT_PASS=sfl rabbitmq:3.13-management) or set " + HOST_PROPERTY + "/"
                + PORT_PROPERTY + ".";
    }

    @AfterEach
    void closeConnection() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void a_publish_reaches_a_provisioned_queue_and_is_confirmed_by_a_real_broker() {
        assumeTrue(brokerAvailable(), unavailableReason());

        String exchangeName = "sfl.events.test." + UUID.randomUUID();
        String queueName = "sfl.events.test.queue." + UUID.randomUUID();
        connectionFactory = realBrokerConnectionFactory();
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        TopicExchange exchange = new TopicExchange(exchangeName, false, true);
        Queue queue = new Queue(queueName, false, false, true);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("#"));

        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        AmqpEmergencyEventTransport transport = new AmqpEmergencyEventTransport(rabbitTemplate, exchangeName,
                Duration.ofSeconds(5));
        EmergencyOutboxMessage outboxMessage = message();

        transport.send(outboxMessage);

        Message received = rabbitTemplate.receive(queueName, 2000);
        assertThat(received).as("the real broker actually routed the publish to the bound queue").isNotNull();
        assertThat(new String(received.getBody(), java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(outboxMessage.payload());
        Object aggregateIdHeader = received.getMessageProperties().getHeader("aggregateId");
        assertThat(aggregateIdHeader).isEqualTo(outboxMessage.aggregateId());

        admin.deleteQueue(queueName);
        admin.deleteExchange(exchangeName);
    }

    @Test
    void publishing_to_an_undeclared_exchange_fails_loudly_rather_than_silently_succeeding() {
        assumeTrue(brokerAvailable(), unavailableReason());

        // No RabbitAdmin.declareExchange call here, deliberately: this transport never declares
        // sfl.events itself (see AmqpFleetEventTransport's Javadoc, which this transport shares the
        // topology contract with) - a deployment that forgets to provision the exchange out-of-band
        // must fail loudly, not drop events silently.
        String neverDeclaredExchange = "sfl.events.never.declared." + UUID.randomUUID();
        connectionFactory = realBrokerConnectionFactory();
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        AmqpEmergencyEventTransport transport = new AmqpEmergencyEventTransport(rabbitTemplate,
                neverDeclaredExchange, Duration.ofSeconds(5));

        assertThatThrownBy(() -> transport.send(message()));
    }

    private static CachingConnectionFactory realBrokerConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(HOST, PORT);
        factory.setUsername(USERNAME);
        factory.setPassword(PASSWORD);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    private static EmergencyOutboxMessage message() {
        return new EmergencyOutboxMessage(UUID.randomUUID(), "sfl.ssemp.emergency-notification-activated.v1", 1,
                "EmergencyActivation", "act-broker-it-1", "MAIN", "corr-broker-it-1", "cause-broker-it-1",
                "{\"probe\":\"broker-integration\"}");
    }

    private static String property(String name, String fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
