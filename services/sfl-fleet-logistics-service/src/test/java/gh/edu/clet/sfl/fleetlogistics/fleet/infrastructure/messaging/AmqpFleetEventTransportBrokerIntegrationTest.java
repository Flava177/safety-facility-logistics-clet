package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Proves {@link AmqpFleetEventTransport} against a real broker, not a mock - {@link
 * AmqpFleetEventTransportTest} proves the transport's reaction to the {@code CorrelationData} contract;
 * this proves that contract is actually what a real RabbitMQ 3.13 broker fulfils when
 * {@code publisher-confirm-type: correlated} / {@code publisher-returns: true} are configured the way
 * {@code application.yml} configures them, and that the confirm/return callbacks Spring Boot's
 * autoconfiguration wires up behave the same way when built by hand here.
 *
 * <p>Skipped, not failed, when no broker is reachable - see {@link #brokerAvailable()}. Point at one with
 * {@value #HOST_PROPERTY}/{@value #PORT_PROPERTY} (or {@value #USERNAME_PROPERTY}/
 * {@value #PASSWORD_PROPERTY}), matching the escape hatch {@code FleetPostgresSupport} uses for Postgres;
 * defaults to {@code localhost:5672} with the {@code sfl}/{@code sfl} credentials the platform's own
 * compose files and dev containers use.
 */
class AmqpFleetEventTransportBrokerIntegrationTest {

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
    void a_publish_reaches_a_provisioned_queue_and_is_confirmed_by_a_real_broker() throws InterruptedException {
        assumeTrue(brokerAvailable(), unavailableReason());

        String exchangeName = "sfl.events.test." + UUID.randomUUID();
        String queueName = "sfl.events.test.queue." + UUID.randomUUID();
        connectionFactory = realBrokerConnectionFactory();
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        TopicExchange exchange = new TopicExchange(exchangeName, false, true);
        Queue queue = new Queue(queueName, false, false, true);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(org.springframework.amqp.core.BindingBuilder.bind(queue).to(exchange)
                .with("#"));

        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        AmqpFleetEventTransport transport = new AmqpFleetEventTransport(rabbitTemplate, exchangeName,
                Duration.ofSeconds(5));
        OutboxMessageEntity outboxMessage = message();

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

        // No RabbitAdmin.declareExchange call here, deliberately: this repository's transports never
        // declare sfl.events themselves (see AmqpFleetEventTransport's own class Javadoc) - a deployment
        // that forgets to provision the exchange out-of-band must fail loudly, not drop events silently.
        String neverDeclaredExchange = "sfl.events.never.declared." + UUID.randomUUID();
        connectionFactory = realBrokerConnectionFactory();
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        AmqpFleetEventTransport transport = new AmqpFleetEventTransport(rabbitTemplate, neverDeclaredExchange,
                Duration.ofSeconds(5));

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

    private static OutboxMessageEntity message() {
        return new OutboxMessageEntity(UUID.randomUUID(), "sfl.ftlmp.vehicle-created.v1", 1, "Vehicle",
                "veh-broker-it-1", "MAIN", "corr-broker-it-1", "cause-broker-it-1", "actor-broker-it-1", null, 1,
                "{\"probe\":\"broker-integration\"}", Instant.now());
    }

    private static String property(String name, String fallback) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
