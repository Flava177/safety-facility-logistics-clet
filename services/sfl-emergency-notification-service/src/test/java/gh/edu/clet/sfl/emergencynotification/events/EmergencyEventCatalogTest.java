package gh.edu.clet.sfl.emergencynotification.events;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.emergencynotification.domain.event.EmergencyEventType;
import org.junit.jupiter.api.Test;

/** Every S174 lifecycle event follows the canonical {@code sfl.ssemp.{name}.v1} naming rule. */
class EmergencyEventCatalogTest {

    @Test
    void all_events_use_the_canonical_ssemp_v1_naming_rule() {
        for (EmergencyEventType event : EmergencyEventType.values()) {
            assertThat(event.eventType()).as("event %s", event.name()).matches("sfl\\.ssemp\\.[a-z0-9-]+\\.v1");
            assertThat(event.version()).isEqualTo(1);
        }
    }

    @Test
    void the_two_preseeded_catalog_events_are_reused_verbatim() {
        assertThat(EmergencyEventType.EMERGENCY_NOTIFICATION_ACTIVATED.eventType())
                .isEqualTo("sfl.ssemp.emergency-notification-activated.v1");
        assertThat(EmergencyEventType.EMERGENCY_NOTIFICATION_STATUS_RECEIVED.eventType())
                .isEqualTo("sfl.ssemp.emergency-notification-status-received.v1");
    }
}
