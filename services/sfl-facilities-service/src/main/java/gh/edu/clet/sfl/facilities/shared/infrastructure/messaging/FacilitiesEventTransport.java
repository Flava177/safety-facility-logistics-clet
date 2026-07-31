package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

/**
 * The wire transport the IFIMP outbox drainer delivers to.
 *
 * <p>Exactly one implementation is active, chosen by {@code sfl.facilities.messaging.transport}.
 * Resolution is explicit configuration and never a silent fallback: selecting {@code rabbitmq} without
 * a broker fails at startup rather than letting the service run while quietly dropping every
 * integration event. That distinction is the whole point of this port — the failure this package
 * exists to end was not a broker that was down, it was a drainer that did not exist while the outbox
 * filled up and everything looked healthy.
 */
public interface FacilitiesEventTransport {

    /**
     * Delivers one message. Throwing marks the attempt failed so the drainer retries with backoff and
     * eventually dead-letters — an implementation must never swallow a delivery failure, because a
     * swallowed failure is indistinguishable from a delivered message and strictly worse than an error.
     */
    void send(OutboxMessage message);

    /** Transport name, reported on the integration-health projection. */
    String name();
}
