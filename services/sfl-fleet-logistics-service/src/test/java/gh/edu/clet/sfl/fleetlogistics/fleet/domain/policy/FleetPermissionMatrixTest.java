package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The SRS S166 user classes and what each of them may do.
 *
 * <p>Traces: SRS-SFL-S166-01 (site/role-scoped access, sensitive-field masking), SRS-SFL-S166-02
 * (only authorised roles may approve, override, cancel or reopen), SRS-SFL-S166-03 (auditor and
 * compliance access), SRS-SFL-S166-04 (integration principals).
 */
class FleetPermissionMatrixTest {

    @Test
    @DisplayName("a fleet officer runs the register and the queue but holds no privileged overrides")
    void fleet_officer_has_operational_but_not_privileged_permissions() {
        Set<SflRole> officer = Set.of(SflRole.FLEET_LOGISTICS_OFFICER);

        assertThat(FleetPermissionMatrix.grants(officer, SflPermission.FLEET_VEHICLE_MANAGE)).isTrue();
        assertThat(FleetPermissionMatrix.grants(officer, SflPermission.FLEET_TRIP_ASSIGN)).isTrue();
        assertThat(FleetPermissionMatrix.grants(officer, SflPermission.FLEET_WORKFLOW_ASSIGN)).isTrue();

        assertThat(FleetPermissionMatrix.grants(officer, SflPermission.FLEET_WORKFLOW_APPROVE)).isFalse();
        assertThat(FleetPermissionMatrix.grants(officer, SflPermission.FLEET_WORKFLOW_REOPEN)).isFalse();
        assertThat(FleetPermissionMatrix.grants(officer, SflPermission.FLEET_TRIP_CANCEL)).isFalse();
        assertThat(FleetPermissionMatrix.grants(officer, SflPermission.FLEET_VEHICLE_ODOMETER_CORRECT)).isFalse();
        assertThat(FleetPermissionMatrix.grants(officer, SflPermission.FLEET_VEHICLE_SENSITIVE_READ)).isFalse();
        assertThat(FleetPermissionMatrix.grants(officer, SflPermission.FLEET_AUDIT_READ)).isFalse();
    }

    @Test
    @DisplayName("a fleet manager holds the privileged transitions the SRS restricts")
    void fleet_manager_holds_privileged_transitions() {
        Set<SflRole> manager = Set.of(SflRole.FLEET_MANAGER);

        assertThat(FleetPermissionMatrix.grants(manager, SflPermission.FLEET_WORKFLOW_APPROVE)).isTrue();
        assertThat(FleetPermissionMatrix.grants(manager, SflPermission.FLEET_WORKFLOW_CANCEL)).isTrue();
        assertThat(FleetPermissionMatrix.grants(manager, SflPermission.FLEET_WORKFLOW_REOPEN)).isTrue();
        assertThat(FleetPermissionMatrix.grants(manager, SflPermission.FLEET_VEHICLE_RESTORE)).isTrue();
        assertThat(FleetPermissionMatrix.grants(manager, SflPermission.FLEET_VEHICLE_ODOMETER_CORRECT)).isTrue();
        assertThat(FleetPermissionMatrix.grants(manager, SflPermission.FLEET_DRIVER_SENSITIVE_READ)).isTrue();

        // Evidence export approval is a compliance responsibility, not a fleet-management one.
        assertThat(FleetPermissionMatrix.grants(manager, SflPermission.FLEET_EVIDENCE_EXPORT_APPROVE)).isFalse();
    }

    @Test
    @DisplayName("a driver only records inspections and evidence for their own work")
    void driver_is_limited() {
        Set<SflRole> driver = Set.of(SflRole.FLEET_DRIVER);

        assertThat(FleetPermissionMatrix.grants(driver, SflPermission.FLEET_INSPECTION_RECORD)).isTrue();
        assertThat(FleetPermissionMatrix.grants(driver, SflPermission.FLEET_TRIP_READ)).isTrue();
        assertThat(FleetPermissionMatrix.grants(driver, SflPermission.FLEET_EVIDENCE_REGISTER)).isTrue();

        assertThat(FleetPermissionMatrix.grants(driver, SflPermission.FLEET_TRIP_ASSIGN)).isFalse();
        assertThat(FleetPermissionMatrix.grants(driver, SflPermission.FLEET_VEHICLE_MANAGE)).isFalse();
        assertThat(FleetPermissionMatrix.grants(driver, SflPermission.FLEET_DASHBOARD_READ)).isFalse();
        assertThat(FleetPermissionMatrix.grants(driver, SflPermission.FLEET_DRIVER_SENSITIVE_READ)).isFalse();
    }

