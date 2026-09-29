package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.LifecycleItem;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Which lifecycle reminders a device is owed today - SRS-SFL-S156-04: "firmware/calibration due dates raise
 * reminders".
 *
 * <p>A reminder is owed when the due date is within the lead time (or already past) and no reminder has
 * gone out for <em>that</em> due date. Keyed on the date rather than a boolean so that rescheduling a
 * calibration earns a fresh reminder for the new date, and running the sweep every hour sends one reminder
 * per due date rather than one per hour. Retired devices are owed nothing.
 */
public final class LifecycleReminderPolicy {

    private LifecycleReminderPolicy() {
    }

    public record Due(LifecycleItem item, LocalDate dueOn) {
    }

    public static List<Due> due(BmsDevice device, LocalDate today, int leadDays) {
        Objects.requireNonNull(today, "today is required");
        List<Due> owed = new ArrayList<>();
        if (!device.isActive()) {
            return owed;
        }
        LocalDate horizon = today.plusDays(Math.max(0, leadDays));
        add(owed, LifecycleItem.CALIBRATION, device.calibrationDueOn(), device.calibrationRemindedFor(), horizon);
        add(owed, LifecycleItem.FIRMWARE_REVIEW, device.firmwareReviewDueOn(), device.firmwareRemindedFor(), horizon);
        add(owed, LifecycleItem.WARRANTY_EXPIRY, device.warrantyExpiresOn(), device.warrantyRemindedFor(), horizon);
        return owed;
    }

    private static void add(List<Due> owed, LifecycleItem item, LocalDate dueOn, LocalDate remindedFor,
            LocalDate horizon) {
        if (dueOn != null && !dueOn.isAfter(horizon) && !dueOn.equals(remindedFor)) {
            owed.add(new Due(item, dueOn));
        }
    }
}
