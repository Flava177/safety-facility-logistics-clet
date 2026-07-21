package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.ArchivedRecordImmutableException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.InvalidStateTransitionException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleLifecycleStatus;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Every allowed <em>and</em> prohibited vehicle lifecycle transition.
 *
 * <p>Traces: SRS-SFL-S166-01 "Records shall support active, inactive, suspended and archived lifecycle
 * states" and the prohibition on editing archived records outside a restoration workflow.
 */
class VehicleLifecyclePolicyTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "ACTIVE, INACTIVE",
            "ACTIVE, SUSPENDED",
            "ACTIVE, ARCHIVED",
            "INACTIVE, ACTIVE",
            "INACTIVE, SUSPENDED",
            "INACTIVE, ARCHIVED",
            "SUSPENDED, ACTIVE",
            "SUSPENDED, INACTIVE",
            "SUSPENDED, ARCHIVED",
            "ARCHIVED, INACTIVE"
    })
    void allowed_transitions(VehicleLifecycleStatus from, VehicleLifecycleStatus to) {
        assertThat(VehicleLifecyclePolicy.canTransition(from, to)).isTrue();
        assertThatCode(() -> VehicleLifecyclePolicy.requireTransition(from, to)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} -> {1} is prohibited")
    @CsvSource({
            "ACTIVE, ACTIVE",
            "INACTIVE, INACTIVE",
            "SUSPENDED, SUSPENDED",
            "ARCHIVED, ACTIVE",
            "ARCHIVED, SUSPENDED",
            "ARCHIVED, ARCHIVED"
    })
    void prohibited_transitions(VehicleLifecycleStatus from, VehicleLifecycleStatus to) {
        assertThat(VehicleLifecyclePolicy.canTransition(from, to)).isFalse();
        assertThatThrownBy(() -> VehicleLifecyclePolicy.requireTransition(from, to))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("an archived vehicle can only be restored to inactive, never straight back into service")
    void archived_restores_only_to_inactive() {
        Set<VehicleLifecycleStatus> reachable = Set.of(VehicleLifecycleStatus.values()).stream()
                .filter(target -> VehicleLifecyclePolicy.canTransition(VehicleLifecycleStatus.ARCHIVED, target))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertThat(reachable).containsExactly(VehicleLifecycleStatus.INACTIVE);
    }

    @Test
    @DisplayName("editability follows the lifecycle: everything except archived is editable")
    void editability_follows_the_lifecycle() {
        assertThatCode(() -> VehicleLifecyclePolicy.requireEditable(VehicleLifecycleStatus.ACTIVE))
                .doesNotThrowAnyException();
        assertThatCode(() -> VehicleLifecyclePolicy.requireEditable(VehicleLifecycleStatus.INACTIVE))
                .doesNotThrowAnyException();
        assertThatCode(() -> VehicleLifecyclePolicy.requireEditable(VehicleLifecycleStatus.SUSPENDED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> VehicleLifecyclePolicy.requireEditable(VehicleLifecycleStatus.ARCHIVED))
                .isInstanceOf(ArchivedRecordImmutableException.class);
    }

    @Test
    @DisplayName("suspend, archive, reinstate and restore all need a privileged permission")
    void privileged_transitions_are_identified() {
        assertThat(VehicleLifecyclePolicy.isPrivileged(VehicleLifecycleStatus.ACTIVE,
                VehicleLifecycleStatus.SUSPENDED)).isTrue();
        assertThat(VehicleLifecyclePolicy.isPrivileged(VehicleLifecycleStatus.ACTIVE,
                VehicleLifecycleStatus.ARCHIVED)).isTrue();
        assertThat(VehicleLifecyclePolicy.isPrivileged(VehicleLifecycleStatus.SUSPENDED,
                VehicleLifecycleStatus.ACTIVE)).isTrue();
        assertThat(VehicleLifecyclePolicy.isPrivileged(VehicleLifecycleStatus.ARCHIVED,
                VehicleLifecycleStatus.INACTIVE)).isTrue();

        // Deactivating an active vehicle is ordinary fleet management.
        assertThat(VehicleLifecyclePolicy.isPrivileged(VehicleLifecycleStatus.ACTIVE,
                VehicleLifecycleStatus.INACTIVE)).isFalse();
    }

    @Test
    @DisplayName("null statuses never authorise a transition")
    void null_statuses_are_rejected() {
        assertThat(VehicleLifecyclePolicy.canTransition(null, VehicleLifecycleStatus.ACTIVE)).isFalse();
        assertThat(VehicleLifecyclePolicy.canTransition(VehicleLifecycleStatus.ACTIVE, null)).isFalse();
    }
}
