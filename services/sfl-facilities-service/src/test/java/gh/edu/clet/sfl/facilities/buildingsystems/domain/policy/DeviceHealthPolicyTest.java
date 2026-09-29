package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ChannelState;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceKind;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.HealthState;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * SRS-SFL-S156-03: "an offline sensor must not be indistinguishable from a normal reading" - no green
 * default.
 */
class DeviceHealthPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");

    @Test
    void a_device_that_has_never_reported_is_unknown_never_normal() {
        BmsDevice device = device();

        DeviceHealthPolicy.DeviceHealth health = DeviceHealthPolicy.assess(device, List.of(), List.of(), NOW, 2);

        assertThat(health.state()).isEqualTo(HealthState.UNKNOWN);
    }

    @Test
    void a_device_reporting_within_its_expected_interval_and_no_alert_is_normal() {
        BmsDevice device = device();
        ChannelState state = channelState(device, NOW.minusSeconds(30));

        DeviceHealthPolicy.DeviceHealth health = DeviceHealthPolicy.assess(device, List.of(state), List.of(), NOW, 2);

        assertThat(health.state()).isEqualTo(HealthState.NORMAL);
    }

    @Test
    void a_device_stale_beyond_its_configured_multiple_of_the_interval_is_unknown() {
        BmsDevice device = device();
        // expectedIntervalSeconds = 60, staleAfterIntervals = 2 -> stale after 120s.
        ChannelState state = channelState(device, NOW.minus(java.time.Duration.ofSeconds(300)));

        DeviceHealthPolicy.DeviceHealth health = DeviceHealthPolicy.assess(device, List.of(state), List.of(), NOW, 2);

        assertThat(health.state()).isEqualTo(HealthState.UNKNOWN);
        assertThat(health.reason()).contains("Stale data");
    }

    @Test
    void an_active_offline_alert_reports_offline_even_if_a_reading_is_recent() {
        BmsDevice device = device();
        ChannelState state = channelState(device, NOW.minusSeconds(10));
        BmsAlert offline = BmsAlert.raise(UUID.randomUUID(), device, AlertType.SENSOR_OFFLINE, null, null,
                AlertPriority.MEDIUM, "offline", List.of(), "system", NOW, SourceChannel.SCHEDULER, "corr");

        DeviceHealthPolicy.DeviceHealth health = DeviceHealthPolicy.assess(device, List.of(state), List.of(offline),
                NOW, 2);

        assertThat(health.state()).isEqualTo(HealthState.OFFLINE);
    }

    @Test
    void a_high_priority_active_alert_reports_fault() {
        BmsDevice device = device();
        ChannelState state = channelState(device, NOW.minusSeconds(10));
        BmsAlert alert = BmsAlert.raise(UUID.randomUUID(), device, AlertType.THRESHOLD_BREACH, null, null,
                AlertPriority.HIGH, "hot", List.of(), "system", NOW, SourceChannel.SCHEDULER, "corr");

        DeviceHealthPolicy.DeviceHealth health = DeviceHealthPolicy.assess(device, List.of(state), List.of(alert),
                NOW, 2);

        assertThat(health.state()).isEqualTo(HealthState.FAULT);
    }

    @Test
    void worst_of_takes_the_most_severe_state() {
        assertThat(HealthState.worstOf(List.of(HealthState.NORMAL, HealthState.UNKNOWN))).isEqualTo(HealthState.UNKNOWN);
        assertThat(HealthState.worstOf(List.of())).isEqualTo(HealthState.UNKNOWN);
        assertThat(HealthState.worstOf(List.of(HealthState.DEGRADED, HealthState.FAULT))).isEqualTo(HealthState.FAULT);
    }

    private static BmsDevice device() {
        return BmsDevice.register(UUID.randomUUID(), "MAIN", "AHU-01", "AVAMP-1", "AHU 1", BuildingSystemType.HVAC,
                DeviceKind.SENSOR, "LAW", null, null, 60, null, null, null, null, null, "engineer", NOW,
                SourceChannel.WEB, "corr-1");
    }

    private static ChannelState channelState(BmsDevice device, Instant lastObservedAt) {
        ChannelState opened = ChannelState.open(UUID.randomUUID(), device, "supply-air-temp",
                gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity.TEMPERATURE_C, "engineer", NOW,
                SourceChannel.WEB, "corr-1");
        return opened.observe(UUID.randomUUID(), new java.math.BigDecimal("21"), lastObservedAt, NOW, "engineer",
                SourceChannel.WEB, "corr-1");
    }
}
