package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvErrorCode;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mirrors {@code AccessControlAccessPolicyTest}: a direct unit test against the policy, no database
 * and no Spring context needed. */
class CctvAccessPolicyTest {

    private final CctvAccessPolicy policy = new CctvAccessPolicy();
    private static final String SITE = "E2E-HQ";

    @Test
    void an_soc_operator_may_read_cameras_but_may_not_approve_evidence_requests() {
        ActorContext soc = actor(SflRole.SOC_OPERATOR, Set.of(SITE));

        assertThat(policy.has(soc, SflPermission.CCTV_CAMERA_READ)).isTrue();

        assertThatThrownBy(() -> policy.require(soc, SflPermission.CCTV_EVIDENCE_REQUEST_APPROVE, SITE,
                "EvidenceRequest", null))
                .isInstanceOf(CctvException.class)
                .satisfies(e -> assertThat(((CctvException) e).errorCode())
                        .isEqualTo(CctvErrorCode.CCTV_UNAUTHORIZED_SCOPE));
    }

    @Test
    void a_security_director_at_the_right_site_is_granted_evidence_request_approval() {
        ActorContext director = actor(SflRole.SECURITY_DIRECTOR, Set.of(SITE));

        policy.require(director, SflPermission.CCTV_EVIDENCE_REQUEST_APPROVE, SITE, "EvidenceRequest", null);
        assertThat(policy.has(director, SflPermission.CCTV_DISCLOSURE_APPROVE)).isTrue();
    }

    @Test
    void a_compliance_officer_may_approve_disclosures_but_not_evidence_requests() {
        ActorContext compliance = actor(SflRole.COMPLIANCE_OFFICER, Set.of(SITE));

        assertThat(policy.has(compliance, SflPermission.CCTV_DISCLOSURE_APPROVE)).isTrue();
        assertThat(policy.has(compliance, SflPermission.CCTV_EVIDENCE_REQUEST_APPROVE)).isFalse();
    }

    @Test
    void an_soc_operator_holding_the_right_permission_is_still_refused_outside_their_site_scope() {
        ActorContext socElsewhere = actor(SflRole.SOC_OPERATOR, Set.of("OTHER-SITE"));

        assertThat(policy.has(socElsewhere, SflPermission.CCTV_LIVE_VIEW_START)).isTrue();
        assertThatThrownBy(() -> policy.require(socElsewhere, SflPermission.CCTV_LIVE_VIEW_START, SITE, "Camera",
                null))
                .isInstanceOf(CctvException.class)
                .satisfies(e -> assertThat(((CctvException) e).errorCode())
                        .isEqualTo(CctvErrorCode.CCTV_UNAUTHORIZED_SCOPE));
    }

    private ActorContext actor(SflRole role, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal("actor-" + role, "Actor", Set.of(role), sites, false),
                "corr-1");
    }
}
