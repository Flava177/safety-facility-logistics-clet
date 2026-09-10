package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import gh.edu.clet.sfl.common.web.RabbitHealthConfigurationValidator;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.IntegrationConfigurationNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging.FleetEventTransport;
import gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.messaging.FleetEventTransports;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the outbox transport from configuration.
 *
 * <p>Resolution fails loudly: selecting {@code rabbitmq} without a {@code RabbitTemplate}, or naming an
 * unknown transport, raises {@link IntegrationConfigurationNotFoundException} at startup rather than
 * letting the service run and silently drop every integration event (SRS-SFL-S166-04: "no fake success
 * or silent fallback").
 */
@Configuration(proxyBeanMethods = false)
class FleetMessagingConfiguration {

    @Bean
    FleetEventTransport fleetEventTransport(
            @Value("${sfl.fleet.messaging.transport:local}") String transport,
            @Value("${sfl.fleet.messaging.exchange:sfl.events}") String exchange,
            @Value("${sfl.fleet.messaging.confirm-timeout:PT5S}") Duration confirmTimeout,
            ObjectProvider<RabbitTemplate> rabbitTemplate) {
        String selected = transport == null ? "" : transport.strip().toLowerCase(Locale.ROOT);
        return switch (selected) {
            case "rabbitmq" -> {
                RabbitTemplate template = rabbitTemplate.getIfAvailable();
                if (template == null) {
                    throw new IntegrationConfigurationNotFoundException(Map.of(
                            "capability", "fleet-event-transport",
                            "configuredTransport", "rabbitmq",
                            "reason", "sfl.fleet.messaging.transport=rabbitmq but no RabbitTemplate is available"));
                }
                yield FleetEventTransports.rabbitMq(template, exchange, confirmTimeout);
            }
            case "local" -> FleetEventTransports.local();
            default -> throw new IntegrationConfigurationNotFoundException(Map.of(
                    "capability", "fleet-event-transport",
                    "configuredTransport", String.valueOf(transport),
                    "supportedTransports", "local, rabbitmq"));
        };
    }

    /**
     * Fails startup rather than let a {@code rabbitmq}-transport deployment run with the broker health
     * indicator disabled - see {@link RabbitHealthConfigurationValidator}.
     */
    @Bean
    InitializingBean rabbitHealthConfigurationCheck(
            @Value("${sfl.fleet.messaging.transport:local}") String transport,
            @Value("${management.health.rabbit.enabled:false}") boolean rabbitHealthEnabled) {
        return () -> RabbitHealthConfigurationValidator.validate("sfl.fleet.messaging.transport", transport,
                "management.health.rabbit.enabled", rabbitHealthEnabled);
    }
}
