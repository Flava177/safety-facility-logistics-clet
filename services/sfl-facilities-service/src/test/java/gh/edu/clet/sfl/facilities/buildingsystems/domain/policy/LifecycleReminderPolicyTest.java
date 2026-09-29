package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.BmsDevice;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.DeviceKind;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.LifecycleItem;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S156-04: "firmware/calibration due dates raise reminders", once per due date. */
class LifecycleReminderPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-28T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 28);

    @Test
    void a_calibration_due_within_the_lead_time_is_owed() {
        BmsDevice device = device(TODAY.plusDays(10), null, null);

        var due = LifecycleReminderPolicy.due(device, TODAY, 30);

        assertThat(due).extracting(LifecycleReminderPolicy.Due::item).containsExactly(LifecycleItem.CALIBRATION);
    }

    @Test
    void a_due_date_beyond_the_lead_time_is_not_owed_yet() {
        BmsDevice device = device(TODAY.plusDays(60), null, null);

        assertThat(LifecycleReminderPolicy.due(device, TODAY, 30)).isEmpty();
    }

    @Test
    void an_overdue_date_is_owed() {
        BmsDevice device = device(TODAY.minusDays(5), null, null);

        var due = LifecycleReminderPolicy.due(device, TODAY, 30);

        assertThat(due).hasSize(1);
    }

    @Test
    void a_date_already_reminded_for_is_not_owed_again() {
        BmsDevice base = device(TODAY.plusDays(5), null, null);
        BmsDevice reminded = base.withReminderRaised(LifecycleItem.CALIBRATION, TODAY.plusDays(5), "system", NOW,
                SourceChannel.SCHEDULER, "corr");

        assertThat(LifecycleReminderPolicy.due(reminded, TODAY, 30)).isEmpty();
    }

    @Test
    void moving_the_due_date_earns_a_fresh_reminder() {
        BmsDevice base = device(TODAY.plusDays(5), null, null);
        BmsDevice reminded = base.withReminderRaised(LifecycleItem.CALIBRATION, TODAY.plusDays(5), "system", NOW,
                SourceChannel.SCHEDULER, "corr");
        BmsDevice rescheduled = reminded.revise(reminded.name(), reminded.buildingCode(), reminded.roomId(),
                reminded.roomCode(), reminded.expectedIntervalSeconds(), reminded.installedOn(),
                reminded.firmwareVersion(), reminded.firmwareReviewDueOn(), reminded.warrantyExpiresOn(),
                TODAY.plusDays(6), "engineer", NOW, SourceChannel.WEB, "corr-2");

        assertThat(LifecycleReminderPolicy.due(rescheduled, TODAY, 30)).hasSize(1);
    }

    @Test
    void a_retired_device_owes_no_reminders() {
        BmsDevice device = device(TODAY.plusDays(5), null, null)
                .retire("decommissioned", "engineer", NOW, SourceChannel.WEB, "corr");

        assertThat(LifecycleReminderPolicy.due(device, TODAY, 30)).isEmpty();
    }

    private static BmsDevice device(LocalDate calibration, LocalDate firmware, LocalDate warranty) {
        return BmsDevice.register(UUID.randomUUID(), "MAIN", "AHU-01", "AVAMP-1", "AHU 1", BuildingSystemType.HVAC,
                DeviceKind.SENSOR, "LAW", null, null, 60, null, null, firmware, warranty, calibration, "engineer", NOW,
                SourceChannel.WEB, "corr-1");
    }
}
