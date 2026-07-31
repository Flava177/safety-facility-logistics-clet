package gh.edu.clet.sfl.facilities.shared.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The catalogue rule, asserted on the guard every IFIMP event passes through.
 *
 * <p>Fleet asserts the same regex over {@code FleetEventType} because every fleet event routes through
 * one enum. Facilities publishes string literals from around fifty call sites, so the equivalent
 * assurance has to sit on the write path — which is what {@link ServiceEventType} is — and this test
 * pins the shapes that were actually wrong before the rename rather than only the shape that is right.
 */
class ServiceEventTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "sfl.ifimp.work-order-created.v1",
            "sfl.ifimp.booking-readiness-hold-placed.v1",
            "sfl.ifimp.site-operating-mode-changed.v1",
            "sfl.avamp.asset-registered.v2"
    })
    void accepts_a_catalogue_shaped_name(String eventType) {
        assertThatCode(() -> ServiceEventType.require(eventType, versionOf(eventType))).doesNotThrowAnyException();
    }

    /** Every one of these was a real event type in this service before the rename, or a near miss. */
    @ParameterizedTest
    @ValueSource(strings = {
            "ifimp.work-order.assigned",        // what facilities actually published: no prefix, no version
            "sfl.ifimp.work-order.assigned",    // dot inside the event name, so the routing key has four segments
            "sfl.ifimp.work-order-assigned",    // no version suffix
            "ifimp.work-order-assigned.v1",     // no sfl prefix
            "sfl.ifimp.work_order_assigned.v1", // underscores, which no routing key in the catalogue uses
            "sfl.ifimp.Work-Order-Assigned.v1", // upper case
            "sfl.asset.asset-registered"        // what AVAMP published: wrong platform token and no version
    })
    void refuses_everything_that_is_not(String eventType) {
        assertThatThrownBy(() -> ServiceEventType.require(eventType, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuses_a_name_whose_version_contradicts_the_stored_event_version() {
        // Two statements of one fact. A consumer that binds on the routing key and a consumer that reads
        // the column must not be able to disagree about which version they are looking at.
        assertThatThrownBy(() -> ServiceEventType.require("sfl.ifimp.work-order-created.v1", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declares v1")
                .hasMessageContaining("eventVersion 2");
    }

    @Test
    void refuses_a_null_name() {
        assertThatThrownBy(() -> ServiceEventType.require(null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static int versionOf(String eventType) {
        return Integer.parseInt(eventType.substring(eventType.lastIndexOf(".v") + 2));
    }
}
