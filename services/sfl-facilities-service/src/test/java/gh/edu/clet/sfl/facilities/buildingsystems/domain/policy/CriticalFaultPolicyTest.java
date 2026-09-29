package gh.edu.clet.sfl.facilities.buildingsystems.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.CriticalFaultType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S156-03: the three critical faults that bypass debounce. */
class CriticalFaultPolicyTest {

    @Test
    void total_power_loss_is_a_power_state_of_zero_on_an_electrical_device() {
        Optional<CriticalFaultType> fault = CriticalFaultPolicy.classify(BuildingSystemType.ELECTRICAL,
                MeasuredQuantity.POWER_STATE, BigDecimal.ZERO, Set.of(3), false);

        assertThat(fault).contains(CriticalFaultType.TOTAL_POWER_LOSS);
    }

    @Test
    void a_lighting_circuit_losing_power_is_not_a_total_power_loss() {
        Optional<CriticalFaultType> fault = CriticalFaultPolicy.classify(BuildingSystemType.LIGHTING,
                MeasuredQuantity.POWER_STATE, BigDecimal.ZERO, Set.of(3), false);

        assertThat(fault).isEmpty();
    }

    @Test
    void a_configured_lift_entrapment_code_is_recognised() {
        Optional<CriticalFaultType> fault = CriticalFaultPolicy.classify(BuildingSystemType.LIFT,
                MeasuredQuantity.LIFT_STATUS, new BigDecimal("3"), Set.of(3), false);

        assertThat(fault).contains(CriticalFaultType.LIFT_ENTRAPMENT);
    }

    @Test
    void a_lift_code_not_in_the_entrapment_set_is_not_critical() {
        Optional<CriticalFaultType> fault = CriticalFaultPolicy.classify(BuildingSystemType.LIFT,
                MeasuredQuantity.LIFT_STATUS, new BigDecimal("1"), Set.of(3), false);

        assertThat(fault).isEmpty();
    }

    @Test
    void a_generator_failed_start_during_an_outage_is_critical() {
        Optional<CriticalFaultType> fault = CriticalFaultPolicy.classify(BuildingSystemType.GENERATOR,
                MeasuredQuantity.GENERATOR_RUN_STATE, new BigDecimal("-1"), Set.of(3), true);

        assertThat(fault).contains(CriticalFaultType.GENERATOR_FAILED_START_DURING_OUTAGE);
    }

    @Test
    void a_generator_failed_start_with_mains_present_is_not_critical() {
        Optional<CriticalFaultType> fault = CriticalFaultPolicy.classify(BuildingSystemType.GENERATOR,
                MeasuredQuantity.GENERATOR_RUN_STATE, new BigDecimal("-1"), Set.of(3), false);

        assertThat(fault).isEmpty();
    }

    @Test
    void indicates_outage_reads_the_electrical_power_state() {
        assertThat(CriticalFaultPolicy.indicatesOutage(BuildingSystemType.ELECTRICAL, MeasuredQuantity.POWER_STATE,
                BigDecimal.ZERO)).isTrue();
        assertThat(CriticalFaultPolicy.indicatesOutage(BuildingSystemType.ELECTRICAL, MeasuredQuantity.POWER_STATE,
                BigDecimal.ONE)).isFalse();
    }
}
