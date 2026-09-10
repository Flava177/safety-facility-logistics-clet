package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging;

import java.time.Duration;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** Factory for the transports, so the implementations stay package-private. */
public final class EmergencyEventTransports {

    private EmergencyEventTransports() {
    }

    public static EmergencyEventTransport local() {
        return new LocalEmergencyEventTransport();
    }

    public static EmergencyEventTransport rabbitMq(RabbitTemplate rabbitTemplate, String exchange,
            Duration confirmTimeout) {
        return new AmqpEmergencyEventTransport(rabbitTemplate, exchange, confirmTimeout);
    }
}
