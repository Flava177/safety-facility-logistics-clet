package gh.edu.clet.sfl.fleetlogistics.fleet.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the event-naming reconciliation recorded as conflict C-03: fleet events follow
 * {@code docs/integration/event-catalog.md} — {@code sfl.{platform}.{event-name}.v{version}} — and the
 * numeric version can never drift from the {@code .vN} suffix.
 *
 * <p>Traces: SRS-SFL-S166-01/02/03/04 change-event publication.
 */
class FleetEventTypeTest {

    private static final Pattern CATALOG_NAMING = Pattern.compile("^sfl\\.ftlmp\\.[a-z0-9]+(-[a-z0-9]+)*\\.v\\d+$");

    @Test
    @DisplayName("every fleet event follows the catalog naming rule")
    void every_event_follows_the_catalog_naming_rule() {
        for (FleetEventType type : FleetEventType.values()) {
            assertThat(type.eventType())
                    .as("event type for %s", type)
                    .matches(CATALOG_NAMING);
        }
    }

    @Test
    @DisplayName("the numeric version mirrors the .vN suffix")
    void numeric_version_mirrors_the_suffix() {
        for (FleetEventType type : FleetEventType.values()) {
            assertThat(type.eventType()).endsWith(".v" + type.version());
        }
    }

    @Test
    @DisplayName("the routing key drops the sfl prefix, as the catalog requires")
    void routing_key_drops_the_sfl_prefix() {
        assertThat(FleetEventType.VEHICLE_CREATED.routingKey()).isEqualTo("ftlmp.vehicle-created.v1");
        assertThat(FleetEventType.VEHICLE_READINESS_CHANGED.routingKey())
                .isEqualTo("ftlmp.vehicle-readiness-changed.v1");
    }

    @Test
    @DisplayName("the three events already in the catalog keep their published names")
    void pre_existing_catalog_events_keep_their_names() {
        assertThat(FleetEventType.VEHICLE_CREATED.eventType()).isEqualTo("sfl.ftlmp.vehicle-created.v1");
        assertThat(FleetEventType.VEHICLE_READINESS_CHANGED.eventType())
                .isEqualTo("sfl.ftlmp.vehicle-readiness-changed.v1");
        assertThat(FleetEventType.VEHICLE_LOCATION_RECEIVED.eventType())
                .isEqualTo("sfl.ftlmp.vehicle-location-received.v1");
    }

    @Test
    @DisplayName("event types are unique")
    void event_types_are_unique() {
        assertThat(java.util.Arrays.stream(FleetEventType.values()).map(FleetEventType::eventType).distinct().count())
                .isEqualTo(FleetEventType.values().length);
    }

    @Test
    @DisplayName("every event declares the aggregate type it belongs to")
    void every_event_declares_an_aggregate_type() {
        for (FleetEventType type : FleetEventType.values()) {
            assertThat(type.defaultAggregateType()).as("aggregate type for %s", type).isNotBlank();
        }
    }
}
