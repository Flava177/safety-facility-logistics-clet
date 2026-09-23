package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlErrorCode;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mirrors {@code VisitorAccessPolicyTest}: a direct unit test against the policy, no database and no
 * Spring context needed. */
class AccessControlAccessPolicyTest {

    private final AccessControlAccessPolicy policy = new AccessControlAccessPolicy();
    private static final String SITE = "E2E-HQ";

    @Test
    void an_auditor_may_read_events_but_may_not_manage_zones() {
        ActorContext auditor = actor(SflRole.AUDITOR, Set.of(SITE));

        assertThat(policy.has(auditor, SflPermission.ACCESS_EVENT_READ)).isTrue();

        assertThatThrownBy(() -> policy.require(auditor, SflPermission.ACCESS_ZONE_MANAGE, SITE, "AccessZone", null))
                .isInstanceOf(AccessControlException.class)
                .satisfies(e -> assertThat(((AccessControlException) e).errorCode())
                        .isEqualTo(AccessControlErrorCode.ACCESS_CONTROL_UNAUTHORIZED_SCOPE));
    }

    @Test
    void an_soc_operator_denied_override_authority_gets_the_not_authorised_error_code() {
        ActorContext auditor = actor(SflRole.AUDITOR, Set.of(SITE));

        assertThatThrownBy(() -> policy.requireOverrideAuthority(auditor, SflPermission.ACCESS_OVERRIDE_CREATE, SITE,
                "override-1"))
                .isInstanceOf(AccessControlException.class)
                .satisfies(e -> assertThat(((AccessControlException) e).errorCode())
                        .isEqualTo(AccessControlErrorCode.ACCESS_OVERRIDE_NOT_AUTHORISED));
    }

    @Test
    void an_access_control_administrator_at_the_right_site_is_granted_zone_management() {
        ActorContext admin = actor(SflRole.ACCESS_CONTROL_ADMINISTRATOR, Set.of(SITE));

        policy.require(admin, SflPermission.ACCESS_ZONE_MANAGE, SITE, "AccessZone", null);
        assertThat(policy.has(admin, SflPermission.ACCESS_ZONE_MANAGE)).isTrue();
        assertThat(policy.has(admin, SflPermission.ACCESS_OVERRIDE_BREAK_GLASS)).isFalse();
    }

    @Test
    void a_soc_operator_holding_the_right_permission_is_still_refused_outside_their_site_scope() {
        ActorContext socElsewhere = actor(SflRole.SOC_OPERATOR, Set.of("OTHER-SITE"));

        assertThat(policy.has(socElsewhere, SflPermission.ACCESS_OVERRIDE_CREATE)).isTrue();
        assertThatThrownBy(() -> policy.requireOverrideAuthority(socElsewhere, SflPermission.ACCESS_OVERRIDE_CREATE,
                SITE, "override-1"))
                .isInstanceOf(AccessControlException.class)
                .satisfies(e -> assertThat(((AccessControlException) e).errorCode())
                        .isEqualTo(AccessControlErrorCode.ACCESS_OVERRIDE_NOT_AUTHORISED));
    }

    private ActorContext actor(SflRole role, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal("actor-" + role, "Actor", Set.of(role), sites, false),
                "corr-1");
    }
}
