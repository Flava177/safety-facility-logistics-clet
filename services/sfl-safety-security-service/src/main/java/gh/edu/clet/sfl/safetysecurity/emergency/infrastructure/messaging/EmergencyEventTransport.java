package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging;

/**
 * The wire transport the emergency-notification outbox drainer delivers to.
 *
 * <p>Exactly one implementation is active, chosen by {@code sfl.emergency.messaging.transport}.
 * Resolution is explicit configuration and never a silent fallback: selecting {@code rabbitmq} without
 * a broker fails at startup rather than letting the service run while quietly dropping every emergency
 * event. Mirrors {@code FacilitiesEventTransport} - same contract, same reason.
 */
public interface EmergencyEventTransport {

    /**
     * Delivers one message. Throwing marks the attempt failed so the drainer retries and eventually
     * dead-letters - an implementation must never swallow a delivery failure, because a swallowed
     * failure is indistinguishable from a delivered message and strictly worse than an error.
     */
    void send(EmergencyOutboxMessage message);

    /** Transport name, for logging and the integration-health projection. */
    String name();
}
