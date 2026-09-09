package gh.edu.clet.sfl.safetysecurity.visitor.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * None of this module's 89 pre-existing tests asserted that an actor lacking a required permission is
 * actually rejected, despite {@link VisitorAccessPolicy#require}/{@link
 * VisitorAccessPolicy#requireApproval} gating every mutating S160 operation. A unit test directly
 * against the policy, matching {@code AuthorizationPolicyTest}'s style in {@code sfl-service-common},
 * is the fastest and most direct way to close that gap - it needs no database and no Spring context,
 * unlike the {@code *MandatoryScenariosEndToEndTest} suites this module otherwise relies on.
 */
class VisitorAccessPolicyTest {

    private final VisitorAccessPolicy policy = new VisitorAccessPolicy();
    private static final String SITE = "E2E-HQ";

    @Test
    void an_auditor_may_read_but_may_not_pre_register_a_visit() {
        // AUDITOR is granted VISITOR_VISIT_READ and VISITOR_REPORT_READ (see VisitorPermissionMatrix)
        // but not VISITOR_VISIT_CREATE - registering a visit is Reception's or a Host's operation, not
        // a read-only role's.
        ActorContext auditor = actor(SflRole.AUDITOR, Set.of(SITE));

        assertThat(policy.has(auditor, SflPermission.VISITOR_VISIT_READ)).isTrue();

        assertThatThrownBy(() -> policy.require(auditor, SflPermission.VISITOR_VISIT_CREATE, SITE, "VisitorVisit",
                null))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_UNAUTHORIZED_SCOPE));
        assertThat(policy.has(auditor, SflPermission.VISITOR_VISIT_CREATE)).isFalse();
    }

    @Test
    void an_auditor_denied_the_approval_permission_gets_the_unauthorized_approval_error_code() {
        // requireApproval() is the flavoured guard VisitorDecisionService uses for host approval/
        // rejection - a permission failure there must surface as VISITOR_UNAUTHORIZED_APPROVAL, not
        // the generic VISITOR_UNAUTHORIZED_SCOPE, so a client can distinguish "wrong resource" from
        // "not allowed to decide on visits at all".
        ActorContext auditor = actor(SflRole.AUDITOR, Set.of(SITE));

        assertThatThrownBy(() -> policy.requireApproval(auditor, SflPermission.VISITOR_VISIT_APPROVE, SITE,
                "VisitorVisit", "visit-1"))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_UNAUTHORIZED_APPROVAL));
    }

    @Test
    void a_host_holding_the_right_permission_is_still_refused_outside_their_site_scope() {
        ActorContext hostElsewhere = actor(SflRole.VISITOR_HOST, Set.of("OTHER-SITE"));

        assertThat(policy.has(hostElsewhere, SflPermission.VISITOR_VISIT_APPROVE)).isTrue();
        assertThatThrownBy(() -> policy.requireApproval(hostElsewhere, SflPermission.VISITOR_VISIT_APPROVE, SITE,
                "VisitorVisit", "visit-1"))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_UNAUTHORIZED_APPROVAL));
    }

    @Test
    void reception_at_the_right_site_is_granted() {
        ActorContext reception = actor(SflRole.RECEPTION_OFFICER, Set.of(SITE));

        policy.require(reception, SflPermission.VISITOR_VISIT_CREATE, SITE, "VisitorVisit", null);
        assertThat(policy.has(reception, SflPermission.VISITOR_VISIT_CREATE)).isTrue();
    }

    private ActorContext actor(SflRole role, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal("actor-" + role, "Actor", Set.of(role), sites, false),
                "corr-1");
    }
}
