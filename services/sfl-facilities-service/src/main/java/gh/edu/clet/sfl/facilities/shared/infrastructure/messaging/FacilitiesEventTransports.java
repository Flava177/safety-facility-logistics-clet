package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Factory for the transports, so the implementations stay package-private. */
public final class FacilitiesEventTransports {

    private FacilitiesEventTransports() {
    }

    public static FacilitiesEventTransport local() {
        return new LocalFacilitiesEventTransport();
    }

    public static FacilitiesEventTransport rabbitMq(RabbitTemplate rabbitTemplate, String exchange) {
        return new AmqpFacilitiesEventTransport(rabbitTemplate, exchange);
    }
}
