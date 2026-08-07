package gh.edu.clet.sfl.facilities.api;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.shared.domain.policy.FacilitiesPermissionMatrix;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The IFIMP matrix answers the questions the dashboard gates screens on.
 *
 * <h2>What this is really pinning</h2>
 *
 * <p>{@code FacilitiesGovernanceController#actorPermissions} serves
 * {@code /api/v1/facilities/actor/permissions} from {@link
 * gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization}, which reads this matrix.
 * The route has existed since S152 and a second controller for it was briefly added here in error -
 * two beans on one path, and the service would not start.
 *
 * <p>What was actually wrong was on the other side: the dashboard treated an unanswered permission
 * lookup as "allow everything". With no service running every account saw every screen, and with one
 * running everything belonging to the others silently vanished. Same cause, opposite symptoms.
 *
 * <p>So this asserts the matrix rather than the route: the distinctions below are what the sidebar
 * now gates on, and they are worth pinning wherever they are read from.
 */
class ActorPermissionsCoverageTest {

    @Test
    @DisplayName("a facilities role's permissions are non-empty and come from the matrix")
    void facilities_permissions_are_answered() {
        Set<SflRole> roles = Set.of(SflRole.FACILITIES_MANAGER);

        Set<SflPermission> granted = FacilitiesPermissionMatrix.permissionsFor(roles);

        assertThat(granted)
                .as("a facilities manager must hold facilities permissions")
                .isNotEmpty()
                .allSatisfy(permission ->
                        assertThat(permission.name()).startsWith("FACILITIES_"));
    }

    @Test
    @DisplayName("the technician/supervisor split the dashboard gates on is real")
    void technician_updates_supervisor_closes() {
        Set<SflPermission> technician = FacilitiesPermissionMatrix.permissionsFor(
                Set.of(SflRole.IFIMP_TECHNICIAN));
        Set<SflPermission> supervisor = FacilitiesPermissionMatrix.permissionsFor(
                Set.of(SflRole.IFIMP_MAINTENANCE_SUPERVISOR));

        // The sidebar hides the work-order register from a technician on exactly this distinction.
        // If it ever stops holding, the dashboard and the service disagree about who closes a job.
        assertThat(technician).contains(SflPermission.FACILITIES_WORK_ORDER_UPDATE);
        assertThat(technician).doesNotContain(SflPermission.FACILITIES_WORK_ORDER_CLOSE);
        assertThat(supervisor).contains(SflPermission.FACILITIES_WORK_ORDER_CLOSE);
    }

    @Test
    @DisplayName("a requester holds reporting and booking, and nothing operational")
    void requester_is_narrow() {
        Set<SflPermission> requester = FacilitiesPermissionMatrix.permissionsFor(
                Set.of(SflRole.IFIMP_REQUESTER));

        assertThat(requester).contains(
                SflPermission.FACILITIES_FAULT_REPORT,
                SflPermission.FACILITIES_BOOKING_REQUEST);
        assertThat(requester).doesNotContain(
                SflPermission.FACILITIES_WORK_ORDER_ASSIGN,
                SflPermission.FACILITIES_SITE_MANAGE,
                SflPermission.FACILITIES_READINESS_OVERRIDE);
    }
}
