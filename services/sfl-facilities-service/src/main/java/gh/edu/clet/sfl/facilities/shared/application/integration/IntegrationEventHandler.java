package gh.edu.clet.sfl.facilities.shared.application.integration;

/**
 * What this service does when another service says something happened.
 *
 * <p>Adding a cross-service reaction is one class implementing this interface. No queue, no binding,
 * no listener, no deduplication, no JSON — all of that is generic and already done, once, in
 * {@code FacilitiesIntegrationListener}. A handler is only the decision.
 *
 * <h2>Why the reaction is per-edge and the plumbing is not</h2>
 *
 * <p>It is worth being clear about which part of an event-driven platform is repetitive and which is
 * not, because the answer decides how much work the enterprise broker costs later.
 *
 * <p><strong>Publishing costs nothing per event.</strong> Every service already writes to an outbox
 * and a drainer ships it; a new event type is a new enum constant, not new plumbing.
 *
 * <p><strong>Changing broker costs one class per service.</strong> The transport is a single-method
 * port. Moving from RabbitMQ to Kafka is a Kafka implementation of it — three classes across the
 * platform, whether there are six events or six hundred.
 *
 * <p><strong>Reacting costs one class per reaction, and that is not overhead.</strong> "A vehicle is
 * due for service" becoming "raise a fault at MEDIUM priority against the registration" is a
 * maintenance policy decision. Nobody can generate it, and it is the same code you would write if the
 * two systems were one monolith — it just lives behind an interface instead of a method call.
 *
 * <p>What would be overhead is copying the deduplication, the parsing and the queue wiring into every
 * one of those decisions. That is what this interface exists to stop.
 */
public interface IntegrationEventHandler {

    /**
     * Whether this handler wants the event.
     *
     * <p>A predicate rather than a single event name so one handler can take a family — the due and
     * overdue variants of the same fact, say — without being registered twice.
     */
    boolean handles(String eventType);

    /**
     * Acts on the event.
     *
     * <p>Called inside the listener's transaction, after the inbox has confirmed this is the first
     * delivery. Throw for anything worth retrying: the transaction rolls the inbox claim back with it
     * and the broker redelivers. Do not throw for a payload that can never be handled — log it and
     * return, or it will loop.
     */
    void handle(InboundIntegrationEvent event);
}
