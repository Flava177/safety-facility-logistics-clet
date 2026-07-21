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

    private ActorContext actor(Set<SflRole> roles, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal("user-1", "User One", roles, sites, false), "corr-1");
    }
}