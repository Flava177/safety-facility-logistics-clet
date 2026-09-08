package gh.edu.clet.sfl.safetysecurity.visitor.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingAuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingEventPublisher;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorErrorCode;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.exception.VisitorException;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitPurpose;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitStatus;
import gh.edu.clet.sfl.safetysecurity.visitor.domain.model.VisitorVisit;
import gh.edu.clet.sfl.safetysecurity.visitor.support.VisitorTestDoubles;
import gh.edu.clet.sfl.safetysecurity.visitor.support.VisitorTestDoubles.InMemoryVisitorRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Service-wiring coverage for {@link VisitorDecisionService}, the S160 self-approval refusal - one of
 * this module's two hard business rules the audit flagged as covered only at the domain-model level
 * (via {@code VisitorDomainTest}) and by broad e2e scenarios, never by a focused test against the
 * service itself with fakes standing in for its repository and access-policy ports. Follows the same
 * in-memory-double idiom as {@code sfl-fleet-logistics-service}'s service tests - no database, no
 * Spring context.
 */
class VisitorDecisionServiceTest {

    private static final String SITE = "E2E-HQ";
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T09:00:00Z"), ZoneOffset.UTC);

    private InMemoryVisitorRepository repository;
    private RecordingAuditPort audit;
    private RecordingEventPublisher events;
    private VisitorDecisionService decisions;

    @BeforeEach
    void setUp() {
        repository = new InMemoryVisitorRepository();
        audit = new RecordingAuditPort();
        events = new RecordingEventPublisher();
        decisions = new VisitorDecisionService(repository, audit, events, new VisitorAccessPolicy(), clock);
    }

    @Test
    void a_host_approves_a_visit_registered_by_someone_else() {
        String hostId = "host-1";
        VisitorVisit registered = registeredMeeting(hostId, "reception-1");

        VisitorVisit confirmed = decisions.decide(new VisitorDecisionService.DecideVisit(registered.id(), true, null,
                null, registered.metadata().version(), VisitorTestDoubles.actor(hostId, SflRole.VISITOR_HOST, SITE),
                SourceChannel.WEB));

        assertThat(confirmed.status()).isEqualTo(VisitStatus.CONFIRMED);
        assertThat(audit.hasRecord("VISITOR_VISIT_CONFIRMED", "VisitorVisit")).isTrue();
        assertThat(events.hasEvent("sfl.ssemp.visitor-visit-confirmed.v1")).isTrue();
    }

    @Test
    void a_host_rejects_a_visit_with_a_reason() {
        String hostId = "host-1";
        VisitorVisit registered = registeredMeeting(hostId, "reception-1");

        VisitorVisit rejected = decisions.decide(new VisitorDecisionService.DecideVisit(registered.id(), false,
                "Not expected this week", null, registered.metadata().version(),
                VisitorTestDoubles.actor(hostId, SflRole.VISITOR_HOST, SITE), SourceChannel.WEB));

        assertThat(rejected.status()).isEqualTo(VisitStatus.REJECTED);
        assertThat(rejected.closureReason()).isEqualTo("Not expected this week");
        assertThat(events.hasEvent("sfl.ssemp.visitor-visit-rejected.v1")).isTrue();
    }

    @Test
    void whoever_registered_a_visit_may_not_also_decide_on_it() {
        // preRegister() below always registers as "reception-1" - a host approving is fine, but
        // reception deciding on its own registration must be refused even though reception genuinely
        // holds VISITOR_VISIT_APPROVE in this test's actor (SFL_ADMIN would too, in production).
        String hostId = "host-1";
        VisitorVisit registered = registeredMeeting(hostId, "reception-1");

        assertThatThrownBy(() -> decisions.decide(new VisitorDecisionService.DecideVisit(registered.id(), true, null,
                null, registered.metadata().version(),
                VisitorTestDoubles.actor("reception-1", SflRole.SFL_ADMIN, SITE), SourceChannel.WEB)))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_SELF_APPROVAL_NOT_ALLOWED));
        assertThat(audit.hasRecord("VISITOR_SELF_APPROVAL_DENIED", "VisitorVisit")).isTrue();
        assertThat(repository.findVisit(registered.id()).orElseThrow().status())
                .isEqualTo(VisitStatus.PRE_REGISTERED);
    }

    @Test
    void an_actor_without_the_approval_permission_is_rejected() {
        String hostId = "host-1";
        VisitorVisit registered = registeredMeeting(hostId, "reception-1");

        // AUDITOR holds VISITOR_VISIT_READ/VISITOR_REPORT_READ but not VISITOR_VISIT_APPROVE (see
        // VisitorPermissionMatrix).
        assertThatThrownBy(() -> decisions.decide(new VisitorDecisionService.DecideVisit(registered.id(), true, null,
                null, registered.metadata().version(), VisitorTestDoubles.actor("auditor-1", SflRole.AUDITOR, SITE),
                SourceChannel.WEB)))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_UNAUTHORIZED_APPROVAL));
        assertThat(repository.findVisit(registered.id()).orElseThrow().status())
                .isEqualTo(VisitStatus.PRE_REGISTERED);
    }

    @Test
    void a_stale_expected_version_is_refused_as_a_lost_update_rather_than_silently_overwritten() {
        String hostId = "host-1";
        VisitorVisit registered = registeredMeeting(hostId, "reception-1");

        assertThatThrownBy(() -> decisions.decide(new VisitorDecisionService.DecideVisit(registered.id(), true, null,
                null, registered.metadata().version() + 1,
                VisitorTestDoubles.actor(hostId, SflRole.VISITOR_HOST, SITE), SourceChannel.WEB)))
                .isInstanceOf(VisitorException.class)
                .satisfies(e -> assertThat(((VisitorException) e).errorCode())
                        .isEqualTo(VisitorErrorCode.VISITOR_RECORD_VERSION_CONFLICT));
    }

    private VisitorVisit registeredMeeting(String hostId, String registeredBy) {
        UUID id = UUID.randomUUID();
        VisitorVisit visit = VisitorVisit.preRegister(id, SITE, "Visitor " + id, "Acme Ltd", "visitor@example.com",
                hostId, "Host " + hostId, VisitPurpose.MEETING, clock.instant().plusSeconds(3600),
                clock.instant().plusSeconds(7200), false, registeredBy, clock.instant(), SourceChannel.WEB,
                "corr-setup");
        return repository.saveVisit(visit);
    }
}
