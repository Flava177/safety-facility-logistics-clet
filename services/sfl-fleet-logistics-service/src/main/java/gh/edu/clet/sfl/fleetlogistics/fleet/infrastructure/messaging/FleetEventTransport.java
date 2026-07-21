package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging;

/**
 * The wire transport the outbox drainer delivers to.
 *
 * <p>Exactly one implementation is active, chosen by {@code sfl.fleet.messaging.transport}. Resolution
 * is explicit configuration, never a silent fallback: selecting {@code rabbitmq} without a broker
 * fails at startup rather than quietly dropping events.
 */
public interface FleetEventTransport {

    /**
     * Delivers one message. Throwing marks the attempt failed so the drainer can retry with backoff and
     * eventually dead-letter — implementations must never swallow a delivery failure.
     */
    void send(OutboxMessageEntity message);

    /** Transport name reported on the integration-health projection. */
    String name();
}
