package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DashboardScopeMissingException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetAuthorizationException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.FleetErrorCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RestrictedDrilldownException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.UnauthorizedApprovalException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Traces: SRS-SFL-S166-01 (Unauthorized Scope), SRS-SFL-S166-02 (Unauthorized Approval),
 * SRS-SFL-S166-05 (No Scope, Restricted Drilldown).
 */
class FleetAccessPolicyTest {

    private static final SiteCode ACCRA = SiteCode.of("ACCRA");
    private static final SiteCode KUMASI = SiteCode.of("KUMASI");

    private final FleetAccessPolicy policy = new FleetAccessPolicy();

    @Test
    @DisplayName("an authorised officer passes the permission and site check")
    void authorised_officer_passes() {
        ActorContext officer = actor(Set.of(SflRole.FLEET_LOGISTICS_OFFICER), Set.of("ACCRA"));

        assertThatCode(() -> policy.require(officer, SflPermission.FLEET_VEHICLE_MANAGE, ACCRA, "Vehicle", "v-1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a missing permission raises the SRS Unauthorized Scope error")
    void missing_permission_is_rejected() {
        ActorContext viewer = actor(Set.of(SflRole.FLEET_REPORTING_VIEWER), Set.of("ACCRA"));

        assertThatThrownBy(() ->
                policy.require(viewer, SflPermission.FLEET_VEHICLE_MANAGE, ACCRA, "Vehicle", "v-1"))
                .isInstanceOf(FleetAuthorizationException.class)
                .hasMessage(FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE.message())
                .extracting(exception -> ((FleetAuthorizationException) exception).details())
                .satisfies(details -> assertThat(details).containsEntry("requiredPermission", "FLEET_VEHICLE_MANAGE"));
    }

    @Test
    @DisplayName("a record in another site is rejected even with the right permission")
    void cross_site_access_is_rejected() {
        ActorContext officer = actor(Set.of(SflRole.FLEET_LOGISTICS_OFFICER), Set.of("KUMASI"));

        assertThatThrownBy(() ->
                policy.require(officer, SflPermission.FLEET_VEHICLE_MANAGE, ACCRA, "Vehicle", "v-1"))
                .isInstanceOf(FleetAuthorizationException.class)
                .hasMessage(FleetErrorCode.FLEET_UNAUTHORIZED_SCOPE.message());
    }

    @Test
    @DisplayName("the wildcard scope reaches every site")
    void wildcard_scope_reaches_every_site() {
        ActorContext admin = actor(Set.of(SflRole.SFL_ADMIN), Set.of("*"));

        assertThatCode(() -> policy.require(admin, SflPermission.FLEET_VEHICLE_MANAGE, KUMASI, "Vehicle", "v-1"))
                .doesNotThrowAnyException();
        assertThat(policy.requireSiteScopeFilter(admin).allSites()).isTrue();
    }

    @Test
    @DisplayName("a privileged transition without permission raises the SRS Unauthorized Approval error")
    void privileged_transition_without_permission_is_rejected() {
        ActorContext officer = actor(Set.of(SflRole.FLEET_LOGISTICS_OFFICER), Set.of("ACCRA"));

        assertThatThrownBy(() -> policy.requirePrivilegedTransition(officer, SflPermission.FLEET_WORKFLOW_APPROVE,
                ACCRA, "FleetWorkflowItem", "w-1"))
                .isInstanceOf(UnauthorizedApprovalException.class)
                .hasMessage(FleetErrorCode.FLEET_UNAUTHORIZED_APPROVAL.message());
    }

    @Test
    @DisplayName("a fleet manager may perform a privileged transition in their own site")
    void manager_may_perform_privileged_transition() {
        ActorContext manager = actor(Set.of(SflRole.FLEET_MANAGER), Set.of("ACCRA"));

        assertThatCode(() -> policy.requirePrivilegedTransition(manager, SflPermission.FLEET_WORKFLOW_APPROVE,
                ACCRA, "FleetWorkflowItem", "w-1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("record scope keeps a driver to their own work")
    void record_scope_keeps_a_driver_to_their_own_work() {
        ActorContext driver = actor("driver@clet.edu.gh", Set.of(SflRole.FLEET_DRIVER), Set.of("ACCRA"));

        assertThatCode(() -> policy.requireRecordScope(driver, "driver@clet.edu.gh",
                SflPermission.FLEET_TRIP_MANAGE, "Trip", "t-1")).doesNotThrowAnyException();

        assertThatThrownBy(() -> policy.requireRecordScope(driver, "other.driver@clet.edu.gh",
                SflPermission.FLEET_TRIP_MANAGE, "Trip", "t-2"))
                .isInstanceOf(FleetAuthorizationException.class);
    }

    @Test
    @DisplayName("a supervising permission overrides record ownership")
    void supervising_permission_overrides_record_ownership() {
        ActorContext officer = actor(Set.of(SflRole.FLEET_LOGISTICS_OFFICER), Set.of("ACCRA"));

        assertThatCode(() -> policy.requireRecordScope(officer, "driver@clet.edu.gh",
                SflPermission.FLEET_TRIP_MANAGE, "Trip", "t-1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an actor with no site scope raises the SRS No Scope error")
    void no_site_scope_raises_no_scope() {
        ActorContext scopeless = actor(Set.of(SflRole.FLEET_REPORTING_VIEWER), Set.of());

        assertThatThrownBy(() -> policy.requireSiteScopeFilter(scopeless))
                .isInstanceOf(DashboardScopeMissingException.class)
                .hasMessage(FleetErrorCode.FLEET_DASHBOARD_NO_SCOPE.message());
    }

    @Test
    @DisplayName("a site filter only permits the actor's own sites")
    void site_filter_permits_only_assigned_sites() {
        SiteScopeFilter filter = policy.requireSiteScopeFilter(
                actor(Set.of(SflRole.FLEET_REPORTING_VIEWER), Set.of("ACCRA", "TAMALE")));

        assertThat(filter.allSites()).isFalse();
        assertThat(filter.permits("accra")).isTrue();
        assertThat(filter.permits("TAMALE")).isTrue();
        assertThat(filter.permits("KUMASI")).isFalse();
    }

    @Test
    @DisplayName("a drilldown without permission raises the SRS Restricted Drilldown error")
    void drilldown_without_permission_is_rejected() {
        ActorContext driver = actor(Set.of(SflRole.FLEET_DRIVER), Set.of("ACCRA"));

        assertThatThrownBy(() -> policy.requireDrilldown(driver, SflPermission.FLEET_DASHBOARD_DRILLDOWN, ACCRA,
                "Vehicle", "v-1"))
                .isInstanceOf(RestrictedDrilldownException.class)
                .hasMessage(FleetErrorCode.FLEET_DASHBOARD_RESTRICTED_DRILLDOWN.message());
    }

    @Test
    @DisplayName("sensitive fields need their own explicit permission")
    void sensitive_field_permission_is_separate() {
        ActorContext officer = actor(Set.of(SflRole.FLEET_LOGISTICS_OFFICER), Set.of("ACCRA"));
        ActorContext manager = actor(Set.of(SflRole.FLEET_MANAGER), Set.of("ACCRA"));

        assertThat(policy.canReadSensitive(officer, SflPermission.FLEET_DRIVER_SENSITIVE_READ)).isFalse();
        assertThat(policy.canReadSensitive(manager, SflPermission.FLEET_DRIVER_SENSITIVE_READ)).isTrue();
    }

    private static ActorContext actor(Set<SflRole> roles, Set<String> sites) {
        return actor("officer@clet.edu.gh", roles, sites);
    }

    private static ActorContext actor(String subject, Set<SflRole> roles, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal(subject, subject, roles, sites, false), "corr-test");
    }
}
