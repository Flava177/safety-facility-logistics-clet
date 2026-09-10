package gh.edu.clet.sfl.common.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RabbitHealthConfigurationValidatorTest {

    @Test
    void a_rabbitmq_deployment_with_health_disabled_fails_fast() {
        assertThatThrownBy(() -> RabbitHealthConfigurationValidator.validate(
                "sfl.facilities.messaging.transport", "rabbitmq", "management.health.rabbit.enabled", false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sfl.facilities.messaging.transport=rabbitmq")
                .hasMessageContaining("management.health.rabbit.enabled");
    }

    @Test
    void a_rabbitmq_deployment_with_health_enabled_is_fine() {
        assertThatCode(() -> RabbitHealthConfigurationValidator.validate(
                "sfl.facilities.messaging.transport", "rabbitmq", "management.health.rabbit.enabled", true))
                .doesNotThrowAnyException();
    }

    @Test
    void a_local_deployment_needs_no_broker_health_check_either_way() {
        assertThatCode(() -> RabbitHealthConfigurationValidator.validate(
                "sfl.facilities.messaging.transport", "local", "management.health.rabbit.enabled", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> RabbitHealthConfigurationValidator.validate(
                "sfl.facilities.messaging.transport", "local", "management.health.rabbit.enabled", true))
                .doesNotThrowAnyException();
    }

    @Test
    void the_transport_check_is_case_and_whitespace_tolerant() {
        assertThatThrownBy(() -> RabbitHealthConfigurationValidator.validate(
                "sfl.facilities.messaging.transport", "  RabbitMQ  ", "management.health.rabbit.enabled", false))
                .isInstanceOf(IllegalStateException.class);
    }
}
