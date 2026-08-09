package gh.edu.clet.sfl.facilities.api;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.shared.domain.policy.FacilitiesPermissionMatrix;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * For each role: one thing it may do, and one it may not.
 *
 * <h2>Why a table rather than an endpoint test per case</h2>
 *
 * <p>{@code FacilitiesAuthorization.require} is the single point every application service goes
 * through, and it asks this matrix. Asserting the matrix asserts every endpoint behind it at once,
 * and it keeps holding when a route is renamed - which an endpoint-per-case suite does not.
 *
 * <p>The pairs are chosen to be the ones that were wrong in the dashboard before capability gating:
 * each "may not" is a control that used to be offered to that role because the sidebar asked whether
 * they could *read* rather than whether they could *act*.
 */
class RoleRefusalTest {

    static Stream<Arguments> roleExpectations() {
        return Stream.of(
                // A technician works the job in front of them; accepting it is the supervisor's call.
                Arguments.of(SflRole.IFIMP_TECHNICIAN,
                        SflPermission.FACILITIES_WORK_ORDER_UPDATE,
                        SflPermission.FACILITIES_WORK_ORDER_CLOSE),
                // A contractor is not staff: assignment is the boundary, not site scope.
                Arguments.of(SflRole.VENDOR_TECHNICIAN,
                        SflPermission.FACILITIES_WORK_ORDER_UPDATE,
                        SflPermission.FACILITIES_SITE_MANAGE),
                // A requester reports and books. The estate is not theirs to change.
                Arguments.of(SflRole.IFIMP_REQUESTER,
                        SflPermission.FACILITIES_FAULT_REPORT,
                        SflPermission.FACILITIES_ASSET_MANAGE),
                // A manager runs the estate but does not override a readiness lock.
                Arguments.of(SflRole.FACILITIES_MANAGER,
                        SflPermission.FACILITIES_SITE_MANAGE,
                        SflPermission.FACILITIES_READINESS_OVERRIDE),
                // An auditor proves; an auditor does not operate.
                Arguments.of(SflRole.AUDITOR,
                        SflPermission.FACILITIES_EVIDENCE_EXPORT,
                        SflPermission.FACILITIES_WORK_ORDER_CREATE));
    }

    @ParameterizedTest(name = "{0} may {1} and may not {2}")
    @DisplayName("each role holds its own work and not the next role's")
    @MethodSource("roleExpectations")
    void role_holds_its_own_work(SflRole role, SflPermission allowed, SflPermission refused) {
        Set<SflPermission> granted = FacilitiesPermissionMatrix.permissionsFor(Set.of(role));

        assertThat(granted).contains(allowed);
        assertThat(granted).doesNotContain(refused);
    }
}
