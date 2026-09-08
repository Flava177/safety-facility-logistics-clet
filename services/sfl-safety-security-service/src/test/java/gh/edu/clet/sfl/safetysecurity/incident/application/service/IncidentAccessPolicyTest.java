package gh.edu.clet.sfl.safetysecurity.incident.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentErrorCode;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * None of this module's 89 pre-existing tests asserted that an actor lacking a required permission is
 * actually rejected, despite {@link IncidentAccessPolicy#require} gating every mutating S163
 * operation. A unit test directly against the policy, matching {@code AuthorizationPolicyTest}'s
 * style in {@code sfl-service-common}, is the fastest and most direct way to close that gap - it needs
 * no database and no Spring context, unlike the {@code *MandatoryScenariosEndToEndTest} suites this
 * module otherwise relies on.
 */
class IncidentAccessPolicyTest {

    private final IncidentAccessPolicy policy = new IncidentAccessPolicy();
    private static final String SITE = "E2E-HQ";

    @Test
    void an_auditor_may_read_and_export_but_may_not_close_a_case() {
        // AUDITOR is granted INCIDENT_REPORT_READ and INCIDENT_REPORT_EXPORT (see
        // IncidentPermissionMatrix) but not INCIDENT_CLOSE - closure is the Investigator's hard-rule-1
        // gated operation (SRS §D.9 step 6), not a read-only role's.
        ActorContext auditor = actor(SflRole.AUDITOR, Set.of(SITE));

        assertThat(policy.has(auditor, SflPermission.INCIDENT_REPORT_READ)).isTrue();

        assertThatThrownBy(() -> policy.require(auditor, SflPermission.INCIDENT_CLOSE, SITE, "SecurityIncident",
                "inc-1"))
                .isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_UNAUTHORIZED_SCOPE));
        assertThat(policy.has(auditor, SflPermission.INCIDENT_CLOSE)).isFalse();
    }

    @Test
    void an_investigator_holding_the_right_permission_is_still_refused_outside_their_site_scope() {
        // Right permission, wrong site: INCIDENT_INVESTIGATOR genuinely holds INCIDENT_CLOSE, but a
        // permission grant is not itself a grant to every site - the site-scope check is a second,
        // independent gate.
        ActorContext investigatorElsewhere = actor(SflRole.INCIDENT_INVESTIGATOR, Set.of("OTHER-SITE"));

        assertThat(policy.has(investigatorElsewhere, SflPermission.INCIDENT_CLOSE)).isTrue();
        assertThatThrownBy(() -> policy.require(investigatorElsewhere, SflPermission.INCIDENT_CLOSE, SITE,
                "SecurityIncident", "inc-1"))
                .isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_UNAUTHORIZED_SCOPE));
    }

    @Test
    void an_investigator_at_the_right_site_is_granted() {
        ActorContext investigator = actor(SflRole.INCIDENT_INVESTIGATOR, Set.of(SITE));

        policy.require(investigator, SflPermission.INCIDENT_CLOSE, SITE, "SecurityIncident", "inc-1");
        assertThat(policy.has(investigator, SflPermission.INCIDENT_CLOSE)).isTrue();
    }

    private ActorContext actor(SflRole role, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal("actor-" + role, "Actor", Set.of(role), sites, false),
                "corr-1");
    }
}
