package gh.edu.clet.sfl.facilities.spaceplanning.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.ComplianceStatus;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyStandard;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.RoomCompliance;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the S158-02 compliance arithmetic, independent of any register or database. */
class OccupancyCompliancePolicyTest {

    private static final UUID ROOM = UUID.randomUUID();

    @Test
    void a_space_type_with_no_standard_is_not_evaluated_never_compliant_by_default() {
        RoomCompliance result = OccupancyCompliancePolicy.evaluate(ROOM, "OFF-101", SpaceType.OFFICE, 4,
                BigDecimal.TEN, 10, null);

        assertThat(result.status()).isEqualTo(ComplianceStatus.NOT_EVALUATED);
        assertThat(result.reasonCode()).isEqualTo(OccupancyCompliancePolicy.STANDARD_NOT_DEFINED);
        assertThat(result.flagged()).isFalse();
    }

    @Test
    void an_allocation_beyond_the_capacity_standard_is_flagged_non_compliant() {
        OccupancyStandard standard = standard(80, null);

        RoomCompliance result = OccupancyCompliancePolicy.evaluate(ROOM, "OFF-101", SpaceType.OFFICE, 4, null, 4,
                standard);

        assertThat(result.status()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
        assertThat(result.reasonCode()).isEqualTo(OccupancyCompliancePolicy.OVER_CAPACITY_SHARE);
        assertThat(result.flagged()).isTrue();
    }

    @Test
    void an_allocation_within_the_capacity_standard_is_compliant() {
        OccupancyStandard standard = standard(80, null);

        RoomCompliance result = OccupancyCompliancePolicy.evaluate(ROOM, "OFF-101", SpaceType.OFFICE, 10, null, 8,
                standard);

        assertThat(result.status()).isEqualTo(ComplianceStatus.COMPLIANT);
        assertThat(result.reasonCode()).isEqualTo(OccupancyCompliancePolicy.WITHIN_STANDARD);
    }

    @Test
    void a_standard_written_in_area_per_person_with_no_recorded_area_is_not_evaluated() {
        OccupancyStandard standard = standard(null, BigDecimal.valueOf(5));

        RoomCompliance result = OccupancyCompliancePolicy.evaluate(ROOM, "OFF-101", SpaceType.OFFICE, 4, null, 4,
                standard);

        assertThat(result.status()).isEqualTo(ComplianceStatus.NOT_EVALUATED);
        assertThat(result.reasonCode()).isEqualTo(OccupancyCompliancePolicy.MEASURES_UNKNOWN);
    }

    @Test
    void under_the_minimum_area_per_person_is_non_compliant() {
        OccupancyStandard standard = standard(null, BigDecimal.valueOf(5));

        RoomCompliance result = OccupancyCompliancePolicy.evaluate(ROOM, "OFF-101", SpaceType.OFFICE, null,
                BigDecimal.valueOf(15), 4, standard);

        assertThat(result.status()).isEqualTo(ComplianceStatus.NON_COMPLIANT);
        assertThat(result.reasonCode()).isEqualTo(OccupancyCompliancePolicy.UNDER_AREA_PER_PERSON);
    }

    private static OccupancyStandard standard(Integer maxCapacityPercent, BigDecimal minAreaPerPerson) {
        return OccupancyStandard.define(UUID.randomUUID(), "MAIN", SpaceType.OFFICE, 1, maxCapacityPercent,
                minAreaPerPerson, null, "tester", Instant.parse("2026-01-01T00:00:00Z"), SourceChannel.WEB, "corr");
    }
}
