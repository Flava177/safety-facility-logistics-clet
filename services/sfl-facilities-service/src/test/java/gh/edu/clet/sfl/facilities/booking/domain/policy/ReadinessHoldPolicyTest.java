package gh.edu.clet.sfl.facilities.booking.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.ReadinessHoldReason;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import org.junit.jupiter.api.Test;

/**
 * The readiness rule, which is asked at request time as a refusal and afterwards as a hold.
 *
 * <p>One function serving both is the design; these tests are what stop it quietly becoming two.
 */
class ReadinessHoldPolicyTest {

    @Test
    void an_ordinary_booking_on_an_unassessed_space_is_fine() {
        assertThat(hold(BookingPurpose.LECTURE, LocationReadinessStatus.UNKNOWN, true, true, true, false))
                .isNull();
    }

    /** S152 already decided this: a hall with one failed projector is usable. */
    @Test
    void an_ordinary_booking_tolerates_a_degraded_space() {
        assertThat(hold(BookingPurpose.LECTURE, LocationReadinessStatus.DEGRADED, true, true, true, false))
                .isNull();
    }

    @Test
    void a_blocked_space_stops_every_booking() {
        assertThat(hold(BookingPurpose.LECTURE, LocationReadinessStatus.BLOCKED, true, true, true, false))
                .isEqualTo(ReadinessHoldReason.SPACE_BLOCKED);
    }

    /** "Probably fine" is not a standard an examination centre can run on. */
    @Test
    void an_examination_needs_ready_not_merely_usable() {
        assertThat(hold(BookingPurpose.EXAMINATION, LocationReadinessStatus.DEGRADED, true, true, true,
                false)).isEqualTo(ReadinessHoldReason.NOT_EXAMINATION_READY);
        assertThat(hold(BookingPurpose.EXAMINATION, LocationReadinessStatus.READY, true, true, true, false))
                .isNull();
    }

    @Test
    void an_examination_needs_a_space_certified_for_one() {
        assertThat(hold(BookingPurpose.EXAMINATION, LocationReadinessStatus.READY, true, false, true, false))
                .isEqualTo(ReadinessHoldReason.NOT_EXAMINATION_READY);
    }

    /** The lock holds the hall for an examination, so the meeting is what has to move. */
    @Test
    void a_locked_space_refuses_everything_except_an_examination() {
        assertThat(hold(BookingPurpose.MEETING, LocationReadinessStatus.READY, true, true, true, true))
                .isEqualTo(ReadinessHoldReason.LOCKED_FOR_EXAMINATION);
        assertThat(hold(BookingPurpose.EXAMINATION, LocationReadinessStatus.READY, true, true, true, true))
                .isNull();
    }

    @Test
    void a_space_not_offered_for_booking_is_withdrawn_whatever_its_readiness() {
        assertThat(hold(BookingPurpose.LECTURE, LocationReadinessStatus.READY, false, true, true, false))
                .isEqualTo(ReadinessHoldReason.SPACE_WITHDRAWN);
        assertThat(hold(BookingPurpose.LECTURE, LocationReadinessStatus.READY, true, true, false, false))
                .isEqualTo(ReadinessHoldReason.SPACE_WITHDRAWN);
    }

    /** Most severe wins: a withdrawn and blocked hall reads as withdrawn, which is what to deal with. */
    @Test
    void the_most_severe_reason_is_the_one_reported() {
        assertThat(hold(BookingPurpose.EXAMINATION, LocationReadinessStatus.BLOCKED, false, false, false,
                true)).isEqualTo(ReadinessHoldReason.SPACE_WITHDRAWN);
    }

    @Test
    void every_reason_can_be_explained_to_a_requester() {
        for (ReadinessHoldReason reason : ReadinessHoldReason.values()) {
            assertThat(ReadinessHoldPolicy.explain(reason, "HALL-A")).contains("HALL-A");
        }
    }

    private static ReadinessHoldReason hold(BookingPurpose purpose, LocationReadinessStatus readiness,
            boolean bookable, boolean examinationCapable, boolean active, boolean locked) {
        return ReadinessHoldPolicy.holdFor(purpose, readiness, bookable, examinationCapable, active, locked);
    }
}
