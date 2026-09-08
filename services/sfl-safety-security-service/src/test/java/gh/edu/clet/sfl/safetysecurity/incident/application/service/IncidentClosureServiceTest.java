package gh.edu.clet.sfl.safetysecurity.incident.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentErrorCode;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.CorrectiveAction;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RiskRating;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Likelihood;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Impact;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.incident.support.IncidentTestDoubles;
import gh.edu.clet.sfl.safetysecurity.incident.support.IncidentTestDoubles.InMemorySecurityIncidentRepository;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingAuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Service-wiring coverage for {@link IncidentClosureService}, the S163 hard-rule-1 CAPA gate - one of
 * this module's two hard business rules the audit flagged as covered only at the domain-model level
 * (via {@code SecurityIncidentDomainTest}) and by broad e2e scenarios, never by a focused test against
 * the service itself with fakes standing in for its repository and access-policy ports. Follows the
 * same in-memory-double idiom as {@code sfl-fleet-logistics-service}'s service tests - no database, no
 * Spring context.
 */
class IncidentClosureServiceTest {

    private static final String SITE = "E2E-HQ";
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T09:00:00Z"), ZoneOffset.UTC);

    private InMemorySecurityIncidentRepository repository;
    private RecordingAuditPort audit;
    private RecordingEventPublisher events;
    private IncidentClosureService closure;

    @BeforeEach
    void setUp() {
        repository = new InMemorySecurityIncidentRepository();
        audit = new RecordingAuditPort();
        events = new RecordingEventPublisher();
        closure = new IncidentClosureService(repository, audit, events, new IncidentAccessPolicy(), clock);
    }

    @Test
    void closes_a_case_with_no_open_mandatory_corrective_actions() {
        SecurityIncident investigating = investigatingIncident();
        repository.saveIncident(investigating);

        SecurityIncident closed = closure.close(new IncidentClosureService.Close(investigating.id(),
                "All clear.", investigating.metadata().version(),
                IncidentTestDoubles.actor("investigator-1", SflRole.INCIDENT_INVESTIGATOR, SITE), SourceChannel.WEB));

        assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(audit.hasRecord("SECURITY_INCIDENT_CLOSED", "SecurityIncident")).isTrue();
        assertThat(events.hasEvent("sfl.ssemp.security-incident-closed.v1")).isTrue();
    }

    @Test
    void refuses_to_close_while_a_mandatory_corrective_action_is_still_open() {
        SecurityIncident investigating = investigatingIncident();
        repository.saveIncident(investigating);
        repository.saveCorrectiveAction(CorrectiveAction.open(UUID.randomUUID(), investigating,
                "Retrain loading-dock staff.", "supervisor-1", LocalDate.now(clock).plusDays(14), true,
                "investigator-1", clock.instant()));

        assertThatThrownBy(() -> closure.close(new IncidentClosureService.Close(investigating.id(), "Too soon.",
                investigating.metadata().version(),
                IncidentTestDoubles.actor("investigator-1", SflRole.INCIDENT_INVESTIGATOR, SITE), SourceChannel.WEB)))
                .isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_MANDATORY_CAPA_OPEN));
        assertThat(repository.findIncident(investigating.id()).orElseThrow().status())
                .isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(audit.hasRecord("SECURITY_INCIDENT_CLOSED", "SecurityIncident")).isFalse();
    }

    @Test
    void a_non_mandatory_open_corrective_action_does_not_block_closure() {
        SecurityIncident investigating = investigatingIncident();
        repository.saveIncident(investigating);
        repository.saveCorrectiveAction(CorrectiveAction.open(UUID.randomUUID(), investigating,
                "Optional housekeeping improvement.", "supervisor-1", LocalDate.now(clock).plusDays(30), false,
                "investigator-1", clock.instant()));

        SecurityIncident closed = closure.close(new IncidentClosureService.Close(investigating.id(),
                "Nothing mandatory outstanding.", investigating.metadata().version(),
                IncidentTestDoubles.actor("investigator-1", SflRole.INCIDENT_INVESTIGATOR, SITE), SourceChannel.WEB));

        assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
    }

    @Test
    void an_actor_without_incident_close_permission_is_rejected() {
        SecurityIncident investigating = investigatingIncident();
        repository.saveIncident(investigating);

        // AUDITOR holds INCIDENT_REPORT_READ/EXPORT but not INCIDENT_CLOSE (see IncidentPermissionMatrix).
        assertThatThrownBy(() -> closure.close(new IncidentClosureService.Close(investigating.id(), "Not my call.",
                investigating.metadata().version(), IncidentTestDoubles.actor("auditor-1", SflRole.AUDITOR, SITE),
                SourceChannel.WEB)))
                .isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_UNAUTHORIZED_SCOPE));
        assertThat(repository.findIncident(investigating.id()).orElseThrow().status())
                .isEqualTo(IncidentStatus.INVESTIGATING);
    }

    @Test
    void a_stale_expected_version_is_refused_as_a_lost_update_rather_than_silently_overwritten() {
        SecurityIncident investigating = investigatingIncident();
        repository.saveIncident(investigating);

        assertThatThrownBy(() -> closure.close(new IncidentClosureService.Close(investigating.id(), "Stale write.",
                investigating.metadata().version() + 1,
                IncidentTestDoubles.actor("investigator-1", SflRole.INCIDENT_INVESTIGATOR, SITE), SourceChannel.WEB)))
                .isInstanceOf(IncidentException.class)
                .satisfies(e -> assertThat(((IncidentException) e).errorCode())
                        .isEqualTo(IncidentErrorCode.INCIDENT_RECORD_VERSION_CONFLICT));
    }

    private SecurityIncident investigatingIncident() {
        UUID id = UUID.randomUUID();
        SecurityIncident reported = SecurityIncident.report(id, SITE, IncidentSource.HSE,
                "INC-" + id.toString().substring(0, 8), false, "reporter-1", "reporter@example.com",
                "Closure-gate service test fixture.", true, "reporter-1", clock.instant(), SourceChannel.WEB,
                "corr-setup");
        SecurityIncident triaged = reported.triage(Severity.HIGH, new RiskRating(Likelihood.LIKELY, Impact.MAJOR),
                false, null, "hse-1", clock.instant(), SourceChannel.WEB, "corr-setup");
        return triaged.openInvestigation("investigator-1", "Under review.", "investigator-1", clock.instant(),
                SourceChannel.WEB, "corr-setup");
    }
}
