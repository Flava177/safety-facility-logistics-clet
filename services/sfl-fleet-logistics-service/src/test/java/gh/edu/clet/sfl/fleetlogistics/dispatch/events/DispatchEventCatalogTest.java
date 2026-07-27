package gh.edu.clet.sfl.fleetlogistics.dispatch.events;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every S171 dispatch lifecycle event follows the canonical {@code sfl.ftlmp.{name}.v1} naming rule. */
class DispatchEventCatalogTest {

    private static final List<FleetEventType> DISPATCH_EVENTS = Arrays.stream(FleetEventType.values())
            .filter(e -> e.name().startsWith("DISPATCH_") || e.name().startsWith("INBOUND_")
                    || e.name().startsWith("CUSTODY_"))
            .toList();

    @Test
    void dispatch_events_use_the_canonical_ftlmp_v1_naming_rule() {
        assertThat(DISPATCH_EVENTS).isNotEmpty();
        for (FleetEventType event : DISPATCH_EVENTS) {
            assertThat(event.eventType())
                    .as("event %s", event.name())
                    .matches("sfl\\.ftlmp\\.[a-z0-9-]+\\.v1");
            assertThat(event.version()).isEqualTo(1);
        }
    }

    @Test
    void the_two_preseeded_catalog_events_are_reused_verbatim() {
        assertThat(FleetEventType.DISPATCH_CREATED.eventType()).isEqualTo("sfl.ftlmp.dispatch-created.v1");
        assertThat(FleetEventType.DISPATCH_RECEIVED.eventType()).isEqualTo("sfl.ftlmp.dispatch-received.v1");
    }
}
