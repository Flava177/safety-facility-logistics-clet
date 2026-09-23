package gh.edu.clet.sfl.safetysecurity.intrusion.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionErrorCode;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.exception.IntrusionException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mirrors {@code AccessControlAccessPolicyTest}: a direct unit test against the policy, no database
 * and no Spring context needed. */
class IntrusionAccessPolicyTest {

    private final IntrusionAccessPolicy policy = new IntrusionAccessPolicy();
    private static final String SITE = "E2E-HQ";

    @Test
    void an_auditor_may_read_alarms_but_may_not_manage_zones() {
        ActorContext auditor = actor(SflRole.AUDITOR, Set.of(SITE));

        assertThat(policy.has(auditor, SflPermission.INTRUSION_ALARM_READ)).isTrue();

        assertThatThrownBy(() -> policy.require(auditor, SflPermission.INTRUSION_ZONE_MANAGE, SITE, "IntrusionZone",
                null))
                .isInstanceOf(IntrusionException.class)
                .satisfies(e -> assertThat(((IntrusionException) e).errorCode())
                        .isEqualTo(IntrusionErrorCode.INTRUSION_UNAUTHORIZED_SCOPE));
    }

    @Test
    void an_auditor_denied_disarm_authority_gets_the_not_authorised_error_code() {
        ActorContext auditor = actor(SflRole.AUDITOR, Set.of(SITE));

        assertThatThrownBy(() -> policy.requireDisarmAuthority(auditor, SflPermission.INTRUSION_ZONE_DISARM, SITE,
                "zone-1"))
                .isInstanceOf(IntrusionException.class)
                .satisfies(e -> assertThat(((IntrusionException) e).errorCode())
                        .isEqualTo(IntrusionErrorCode.INTRUSION_ZONE_DISARM_NOT_AUTHORISED));
    }

    @Test
    void a_security_director_at_the_right_site_is_granted_zone_management() {
        ActorContext director = actor(SflRole.SECURITY_DIRECTOR, Set.of(SITE));

        policy.require(director, SflPermission.INTRUSION_ZONE_MANAGE, SITE, "IntrusionZone", null);
        assertThat(policy.has(director, SflPermission.INTRUSION_ZONE_MANAGE)).isTrue();
    }

    @Test
    void a_soc_operator_holding_the_right_permission_is_still_refused_outside_their_site_scope() {
        ActorContext socElsewhere = actor(SflRole.SOC_OPERATOR, Set.of("OTHER-SITE"));

        assertThat(policy.has(socElsewhere, SflPermission.INTRUSION_ZONE_DISARM)).isTrue();
        assertThatThrownBy(() -> policy.requireDisarmAuthority(socElsewhere, SflPermission.INTRUSION_ZONE_DISARM,
                SITE, "zone-1"))
                .isInstanceOf(IntrusionException.class)
                .satisfies(e -> assertThat(((IntrusionException) e).errorCode())
                        .isEqualTo(IntrusionErrorCode.INTRUSION_ZONE_DISARM_NOT_AUTHORISED));
    }

    private ActorContext actor(SflRole role, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal("actor-" + role, "Actor", Set.of(role), sites, false),
                "corr-1");
    }
}
