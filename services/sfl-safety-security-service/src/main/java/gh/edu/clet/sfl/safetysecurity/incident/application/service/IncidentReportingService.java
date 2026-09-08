package gh.edu.clet.sfl.safetysecurity.incident.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.EmergencyRepository.EmergencyPage;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.EmergencyRepository.Paging;
import gh.edu.clet.sfl.safetysecurity.incident.application.port.SecurityIncidentRepository;
import gh.edu.clet.sfl.safetysecurity.incident.application.port.SecurityIncidentSearchPageRepository;
import gh.edu.clet.sfl.safetysecurity.incident.domain.event.IncidentEventType;
import gh.edu.clet.sfl.safetysecurity.incident.domain.exception.IncidentException;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentSource;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS §D.9 step 1: report an incident or near-miss, and read what was reported (step 8: the dashboard). */
@Service
public class IncidentReportingService {

    private final SecurityIncidentRepository repository;
    private final SecurityIncidentSearchPageRepository searchPageRepository;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final IncidentAccessPolicy access;
    private final Clock clock;

    public IncidentReportingService(SecurityIncidentRepository repository,
            SecurityIncidentSearchPageRepository searchPageRepository, AuditPort audit,
            IntegrationEventPublisher events, IncidentAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.searchPageRepository = searchPageRepository;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public SecurityIncident report(ReportIncident command) {
        ActorContext actor = command.actor();
        access.require(actor, SflPermission.INCIDENT_REPORT_CREATE, command.siteCode(), "SecurityIncident", null);

        UUID id = UUID.randomUUID();
        String reference = "INC-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Instant now = clock.instant();
        SecurityIncident incident = SecurityIncident.report(id, command.siteCode(), command.source(), reference,
                command.anonymous(), command.reporterId(), command.reporterContact(), command.description(),
                command.nearMiss(), actor.actorId(), now, command.sourceChannel(), actor.correlationId());
        SecurityIncident saved = repository.saveIncident(incident);

        audit.record(actor, command.sourceChannel().name(), saved.siteCode(), "SECURITY_INCIDENT_REPORTED",
                "SecurityIncident", saved.id().toString(), null, saved, null);
        events.publish(IncidentEventType.SECURITY_INCIDENT_REPORTED.eventType(),
                IncidentEventType.SECURITY_INCIDENT_REPORTED.version(), "SecurityIncident", saved.id().toString(),
                saved.siteCode(), actor, Map.of("incidentId", saved.id().toString(), "reference", saved.reference(),
                        "nearMiss", saved.nearMiss(), "anonymous", saved.anonymous()));
        return saved;
    }

    @Transactional(readOnly = true)
    public SecurityIncident get(UUID id, ActorContext actor) {
        SecurityIncident incident = findOrThrow(id);
        access.require(actor, SflPermission.INCIDENT_REPORT_READ, incident.siteCode(), "SecurityIncident",
                id.toString());
        return incident;
    }

    @Transactional(readOnly = true)
    public List<SecurityIncident> search(SecurityIncidentRepository.IncidentQuery query, ActorContext actor) {
        access.require(actor, SflPermission.INCIDENT_REPORT_READ, query.siteCode(), "SecurityIncident", null);
        return repository.search(query);
    }

    /**
     * The paginated counterpart to {@link #search} - total count, page number and page size, not just
     * a client-{@code limit}-capped list. See {@link SecurityIncidentSearchPageRepository}.
     */
    @Transactional(readOnly = true)
    public EmergencyPage<SecurityIncident> searchPage(String siteCode, IncidentStatus status, Severity severity,
            Paging paging, ActorContext actor) {
        access.require(actor, SflPermission.INCIDENT_REPORT_READ, siteCode, "SecurityIncident", null);
        return searchPageRepository.searchPage(siteCode, status, severity, paging);
    }

    /** SRS §D.9 step 8: counts for the site's HSE dashboard. */
    @Transactional(readOnly = true)
    public Dashboard dashboard(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.INCIDENT_REPORT_READ, siteCode, "SecurityIncident", null);
        return new Dashboard(repository.countByStatus(siteCode), repository.countBySeverity(siteCode));
    }

    SecurityIncident findOrThrow(UUID id) {
        return repository.findIncident(id).orElseThrow(() -> IncidentException.notFound("SecurityIncident", id));
    }

    public record ReportIncident(String siteCode, IncidentSource source, boolean anonymous, String reporterId,
            String reporterContact, String description, boolean nearMiss, ActorContext actor,
            SourceChannel sourceChannel) {
    }

    public record Dashboard(Map<IncidentStatus, Long> byStatus, Map<Severity, Long> bySeverity) {
    }
}
