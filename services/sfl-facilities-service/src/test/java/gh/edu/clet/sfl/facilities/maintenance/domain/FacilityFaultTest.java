package gh.edu.clet.sfl.facilities.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FacilityFaultTest {

    @Test
    void reports_a_new_fault_in_reported_state() {
        FacilityFault fault = FacilityFault.report(
                UUID.randomUUID(), "FLT-0001", "MAIN", "BLD-A-101", "Air conditioner failed",
                "The unit is not cooling", "HVAC", FaultPriority.HIGH, "staff@sfl.local", Instant.now());

        assertThat(fault.status()).isEqualTo(FacilityFaultStatus.REPORTED);
        assertThat(fault.workOrderId()).isNull();
    }

    @Test
    void rejects_a_fault_without_a_location() {
        assertThatThrownBy(() -> FacilityFault.report(
                UUID.randomUUID(), "FLT-0001", "MAIN", " ", "Air conditioner failed",
                "The unit is not cooling", "HVAC", FaultPriority.HIGH, "staff@sfl.local", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("locationCode is required");
    }
}

