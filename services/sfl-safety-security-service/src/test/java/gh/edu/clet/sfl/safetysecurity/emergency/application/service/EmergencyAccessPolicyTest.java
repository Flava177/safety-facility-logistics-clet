package gh.edu.clet.sfl.safetysecurity.emergency.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.exception.EmergencyErrorCode;
import gh.edu.clet.sfl.safetysecurity.emergency.domain.exception.EmergencyException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * None of this module's 89 pre-existing tests asserted that an actor lacking a required permission is
 * actually rejected, despite {@link EmergencyAccessPolicy#require}/{@link
 * EmergencyAccessPolicy#requireApproval}/{@link EmergencyAccessPolicy#requirePermission} gating every
 * mutating S174 operation. A unit test directly against the policy, matching {@code
 * AuthorizationPolicyTest}'s style in {@code sfl-service-common}, is the fastest and most direct way to
 * close that gap - it needs no database and no Spring context, unlike the {@code
 * EmergencyMandatoryScenariosEndToEndTest} suite this module otherwise relies on.
 */
class EmergencyAccessPolicyTest {

    private final EmergencyAccessPolicy policy = new EmergencyAccessPolicy();
    private static final String SITE = "E2E-HQ";

    @Test
    void an_auditor_may_read_and_export_evidence_but_may_not_create_an_activation() {
        // AUDITOR is granted read/export permissions (see EmergencyPermissionMatrix) but not
        // EMERGENCY_ACTIVATION_CREATE - firing a notification is an operational role's job, not a
        // read-only auditor's.
        ActorContext auditor = actor(SflRole.AUDITOR, Set.of(SITE));

        assertThat(policy.has(auditor, SflPermission.EMERGENCY_ACTIVATION_READ)).isTrue();

        assertThatThrownBy(() -> policy.require(auditor, SflPermission.EMERGENCY_ACTIVATION_CREATE, SITE,
                "NotificationActivation", null))
                .isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_SCOPE));
        assertThat(policy.has(auditor, SflPermission.EMERGENCY_ACTIVATION_CREATE)).isFalse();
    }

    @Test
    void a_security_officer_denied_the_approval_permission_gets_the_unauthorized_approval_error_code() {
        // SECURITY_OFFICER can create/send activations but does not hold EMERGENCY_ACTIVATION_APPROVE
        // (see EmergencyPermissionMatrix) - approval is HSE_MANAGER's/COMMAND_ROLE's job. requireApproval()
        // must surface EMERGENCY_UNAUTHORIZED_APPROVAL, not the generic scope error, so a client can
        // tell "wrong resource" from "not allowed to approve activations at all".
        ActorContext securityOfficer = actor(SflRole.SECURITY_OFFICER, Set.of(SITE));

        assertThatThrownBy(() -> policy.requireApproval(securityOfficer, SflPermission.EMERGENCY_ACTIVATION_APPROVE,
                SITE, "NotificationActivation", "activation-1"))
                .isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_APPROVAL));
    }

    @Test
    void the_cross_site_admin_guard_ignores_site_scope_but_still_enforces_the_permission() {
        // requirePermission() is the permission-only guard for cross-site administration (integration
        // health/replay) - SOC_OPERATOR does not hold EMERGENCY_INTEGRATION_REPLAY (only _INGEST), so
        // it must still be refused even though this guard never checks site scope at all.
        ActorContext socOperator = actor(SflRole.SOC_OPERATOR, Set.of(SITE));

        assertThat(policy.has(socOperator, SflPermission.EMERGENCY_INTEGRATION_INGEST)).isTrue();
        assertThatThrownBy(() -> policy.requirePermission(socOperator, SflPermission.EMERGENCY_INTEGRATION_REPLAY,
                "IntegrationReplay"))
                .isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_SCOPE));
    }

    @Test
    void a_coordinator_holding_the_right_permission_is_still_refused_outside_their_site_scope() {
        ActorContext coordinatorElsewhere = actor(SflRole.EMERGENCY_COORDINATOR, Set.of("OTHER-SITE"));

        assertThat(policy.has(coordinatorElsewhere, SflPermission.EMERGENCY_ACTIVATION_CREATE)).isTrue();
        assertThatThrownBy(() -> policy.require(coordinatorElsewhere, SflPermission.EMERGENCY_ACTIVATION_CREATE,
                SITE, "NotificationActivation", null))
                .isInstanceOf(EmergencyException.class)
                .satisfies(e -> assertThat(((EmergencyException) e).errorCode())
                        .isEqualTo(EmergencyErrorCode.EMERGENCY_UNAUTHORIZED_SCOPE));
    }

    private ActorContext actor(SflRole role, Set<String> sites) {
        return new ActorContext(new SiteScopedPrincipal("actor-" + role, "Actor", Set.of(role), sites, false),
                "corr-1");
    }
}
