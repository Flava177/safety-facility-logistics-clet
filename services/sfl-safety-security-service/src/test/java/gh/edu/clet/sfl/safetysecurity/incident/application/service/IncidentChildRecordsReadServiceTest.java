package gh.edu.clet.sfl.safetysecurity.incident.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.CorrectiveAction;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentEvidence;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.RetentionClass;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
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

class IncidentChildRecordsReadServiceTest {

    private static final String SITE = "CLET-HQ";
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T09:00:00Z"), ZoneOffset.UTC);
    private InMemorySecurityIncidentRepository repository;
    private IncidentInvestigationService investigation;
    private CorrectiveActionService correctiveActions;
    private SecurityIncident incident;

    @BeforeEach
    void setUp() {
        repository = new InMemorySecurityIncidentRepository();
        var audit = new RecordingAuditPort();
        var events = new RecordingEventPublisher();
        var access = new IncidentAccessPolicy();
        investigation = new IncidentInvestigationService(repository, audit, events, access, clock);
        correctiveActions = new CorrectiveActionService(repository, audit, events, access, clock);
        UUID id = UUID.randomUUID();
        incident = repository.saveIncident(SecurityIncident.report(id, SITE, IncidentSource.HSE,
                "INC-" + id.toString().substring(0, 8), false, "reporter-1", null, "A near miss.", true,
                "reporter-1", clock.instant(), SourceChannel.WEB, "corr-test"));
    }

    @Test
    void a_reader_can_reload_persisted_evidence_and_corrective_actions() {
        IncidentEvidence evidence = repository.saveEvidence(IncidentEvidence.attach(UUID.randomUUID(), incident,
                "s003://evidence/1", "photo.jpg", "image/jpeg", 120L, "a".repeat(64),
                RetentionClass.STANDARD, null, "hse-1", clock.instant()));
        CorrectiveAction capa = repository.saveCorrectiveAction(CorrectiveAction.open(UUID.randomUUID(), incident,
                "Install a guard.", "owner-1", LocalDate.now(clock).plusDays(7), true, "investigator-1",
                clock.instant()));
        var reader = IncidentTestDoubles.actor("auditor-1", SflRole.COMPLIANCE_OFFICER, SITE);

        assertThat(investigation.evidence(incident.id(), reader)).containsExactly(evidence);
        assertThat(correctiveActions.list(incident.id(), reader)).containsExactly(capa);
    }

    @Test
    void a_role_without_incident_read_permission_cannot_list_child_records() {
        var reception = IncidentTestDoubles.actor("reception-1", SflRole.RECEPTION_OFFICER, SITE);

        assertThatThrownBy(() -> investigation.evidence(incident.id(), reception))
                .isInstanceOf(IncidentException.class);
        assertThatThrownBy(() -> correctiveActions.list(incident.id(), reception))
                .isInstanceOf(IncidentException.class);
    }
}
