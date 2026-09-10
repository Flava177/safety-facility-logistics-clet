package gh.edu.clet.sfl.safetysecurity.emergency.config;

import gh.edu.clet.sfl.common.web.RabbitHealthConfigurationValidator;
import gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging.EmergencyEventTransport;
import gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.messaging.EmergencyEventTransports;
import java.time.Duration;
import java.util.Locale;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the emergency outbox transport from configuration, and fails loudly when it cannot.
 *
 * <p>Mirrors {@code FacilitiesMessagingConfiguration}: selecting {@code rabbitmq} without a
 * {@code RabbitTemplate}, or naming a transport that does not exist, raises at startup rather than
 * letting the service run and silently drop every emergency event. Before this class existed,
 * {@code sfl.emergency.messaging.transport} was read nowhere - the property could be set to anything
 * and the drainer's no-op {@code deliver()} would behave identically regardless.
 */
@Configuration(proxyBeanMethods = false)
class EmergencyMessagingConfiguration {

    @Bean
    EmergencyEventTransport emergencyEventTransport(
            @Value("${sfl.emergency.messaging.transport:local}") String transport,
            @Value("${sfl.emergency.messaging.exchange:sfl.events}") String exchange,
            @Value("${sfl.emergency.messaging.confirm-timeout:PT5S}") Duration confirmTimeout,
            ObjectProvider<RabbitTemplate> rabbitTemplate) {
        String selected = transport == null ? "" : transport.strip().toLowerCase(Locale.ROOT);
        return switch (selected) {
            case "rabbitmq" -> {
                RabbitTemplate template = rabbitTemplate.getIfAvailable();
                if (template == null) {
                    throw new IllegalStateException(
                            "sfl.emergency.messaging.transport=rabbitmq but no RabbitTemplate is available. "
                                    + "Configure spring.rabbitmq.* or select the local transport deliberately.");
                }
                yield EmergencyEventTransports.rabbitMq(template, exchange, confirmTimeout);
            }
            case "local" -> EmergencyEventTransports.local();
            default -> throw new IllegalStateException("Unknown sfl.emergency.messaging.transport '" + transport
                    + "'. Supported transports are: local, rabbitmq.");
        };
    }

    /**
     * Fails startup rather than let a {@code rabbitmq}-transport deployment run with the broker health
     * indicator disabled - see {@link RabbitHealthConfigurationValidator}.
     */
    @Bean
    InitializingBean rabbitHealthConfigurationCheck(
            @Value("${sfl.emergency.messaging.transport:local}") String transport,
            @Value("${management.health.rabbit.enabled:false}") boolean rabbitHealthEnabled) {
        return () -> RabbitHealthConfigurationValidator.validate("sfl.emergency.messaging.transport", transport,
                "management.health.rabbit.enabled", rabbitHealthEnabled);
    }
}
