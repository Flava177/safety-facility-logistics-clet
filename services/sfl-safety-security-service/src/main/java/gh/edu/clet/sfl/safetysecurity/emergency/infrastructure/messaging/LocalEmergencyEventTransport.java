package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records delivery without a broker. The development and single-node default.
 *
 * <p>Logs and returns, which marks the row published. That is honest only because it is named
 * {@code local} and selected deliberately: it claims the message left this service, not that anything
 * received it - identical in shape and in that caveat to {@code LocalFacilitiesEventTransport}. Any
 * environment where a real consumer is expected must select {@code rabbitmq}.
 *
 * <p>Logs the full envelope rather than just the message id, unlike the no-op this replaces: an
 * operator watching logs for what this service is "delivering" locally could previously not tell one
 * message from another.
 */
final class LocalEmergencyEventTransport implements EmergencyEventTransport {

    private static final Logger log = LoggerFactory.getLogger(LocalEmergencyEventTransport.class);

    @Override
    public void send(EmergencyOutboxMessage message) {
        log.debug("Recorded local delivery of {} for {} {}", message.eventType(), message.aggregateType(),
                message.aggregateId());
    }

    @Override
    public String name() {
        return "local";
    }
}
