package gh.edu.clet.sfl.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class AuthorizationPolicyTest {

    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @Test
    void authorizes_role_and_site_scope() {
        ActorContext actor = actor(Set.of(SflRole.IFIMP_MAINTENANCE_SUPERVISOR), Set.of("main"));

        assertThat(policy.hasRole(actor, SflRole.IFIMP_MAINTENANCE_SUPERVISOR)).isTrue();
        assertThat(policy.canAccessSite(actor, "MAIN")).isTrue();
    }

    @Test
    void wildcard_site_scope_can_access_any_site() {
        ActorContext actor = actor(Set.of(SflRole.SFL_ADMIN), Set.of("*"));

        assertThat(policy.canAccessSite(actor, "HQ")).isTrue();
        assertThat(policy.canAccessSite(actor, "MAIN")).isTrue();
    }

    @Test
    void rejects_missing_role_and_site_scope() {
        ActorContext actor = actor(Set.of(SflRole.IFIMP_REQUESTER), Set.of("MAIN"));

        assertThatThrownBy(() -> policy.requireAnyRole(actor, Set.of(SflRole.FACILITIES_MANAGER)))
                .isInstanceOf(AuthorizationException.class)
                .hasMessage("Actor does not have a required role");
        assertThatThrownBy(() -> policy.requireSiteAccess(actor, "HQ"))
                .isInstanceOf(AuthorizationException.class)
                .hasMessage("Actor cannot access site: HQ");
    }

    @Test
    void grants_permission_when_matrix_says_so() {
        PermissionMatrix matrix = (roles, permission) ->
                roles.contains(SflRole.FACILITIES_MANAGER) && permission == SflPermission.FACILITIES_WORK_ORDER_CLOSE;
        AuthorizationPolicy wired = new AuthorizationPolicy(matrix);
        ActorContext manager = actor(Set.of(SflRole.FACILITIES_MANAGER), Set.of("MAIN"));

        assertThat(wired.hasPermission(manager, SflPermission.FACILITIES_WORK_ORDER_CLOSE)).isTrue();
        assertThatThrownBy(() -> wired.requirePermission(manager, SflPermission.FACILITIES_MASTER_DATA_MANAGE))
                .isInstanceOf(AuthorizationException.class)
                .hasMessage("Actor requires permission: FACILITIES_MASTER_DATA_MANAGE");
    }

    @Test
    void refuses_permission_when_matrix_denies_it() {
        PermissionMatrix matrix = (roles, permission) -> false;
        AuthorizationPolicy wired = new AuthorizationPolicy(matrix);
        ActorContext requester = actor(Set.of(SflRole.IFIMP_REQUESTER), Set.of("MAIN"));

        assertThat(wired.hasPermission(requester, SflPermission.FACILITIES_WORK_ORDER_CLOSE)).isFalse();
        assertThatThrownBy(() -> wired.requirePermission(requester, SflPermission.FACILITIES_WORK_ORDER_CLOSE))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    void default_no_arg_constructor_denies_every_permission() {
        ActorContext admin = actor(Set.of(SflRole.SFL_ADMIN), Set.of("*"));

        assertThat(policy.hasPermission(admin, SflPermission.FACILITIES_WORK_ORDER_CLOSE)).isFalse();
        assertThatThrownBy(() -> policy.requirePermission(admin, SflPermission.FACILITIES_WORK_ORDER_CLOSE))
                .isInstanceOf(AuthorizationException.class);
    }

    private ActorContext actor(Set<SflRole> roles, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal("user-1", "User One", roles, sites, false), "corr-1");
    }
}