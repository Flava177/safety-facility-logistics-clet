package gh.edu.clet.sfl.safetysecurity.visitor.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport;
import gh.edu.clet.sfl.safetysecurity.visitor.application.port.VisitorRepository;
import gh.edu.clet.sfl.safetysecurity.visitor.application.service.VisitorCheckInOutService;
import gh.edu.clet.sfl.safetysecurity.visitor.application.service.VisitorDecisionService;
import gh.edu.clet.sfl.safetysecurity.visitor.application.service.VisitorRegistrationService;
import gh.edu.clet.sfl.safetysecurity.visitor.application.service.VisitorRollCallService;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitPurpose;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Database-backed proof of the S160 mandatory scenarios: pre-registration, approval-gated
 * confirmation, badge assignment, check-in/out, roll-call visibility, rejection and self-approval
 * denial. Each test drives the real application services directly against a real Postgres, the same
 * shape as {@code EmergencyMandatoryScenariosEndToEndTest}.
 */
@SpringBootTest(properties = {"sfl.security.enabled=false"})
@EnabledIf(value = "gh.edu.clet.sfl.safetysecurity.e2e.SafetySecurityPostgresSupport#databaseAvailable",
        disabledReason = "No PostgreSQL available")
class VisitorMandatoryScenariosEndToEndTest extends SafetySecurityPostgresSupport {

    @Autowired
    private VisitorRegistrationService registration;
    @Autowired
    private VisitorDecisionService decisions;
    @Autowired
    private VisitorCheckInOutService checkInOut;
    @Autowired
    private VisitorRollCallService rollCall;

    private static final String SITE = "E2E-HQ";

    private ActorContext actor(String id, SflRole role) {
        return new ActorContext(new SiteScopedPrincipal(id, id, Set.of(role), Set.of(SITE), false),
                "e2e-" + UUID.randomUUID());
    }

    private VisitorVisit preRegister(VisitPurpose purpose, String hostId) {
        return registration.preRegister(new VisitorRegistrationService.PreRegisterVisit(SITE,
                "Visitor " + UUID.randomUUID(), "Acme Ltd", "visitor@example.com", hostId, "Host " + hostId,
                purpose, Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                actor("reception-e2e", SflRole.RECEPTION_OFFICER), SourceChannel.WEB));
    }

    @Test
    void a_meeting_runs_the_full_approval_gated_lifecycle_and_appears_on_the_roll_call() {
        String hostId = "host-" + UUID.randomUUID();
        VisitorVisit registered = preRegister(VisitPurpose.MEETING, hostId);
        assertThat(registered.status()).isEqualTo(VisitStatus.PRE_REGISTERED);
        assertThat(registered.approvalRequired()).isTrue();

        VisitorVisit confirmed = decisions.decide(new VisitorDecisionService.DecideVisit(registered.id(), true,
                null, null, registered.metadata().version(), actor(hostId, SflRole.VISITOR_HOST),
                SourceChannel.WEB));
        assertThat(confirmed.status()).isEqualTo(VisitStatus.CONFIRMED);

        VisitorVisit badged = checkInOut.assignBadge(new VisitorCheckInOutService.AssignBadge(confirmed.id(),
                "B-" + UUID.randomUUID(), List.of("LOBBY"), confirmed.metadata().version(),
                actor("reception-e2e", SflRole.RECEPTION_OFFICER), SourceChannel.WEB));

        VisitorVisit checkedIn = checkInOut.checkIn(new VisitorCheckInOutService.Transition(badged.id(),
                badged.metadata().version(), actor("reception-e2e", SflRole.RECEPTION_OFFICER), SourceChannel.WEB));
        assertThat(checkedIn.status()).isEqualTo(VisitStatus.CHECKED_IN);

        List<VisitorVisit> onSite = rollCall.rollCall(SITE, actor("soc-e2e", SflRole.SOC_OPERATOR));
        assertThat(onSite).extracting(VisitorVisit::id).contains(checkedIn.id());

        VisitorVisit checkedOut = checkInOut.checkOut(new VisitorCheckInOutService.Transition(checkedIn.id(),
                checkedIn.metadata().version(), actor("reception-e2e", SflRole.RECEPTION_OFFICER),
                SourceChannel.WEB));
        assertThat(checkedOut.status()).isEqualTo(VisitStatus.CHECKED_OUT);

        List<VisitorVisit> afterCheckout = rollCall.rollCall(SITE, actor("soc-e2e", SflRole.SOC_OPERATOR));
        assertThat(afterCheckout).extracting(VisitorVisit::id).doesNotContain(checkedOut.id());
    }

    @Test
    void a_delivery_needs_no_host_approval_and_is_confirmed_at_registration() {
        VisitorVisit registered = preRegister(VisitPurpose.DELIVERY, "host-" + UUID.randomUUID());
        assertThat(registered.approvalRequired()).isFalse();
        assertThat(registered.status()).isEqualTo(VisitStatus.CONFIRMED);
        assertThat(registered.approvalId()).isNull();
    }

    @Test
    void whoever_registered_a_visit_may_not_also_decide_on_it() {
        // preRegister() always registers as "reception-e2e" - the host is a different actor and is
        // legitimately allowed to decide (that is the whole point of host approval). What is
        // forbidden is the registrant deciding on their own registration.
        String hostId = "host-" + UUID.randomUUID();
        VisitorVisit registered = preRegister(VisitPurpose.MEETING, hostId);

        assertThatThrownBy(() -> decisions.decide(new VisitorDecisionService.DecideVisit(registered.id(), true,
                null, null, registered.metadata().version(), actor("reception-e2e", SflRole.SFL_ADMIN),
                SourceChannel.WEB)))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_SELF_APPROVAL_NOT_ALLOWED));
    }

    @Test
    void a_rejected_visit_records_the_reason_and_cannot_be_checked_in() {
        String hostId = "host-" + UUID.randomUUID();
        VisitorVisit registered = preRegister(VisitPurpose.MEETING, hostId);

        VisitorVisit rejected = decisions.decide(new VisitorDecisionService.DecideVisit(registered.id(), false,
                "Not expected this week", null, registered.metadata().version(),
                actor(hostId + "-approver", SflRole.SFL_ADMIN), SourceChannel.WEB));
        assertThat(rejected.status()).isEqualTo(VisitStatus.REJECTED);
        assertThat(rejected.closureReason()).isEqualTo("Not expected this week");

        assertThatThrownBy(() -> checkInOut.checkIn(new VisitorCheckInOutService.Transition(rejected.id(),
                rejected.metadata().version(), actor("reception-e2e", SflRole.RECEPTION_OFFICER),
                SourceChannel.WEB)))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_INVALID_STATE_TRANSITION));
    }

    @Test
    void search_finds_a_registered_visit_by_site_and_host() {
        String hostId = "host-" + UUID.randomUUID();
        VisitorVisit registered = preRegister(VisitPurpose.EVENT, hostId);

        List<VisitorVisit> found = registration.search(
                new VisitorRepository.VisitQuery(SITE, VisitStatus.PRE_REGISTERED, hostId, null, null, 50),
                actor("soc-e2e", SflRole.SOC_OPERATOR));
        assertThat(found).extracting(VisitorVisit::id).contains(registered.id());
    }
}
