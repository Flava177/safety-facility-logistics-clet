package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The half of the chain that had never been built.
 *
 * <p>Every service could already publish: a business change and its event commit in one transaction,
 * a drainer picks it up, retries with backoff and dead-letters what will not go. Nothing could
 * receive. Every {@code inbox_messages} table existed and every one was empty, because no code in any
 * of the three services subscribed to anything — the sagas were designed, contracted and catalogued,
 * and then not connected.
 *
 * <h2>One queue, bound by programme</h2>
 *
 * <p>Publishers send to one topic exchange, {@code sfl.events}, with the event type as the routing key
 * minus its {@code sfl.} prefix — {@code sfl.ftlmp.vehicle-service-due.v1} routes as
 * {@code ftlmp.vehicle-service-due.v1}.
 *
 * <p>This binds {@code ftlmp.#} and {@code ssemp.#}: everything the other two programmes publish,
 * rather than a list of the events facilities happens to react to today. That is deliberate. Binding
 * per event name means every new reaction needs a broker change as well as a handler, and broker
 * changes are the kind that get forgotten between environments — the handler ships, nothing arrives,
 * and it looks like a code bug. Binding by programme means writing a handler is the only step.
 *
 * <p>The cost is that facilities receives events nobody handles. {@code FacilitiesIntegrationListener}
 * drops those without touching the database, and a topic exchange filtering in the broker is far
 * cheaper than the alternative of getting the bindings wrong.
 *
 * <h2>Why it is conditional</h2>
 *
 * <p>Declared only when the transport is {@code rabbitmq}. With the default {@code local} transport
 * there is no broker, and a service trying to declare a queue against nothing would fail to start —
 * turning "no broker provisioned yet", the expected Phase 1 state, into a dead service.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sfl.facilities.messaging.transport", havingValue = "rabbitmq")
public class FacilitiesInboundMessaging {

    /** One inbound queue for the service. Named for its owner, so the broker is readable. */
    public static final String INBOUND_QUEUE = "sfl.ifimp.inbound";

    /**
     * Whole programmes, not event names — see the class docblock.
     *
     * <p>AVAMP publishes as {@code asset.*} and lives in the fleet service; add it here when facilities
     * has a reason to care, which today it does not.
     */
    private static final List<String> ROUTING_PATTERNS = List.of("ftlmp.#", "ssemp.#");

    @Bean
    Declarables facilitiesInboundTopology(
            @Value("${sfl.facilities.messaging.exchange:sfl.events}") String exchangeName) {
        TopicExchange exchange = new TopicExchange(exchangeName, true, false);
        // Durable: an event published while facilities is restarting is still true when it comes back,
        // and holding it is the whole reason for a broker rather than an HTTP call.
        Queue queue = QueueBuilder.durable(INBOUND_QUEUE).build();
        Stream<Binding> bindings = ROUTING_PATTERNS.stream()
                .map(pattern -> BindingBuilder.bind(queue).to(exchange).with(pattern));
        return new Declarables(Stream.concat(Stream.of(exchange, queue), bindings).toList());
    }
}
