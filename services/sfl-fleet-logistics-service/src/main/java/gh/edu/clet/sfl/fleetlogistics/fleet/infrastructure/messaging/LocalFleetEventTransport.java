package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local transport used when no broker is provisioned for the environment
 * ({@code sfl.fleet.messaging.transport=local}).
 *
 * <p>This is a real delivery target, not a stub that pretends: the message is logged with its full
 * routing metadata and retained in memory for tests and local verification. The integration-health
 * projection reports the transport name so an operator can always see that events are not reaching a
 * broker. Production environments must set the transport to {@code rabbitmq}; the startup guard in
 * {@code FleetMessagingConfiguration} refuses to start if that is selected without a broker.
 */
class LocalFleetEventTransport implements FleetEventTransport {

    private static final Logger log = LoggerFactory.getLogger(LocalFleetEventTransport.class);
    private static final int RETAINED_MESSAGES = 500;

    private final List<String> delivered = new CopyOnWriteArrayList<>();

    @Override
    public void send(OutboxMessageEntity message) {
        log.info("Fleet event delivered to the local transport: type={} routingKey={} aggregate={}:{} site={} "
                        + "correlationId={}",
                message.eventType(), routingKeyOf(message.eventType()), message.aggregateType(),
                message.aggregateId(), message.siteScope(), message.correlationId());
        delivered.add(message.eventType() + "|" + message.aggregateId());
        while (delivered.size() > RETAINED_MESSAGES) {
            delivered.remove(0);
        }
    }

    @Override
    public String name() {
        return "local";
    }

    List<String> delivered() {
        return List.copyOf(delivered);
    }

    private static String routingKeyOf(String eventType) {
        return eventType.startsWith("sfl.") ? eventType.substring("sfl.".length()) : eventType;
    }
}