    @Test
    @DisplayName("an auditor reads and replays but never changes operational records")
    void auditor_reads_and_replays_only() {
        Set<SflRole> auditor = Set.of(SflRole.AUDITOR);

        assertThat(FleetPermissionMatrix.grants(auditor, SflPermission.FLEET_AUDIT_READ)).isTrue();
        assertThat(FleetPermissionMatrix.grants(auditor, SflPermission.FLEET_AUDIT_INTEGRITY_CHECK)).isTrue();
        assertThat(FleetPermissionMatrix.grants(auditor, SflPermission.FLEET_EVIDENCE_READ)).isTrue();
        assertThat(FleetPermissionMatrix.grants(auditor, SflPermission.FLEET_EVIDENCE_EXPORT_REQUEST)).isTrue();

        assertThat(FleetPermissionMatrix.grants(auditor, SflPermission.FLEET_VEHICLE_MANAGE)).isFalse();
        assertThat(FleetPermissionMatrix.grants(auditor, SflPermission.FLEET_TRIP_MANAGE)).isFalse();
        assertThat(FleetPermissionMatrix.grants(auditor, SflPermission.FLEET_WORKFLOW_MANAGE)).isFalse();
        // Approving an export you requested yourself is the separation of duties the SRS relies on.
        assertThat(FleetPermissionMatrix.grants(auditor, SflPermission.FLEET_EVIDENCE_EXPORT_APPROVE)).isFalse();
    }

    @Test
    @DisplayName("a compliance officer approves exports and can override a legal hold")
    void compliance_officer_approves_exports() {
        Set<SflRole> compliance = Set.of(SflRole.COMPLIANCE_OFFICER);

        assertThat(FleetPermissionMatrix.grants(compliance, SflPermission.FLEET_EVIDENCE_EXPORT_APPROVE)).isTrue();
        assertThat(FleetPermissionMatrix.grants(compliance, SflPermission.FLEET_EVIDENCE_LEGAL_HOLD_OVERRIDE))
                .isTrue();
        assertThat(FleetPermissionMatrix.grants(compliance, SflPermission.FLEET_AUDIT_INTEGRITY_CHECK)).isTrue();
        assertThat(FleetPermissionMatrix.grants(compliance, SflPermission.FLEET_VEHICLE_MANAGE)).isFalse();
    }

    @Test
    @DisplayName("a reporting viewer reads dashboards and nothing else")
    void reporting_viewer_is_read_only() {
        Set<SflRole> viewer = Set.of(SflRole.FLEET_REPORTING_VIEWER);

        assertThat(FleetPermissionMatrix.grants(viewer, SflPermission.FLEET_DASHBOARD_READ)).isTrue();
        assertThat(FleetPermissionMatrix.grants(viewer, SflPermission.FLEET_VEHICLE_READ)).isTrue();
        assertThat(FleetPermissionMatrix.grants(viewer, SflPermission.FLEET_VEHICLE_MANAGE)).isFalse();
        assertThat(FleetPermissionMatrix.grants(viewer, SflPermission.FLEET_EVIDENCE_READ)).isFalse();
    }

    @Test
    @DisplayName("a service integration principal may only ingest and read integration health")
    void service_integration_principal_is_narrow() {
        Set<SflRole> service = Set.of(SflRole.SERVICE_INTEGRATION);

        assertThat(FleetPermissionMatrix.grants(service, SflPermission.FLEET_INTEGRATION_INGEST)).isTrue();
        assertThat(FleetPermissionMatrix.grants(service, SflPermission.FLEET_INTEGRATION_HEALTH_READ)).isTrue();
        assertThat(FleetPermissionMatrix.grants(service, SflPermission.FLEET_VEHICLE_READ)).isFalse();
        assertThat(FleetPermissionMatrix.grants(service, SflPermission.FLEET_VEHICLE_MANAGE)).isFalse();
        assertThat(FleetPermissionMatrix.grants(service, SflPermission.FLEET_TRIP_MANAGE)).isFalse();
    }

    @Test
    @DisplayName("the platform administrator holds every fleet permission and no other module's")
    void platform_administrator_holds_every_fleet_permission() {
        Set<SflPermission> granted = FleetPermissionMatrix.permissionsFor(SflRole.SFL_ADMIN);

        assertThat(granted).allMatch(permission -> permission.name().startsWith("FLEET_"));
        assertThat(granted).containsAll(java.util.Arrays.stream(SflPermission.values())
                .filter(permission -> permission.name().startsWith("FLEET_"))
                .toList());
    }

    @Test
    @DisplayName("roles combine additively and an unmapped role grants nothing")
    void roles_combine_and_unknown_roles_grant_nothing() {
        Set<SflPermission> combined = FleetPermissionMatrix.permissionsFor(
                Set.of(SflRole.FLEET_DRIVER, SflRole.FLEET_REPORTING_VIEWER));

        assertThat(combined).contains(SflPermission.FLEET_INSPECTION_RECORD, SflPermission.FLEET_DASHBOARD_READ);
        assertThat(FleetPermissionMatrix.permissionsFor(SflRole.IFIMP_TECHNICIAN)).isEmpty();
        assertThat(FleetPermissionMatrix.permissionsFor(Set.of())).isEmpty();
    }
}
