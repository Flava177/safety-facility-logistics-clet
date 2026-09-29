package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsAlert;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ChannelState;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.HealthState;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/**
 * One device's health - SRS-SFL-S156-03.
 *
 * <h2>No green default</h2>
 *
 * <p>The validation rule is the whole design: "an offline sensor must not be indistinguishable from a
 * 'normal' reading - an explicit unknown/stale state is always shown rather than defaulting to green". So
 * {@link HealthState#NORMAL} is the one state that has to be <em>earned</em>: the device has reported
 * within its staleness window, and nothing it reported is in breach or fault. A device that has never
 * reported, or has gone quiet, is {@link HealthState#UNKNOWN}. The code path that would return NORMAL for
 * a device with no data does not exist.
 *
 * <h2>The order of precedence</h2>
 *
 * <ol>
 *   <li>An active offline alert - {@code OFFLINE}. The sweep has already decided it is gone.</li>
 *   <li>No reading within {@code staleAfterIntervals × expectedInterval} - {@code UNKNOWN} (Stale Data).
 *       Checked before faults, because a stale "fault" is no more trustworthy than a stale "normal".</li>
 *   <li>An active critical alert, or any active alert of HIGH or CRITICAL priority - {@code FAULT}.</li>
 *   <li>A lower-priority active alert, or a breach still inside its debounce window - {@code DEGRADED}.</li>
 *   <li>Otherwise - {@code NORMAL}.</li>
 * </ol>
 */
public final class DeviceHealthPolicy {

    private DeviceHealthPolicy() {
    }

    public record DeviceHealth(HealthState state, String reason, Instant lastObservedAt) {
    }

    public static DeviceHealth assess(BmsDevice device, Collection<ChannelState> channels,
            Collection<BmsAlert> activeAlerts, Instant now, int staleAfterIntervals) {
        Objects.requireNonNull(device, "device is required");
        Instant lastObserved = channels.stream()
                .map(ChannelState::lastObservedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        if (activeAlerts.stream().anyMatch(alert -> alert.type() == AlertType.SENSOR_OFFLINE)) {
            return new DeviceHealth(HealthState.OFFLINE, "Offline alert active - no telemetry within the offline window",
                    lastObserved);
        }
        if (lastObserved == null) {
            return new DeviceHealth(HealthState.UNKNOWN, "No reading has ever been received from this device", null);
        }
        Duration staleAfter = Duration.ofSeconds((long) device.expectedIntervalSeconds() * Math.max(1, staleAfterIntervals));
        if (lastObserved.plus(staleAfter).isBefore(now)) {
            return new DeviceHealth(HealthState.UNKNOWN,
                    "Stale data - no reading within the expected interval; shown as unknown, not healthy", lastObserved);
        }
        if (activeAlerts.stream().anyMatch(alert -> alert.type() == AlertType.CRITICAL_FAULT
                || alert.priority().atLeast(AlertPriority.HIGH))) {
            return new DeviceHealth(HealthState.FAULT, "Active fault alert", lastObserved);
        }
        if (!activeAlerts.isEmpty()) {
            return new DeviceHealth(HealthState.DEGRADED, "Active alert", lastObserved);
        }
        if (channels.stream().anyMatch(ChannelState::inBreach)) {
            return new DeviceHealth(HealthState.DEGRADED, "Threshold breach inside its debounce window", lastObserved);
        }
        return new DeviceHealth(HealthState.NORMAL, "Reporting within its expected interval", lastObserved);
    }
}
