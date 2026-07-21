package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Factory for the package-private transports, so the configuration class in {@code fleet.config} can
 * select one without the transport implementations becoming part of the public surface.
 */
public final class FleetEventTransports {

    private FleetEventTransports() {
    }

    public static FleetEventTransport rabbitMq(RabbitTemplate rabbitTemplate, String exchange) {
        return new AmqpFleetEventTransport(rabbitTemplate, exchange);
    }

    public static FleetEventTransport local() {
        return new LocalFleetEventTransport();
    }
}
