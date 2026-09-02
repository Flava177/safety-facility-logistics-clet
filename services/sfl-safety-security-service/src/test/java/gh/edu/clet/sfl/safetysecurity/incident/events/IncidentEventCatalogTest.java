package gh.edu.clet.sfl.safetysecurity.incident.events;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.safetysecurity.incident.domain.event.IncidentEventType;
import org.junit.jupiter.api.Test;

/** Every S163 lifecycle event follows the canonical {@code sfl.ssemp.{name}.v1} naming rule - the
 * platform-wide SSEMP prefix, since S163 shares the deployable and event namespace with S174/S160. */
class IncidentEventCatalogTest {

    @Test
    void all_events_use_the_canonical_ssemp_v1_naming_rule() {
        for (IncidentEventType event : IncidentEventType.values()) {
            assertThat(event.eventType()).as("event %s", event.name()).matches("sfl\\.ssemp\\.[a-z0-9-]+\\.v1");
            assertThat(event.version()).isEqualTo(1);
        }
    }
}
