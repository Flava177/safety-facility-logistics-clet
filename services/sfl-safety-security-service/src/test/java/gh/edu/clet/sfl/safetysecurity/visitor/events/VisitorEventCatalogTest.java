package gh.edu.clet.sfl.safetysecurity.visitor.events;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.safetysecurity.visitor.domain.event.VisitorEventType;
import org.junit.jupiter.api.Test;

/** Every S160 lifecycle event follows the canonical {@code sfl.ssemp.{name}.v1} naming rule - the
 * platform-wide SSEMP prefix, since S160 and S174 share the same deployable and event namespace. */
class VisitorEventCatalogTest {

    @Test
    void all_events_use_the_canonical_ssemp_v1_naming_rule() {
        for (VisitorEventType event : VisitorEventType.values()) {
            assertThat(event.eventType()).as("event %s", event.name()).matches("sfl\\.ssemp\\.[a-z0-9-]+\\.v1");
            assertThat(event.version()).isEqualTo(1);
        }
    }
}
