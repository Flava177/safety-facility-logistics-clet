package gh.edu.clet.sfl.facilities.spaceplanning.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.UtilisationSnapshot;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the S158-03 utilisation metric and signal rules - the definition the SRS leaves open. */
class UtilisationPolicyTest {

    private static final UUID ROOM = UUID.randomUUID();

    @Test
    void a_room_booked_under_20_percent_of_capacity_over_a_full_period_is_under_utilised() {
        // The acceptance criterion, worked through the defined metric: 40% of the available hours used
        // (frequency), at 39% of capacity each time (occupancy) -> 0.4 x 0.39 = 15.6%, under 20%.
        UtilisationSnapshot snapshot = snapshot(100, 4, 4 * 60, 4 * 39, 10 * 60);

        assertThat(UtilisationPolicy.underUtilised(snapshot, UtilisationPolicy.percent(20))).isTrue();
    }

    @Test
    void a_room_at_or_above_the_threshold_is_not_under_utilised() {
        UtilisationSnapshot snapshot = snapshot(100, 5, 5 * 60, 5 * 40, 10 * 60);

        assertThat(UtilisationPolicy.underUtilised(snapshot, UtilisationPolicy.percent(20))).isFalse();
    }

    @Test
    void a_room_with_no_recorded_capacity_is_not_observable() {
        UtilisationSnapshot snapshot = snapshot(null, 4, 4 * 60, 0, 10 * 60);

        assertThat(snapshot.observable()).isFalse();
        assertThat(UtilisationPolicy.underUtilised(snapshot, UtilisationPolicy.percent(20))).isFalse();
    }

    @Test
    void a_persistent_gap_requires_every_one_of_the_recent_consecutive_periods_to_show_it() {
        UtilisationSnapshot week2 = snapshot(20, 0, 0, 0, 1000, Instant.parse("2024-01-08T00:00:00Z"),
                Instant.parse("2024-01-15T00:00:00Z"), 20);
        UtilisationSnapshot week1 = snapshot(20, 0, 0, 0, 1000, Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-08T00:00:00Z"), 20);

        assertThat(UtilisationPolicy.persistentGap(List.of(week2, week1), 2, UtilisationPolicy.percent(30))).isTrue();
    }

    @Test
    void a_single_gap_period_is_not_persistent_when_two_are_required() {
        UtilisationSnapshot week2 = snapshot(20, 0, 0, 0, 1000, Instant.parse("2024-01-08T00:00:00Z"),
                Instant.parse("2024-01-15T00:00:00Z"), 20);
        // week1 matches its plan exactly (fully booked at full capacity), so only week2 shows a gap.
        UtilisationSnapshot week1 = snapshot(20, 20, 1000, 400, 1000, Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-08T00:00:00Z"), 20);

        assertThat(UtilisationPolicy.persistentGap(List.of(week2, week1), 2, UtilisationPolicy.percent(30)))
                .isFalse();
    }

    @Test
    void available_minutes_counts_only_the_configured_weekdays_and_hours() {
        UtilisationPolicy.Period week = new UtilisationPolicy.Period(Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-01-08T00:00:00Z"));

        long minutes = UtilisationPolicy.availableMinutes(week, EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 10);

        assertThat(minutes).isEqualTo(5L * 10 * 60);
    }

    @Test
    void periods_align_to_the_monday_anchor() {
        assertThat(UtilisationPolicy.isPeriodBoundary(Instant.parse("2024-01-08T00:00:00Z"), 7)).isTrue();
        assertThat(UtilisationPolicy.isPeriodBoundary(Instant.parse("2024-01-09T00:00:00Z"), 7)).isFalse();
    }

    private static UtilisationSnapshot snapshot(Integer capacity, int takenUpCount, long usedMinutes,
            long totalAttendees, long availableMinutes) {
        return snapshot(capacity, takenUpCount, usedMinutes, totalAttendees, availableMinutes,
                Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-08T00:00:00Z"), 0);
    }

    private static UtilisationSnapshot snapshot(Integer capacity, int takenUpCount, long usedMinutes,
            long totalAttendees, long availableMinutes, Instant start, Instant end, int plannedHeadcount) {
        UtilisationPolicy.Rates rates = UtilisationPolicy.rates(capacity, takenUpCount, usedMinutes, totalAttendees,
                availableMinutes);
        return new UtilisationSnapshot(UUID.randomUUID(), "MAIN", ROOM, "HALL-A", start, end, capacity, true,
                takenUpCount, takenUpCount, 0, usedMinutes, usedMinutes, availableMinutes, rates.frequencyRate(),
                rates.occupancyRate(), rates.utilisationRate(), plannedHeadcount,
                UtilisationPolicy.plannedOccupancy(plannedHeadcount, capacity), start,
                RecordMetadata.createdBy("tester", start, SourceChannel.SCHEDULER, "corr"));
    }
}
