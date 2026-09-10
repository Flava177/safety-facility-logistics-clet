package gh.edu.clet.sfl.common.web;

import java.util.Locale;

/**
 * Refuses to let a {@code rabbitmq}-transport deployment start with the RabbitMQ health indicator
 * disabled.
 *
 * <p>{@code management.health.rabbit.enabled} defaults to {@code false} in every service so a local,
 * broker-less {@code local}-transport deployment does not fail its own readiness probe over a
 * dependency it never uses. That default is silently wrong the moment a deployment sets its transport
 * to {@code rabbitmq}: the orchestrator's readiness probe would then report healthy while the service
 * has no way to see whether the broker its outbox depends on is actually reachable, and traffic keeps
 * routing to an instance whose outbox has stopped draining until someone notices the backlog directly
 * in the table.
 *
 * <p>Called from each service's own startup validator (a {@code @PostConstruct} bean, so it runs once
 * per boot and fails context refresh with a message naming the exact properties to fix, rather than
 * starting the service in a state that only degrades quietly).
 */
public final class RabbitHealthConfigurationValidator {

    private RabbitHealthConfigurationValidator() {
    }

    public static void validate(String transportPropertyName, String transport,
            String rabbitHealthPropertyName, boolean rabbitHealthEnabled) {
        boolean isRabbitMq = transport != null && "rabbitmq".equals(transport.strip().toLowerCase(Locale.ROOT));
        if (isRabbitMq && !rabbitHealthEnabled) {
            throw new IllegalStateException(
                    transportPropertyName + "=rabbitmq but " + rabbitHealthPropertyName + " is false (or unset, "
                            + "which defaults to false). A rabbitmq-backed deployment must expose broker "
                            + "connectivity through its readiness probe, or an orchestrator keeps routing "
                            + "traffic to an instance whose outbox has silently stopped draining. Set "
                            + rabbitHealthPropertyName + "=true (SFL_RABBITMQ_HEALTH_ENABLED=true), or select "
                            + "the local transport if this deployment genuinely has no broker.");
        }
    }
}
