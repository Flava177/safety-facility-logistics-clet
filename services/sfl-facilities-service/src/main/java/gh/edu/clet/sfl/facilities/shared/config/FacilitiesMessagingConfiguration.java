package gh.edu.clet.sfl.facilities.shared.config;

import gh.edu.clet.sfl.facilities.shared.infrastructure.messaging.FacilitiesEventTransport;
import gh.edu.clet.sfl.facilities.shared.infrastructure.messaging.FacilitiesEventTransports;
import java.util.Locale;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the outbox transport from configuration, and fails loudly when it cannot.
 *
 * <p>Selecting {@code rabbitmq} without a {@code RabbitTemplate}, or naming a transport that does not
 * exist, raises at startup rather than letting the service run and silently drop every integration
 * event. That is the same rule the fleet service applies, and it matters more here than anywhere else
 * in the platform: IFIMP has just spent three build passes writing outbox rows that nothing published,
 * and the failure was invisible precisely because nothing complained.
 */
@Configuration(proxyBeanMethods = false)
class FacilitiesMessagingConfiguration {

    @Bean
    FacilitiesEventTransport facilitiesEventTransport(
            @Value("${sfl.facilities.messaging.transport:local}") String transport,
            @Value("${sfl.facilities.messaging.exchange:sfl.events}") String exchange,
            ObjectProvider<RabbitTemplate> rabbitTemplate) {
        String selected = transport == null ? "" : transport.strip().toLowerCase(Locale.ROOT);
        return switch (selected) {
            case "rabbitmq" -> {
                RabbitTemplate template = rabbitTemplate.getIfAvailable();
                if (template == null) {
                    throw new IllegalStateException(
                            "sfl.facilities.messaging.transport=rabbitmq but no RabbitTemplate is available. "
                                    + "Configure spring.rabbitmq.* or select the local transport deliberately.");
                }
                yield FacilitiesEventTransports.rabbitMq(template, exchange);
            }
            case "local" -> FacilitiesEventTransports.local();
            default -> throw new IllegalStateException("Unknown sfl.facilities.messaging.transport '" + transport
                    + "'. Supported transports are: local, rabbitmq.");
        };
    }
}
