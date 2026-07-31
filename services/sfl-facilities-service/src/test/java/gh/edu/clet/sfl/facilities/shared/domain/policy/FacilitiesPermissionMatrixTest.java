package gh.edu.clet.sfl.facilities.shared.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The role to permission mapping (SRS-SFL-S152-01).
 *
 * <p>Asserts the decisions that were made deliberately rather than every cell of the matrix. A test
 * that restated the whole table would only prove the table equals itself; these state what the table
 * is *for*.
 */
class FacilitiesPermissionMatrixTest {

    @Test
    void an_administrator_holds_every_facilities_permission() {
        Set<SflPermission> granted = FacilitiesPermissionMatrix.permissionsFor(SflRole.SFL_ADMIN);

        assertThat(granted).contains(SflPermission.FACILITIES_SITE_MANAGE,
                SflPermission.FACILITIES_OPERATING_MODE_CHANGE, SflPermission.FACILITIES_CONFIG_MANAGE,
                SflPermission.FACILITIES_AUDIT_INTEGRITY_CHECK);
        assertThat(granted).allMatch(permission -> permission.name().startsWith("FACILITIES_"));
    }

    @Test
    void a_facilities_manager_runs_the_estate_but_does_not_declare_examination_mode() {
        Set<SflPermission> granted = FacilitiesPermissionMatrix.permissionsFor(SflRole.FACILITIES_MANAGER);

        assertThat(granted).contains(SflPermission.FACILITIES_SPACE_MANAGE,
                SflPermission.FACILITIES_ASSET_MANAGE, SflPermission.FACILITIES_READINESS_ASSESS);
        assertThat(granted).doesNotContain(SflPermission.FACILITIES_OPERATING_MODE_CHANGE);
    }

    @Test
    void a_centre_manager_declares_examination_mode() {
        // Declaring an examination is a centre-level operational decision, not an estate-maintenance one.
        assertThat(FacilitiesPermissionMatrix.grants(Set.of(SflRole.CENTRE_MANAGER),
                SflPermission.FACILITIES_OPERATING_MODE_CHANGE)).isTrue();
        assertThat(FacilitiesPermissionMatrix.grants(Set.of(SflRole.COMMAND_ROLE),
                SflPermission.FACILITIES_OPERATING_MODE_CHANGE)).isTrue();
    }

    @Test
    void a_technician_assesses_readiness_but_cannot_restructure_the_estate() {
        Set<SflPermission> granted = FacilitiesPermissionMatrix.permissionsFor(SflRole.IFIMP_TECHNICIAN);

        assertThat(granted).contains(SflPermission.FACILITIES_READINESS_ASSESS,
                SflPermission.FACILITIES_ASSET_MANAGE);
        assertThat(granted).doesNotContain(SflPermission.FACILITIES_SPACE_MANAGE,
                SflPermission.FACILITIES_READINESS_OVERRIDE, SflPermission.FACILITIES_SITE_MANAGE);
    }

    @Test
    void a_supervisor_can_override_a_readiness_lock() {
        // A blocked examination hall escalates to the maintenance supervisor, so the supervisor is who
        // must be able to release the lock.
        assertThat(FacilitiesPermissionMatrix.grants(Set.of(SflRole.IFIMP_MAINTENANCE_SUPERVISOR),
                SflPermission.FACILITIES_READINESS_OVERRIDE)).isTrue();
    }

    @Test
    void a_requester_reports_a_fault_and_follows_it_and_does_nothing_else() {
        Set<SflPermission> granted = FacilitiesPermissionMatrix.permissionsFor(SflRole.IFIMP_REQUESTER);

        // S153 gave the requester the two permissions reporting a fault actually needs. The read is
        // narrowed per record to their own reports, in FacilityFaultService — a matrix cannot express
        // "mine", so the set here is the outer bound rather than the whole rule.
        assertThat(granted).containsExactlyInAnyOrder(
                SflPermission.FACILITIES_SITE_READ,
                SflPermission.FACILITIES_SPACE_READ,
                SflPermission.FACILITIES_FAULT_REPORT,
                SflPermission.FACILITIES_FAULT_READ);
    }

    @Test
    void assurance_roles_read_everything_and_change_nothing() {
        for (SflRole role : Set.of(SflRole.AUDITOR, SflRole.COMPLIANCE_OFFICER)) {
            Set<SflPermission> granted = FacilitiesPermissionMatrix.permissionsFor(role);

            assertThat(granted).contains(SflPermission.FACILITIES_AUDIT_READ,
                    SflPermission.FACILITIES_AUDIT_INTEGRITY_CHECK, SflPermission.FACILITIES_DASHBOARD_DRILLDOWN);
            assertThat(granted).noneMatch(permission -> permission.name().endsWith("_MANAGE")
                    || permission.name().endsWith("_ASSESS")
                    || permission.name().endsWith("_OVERRIDE")
                    || permission.name().endsWith("_CHANGE"));
        }
    }

    @Test
    void an_unknown_role_grants_nothing() {
        assertThat(FacilitiesPermissionMatrix.permissionsFor(SflRole.FLEET_DRIVER)).isEmpty();
        assertThat(FacilitiesPermissionMatrix.permissionsFor((Set<SflRole>) null)).isEmpty();
        assertThat(FacilitiesPermissionMatrix.permissionsFor(Set.of())).isEmpty();
    }

    @Test
    void permissions_from_several_roles_are_unioned() {
        Set<SflPermission> granted = FacilitiesPermissionMatrix.permissionsFor(
                Set.of(SflRole.IFIMP_TECHNICIAN, SflRole.CENTRE_MANAGER));

        assertThat(granted).contains(SflPermission.FACILITIES_ASSET_MANAGE,
                SflPermission.FACILITIES_OPERATING_MODE_CHANGE);
    }
}
