package gh.edu.clet.sfl.facilities.shared.infrastructure.messaging;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import gh.edu.clet.sfl.facilities.shared.application.integration.InboundIntegrationEvent;
import gh.edu.clet.sfl.facilities.shared.application.integration.IntegrationEventHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every inbound event, one door.
 *
 * <p>This is the plumbing half of cross-service integration, and it is written once. It receives
 * anything the queue is bound to, turns it into an {@link InboundIntegrationEvent}, makes sure it has
 * not already been handled, and gives it to whichever {@link IntegrationEventHandler} beans want it.
 * Adding a reaction to a new event is a handler bean and nothing else — no queue, no binding, no
 * listener, no parsing, no deduplication.
 *
 * <p>That separation is what stops the per-event cost people fear about event-driven systems. The
 * expensive thing is never the wiring; it is deciding what an event <em>means</em> in the receiving
 * domain, and that is business logic that has to be written whatever the architecture.
 *
 * <h2>Bound broadly, dispatched narrowly</h2>
 *
 * <p>The queue binds whole programmes ({@code ftlmp.#}), not individual event names. A topic exchange
 * makes that cheap, and it means the day someone writes a handler for
 * {@code sfl.ftlmp.fuel-anomaly-detected.v1} the message is already arriving — no infrastructure
 * change, no redeploy of the publisher, no broker administration. Unwanted events are acknowledged
 * and dropped in memory, which costs nothing.
 *
 * <h2>Claim only what is handled</h2>
 *
 * <p>The inbox row is written only when a handler actually wants the event. Recording every message
 * this service merely overheard would fill the table with rows describing nothing, and would mean a
 * handler added later found its backlog already marked as processed — silently doing nothing on
 * exactly the events it was written for.
 */
@Component
@ConditionalOnProperty(name = "sfl.facilities.messaging.transport", havingValue = "rabbitmq")
public class FacilitiesIntegrationListener {

    private static final Logger log = LoggerFactory.getLogger(FacilitiesIntegrationListener.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() {
    };

    private final List<IntegrationEventHandler> handlers;
    private final IntegrationInbox inbox;
    private final ObjectMapper json;

    /** Spring injects every handler bean; the list is the registry, so registration is declaration. */
    public FacilitiesIntegrationListener(List<IntegrationEventHandler> handlers, IntegrationInbox inbox,
            ObjectMapper json) {
        this.handlers = handlers;
        this.inbox = inbox;
        this.json = json;
    }

    @RabbitListener(queues = FacilitiesInboundMessaging.INBOUND_QUEUE)
    @Transactional
    public void onIntegrationEvent(Message message) {
        String eventType = header(message, "eventType");
        if (eventType == null) {
            log.error("Discarding an inbound message with no eventType header");
            return;
        }

        List<IntegrationEventHandler> interested = handlers.stream()
                .filter(handler -> handler.handles(eventType))
                .toList();
        if (interested.isEmpty()) {
            // Normal and not a problem: the queue is bound to whole programmes so future handlers need
            // no infrastructure change. Overheard events are simply dropped.
            log.trace("No handler for {}", eventType);
            return;
        }

        UUID messageId = messageId(message);
        if (messageId == null) {
            log.error("Discarding {} — no usable message id, so it cannot be deduplicated", eventType);
            return;
        }

        String correlationId = message.getMessageProperties().getCorrelationId();
        if (!inbox.claim(messageId, CONSUMER, eventType, correlationId)) {
            log.debug("Already handled {} ({}); acknowledging without acting", messageId, eventType);
            return;
        }

        Map<String, Object> payload;
        try {
            payload = json.readValue(message.getBody(), PAYLOAD);
        } catch (Exception unreadable) {
            // Never retryable. The claim stays committed so this cannot come back around and loop.
            log.error("Unreadable payload for {} ({})", eventType, messageId, unreadable);
            return;
        }

        InboundIntegrationEvent event = new InboundIntegrationEvent(
                messageId,
                eventType,
                header(message, "aggregateType"),
                header(message, "aggregateId"),
                header(message, "siteCode"),
                correlationId,
                header(message, "causationId"),
                payload);

        // Anything thrown here propagates: the transaction takes the claim back with it and the broker
        // redelivers, which is the behaviour a transient failure needs.
        interested.forEach(handler -> handler.handle(event));
    }

    /** One consumer name for the service; the inbox key is the message, not the handler. */
    private static final String CONSUMER = "facilities.integration";

    /** The publisher sets the outbox row id as the message id. */
    private static UUID messageId(Message message) {
        String id = message.getMessageProperties().getMessageId();
        try {
            return id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    private static String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeader(name);
        return value == null ? null : value.toString();
    }
}
