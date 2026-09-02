package gh.edu.clet.sfl.safetysecurity.incident.application.port;

import gh.edu.clet.sfl.safetysecurity.incident.domain.model.CorrectiveAction;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentEvidence;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.IncidentStatus;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.SecurityIncident;
import gh.edu.clet.sfl.safetysecurity.incident.domain.model.Severity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SecurityIncidentRepository {

    SecurityIncident saveIncident(SecurityIncident incident);

    Optional<SecurityIncident> findIncident(UUID id);

    List<SecurityIncident> search(IncidentQuery query);

    CorrectiveAction saveCorrectiveAction(CorrectiveAction action);

    Optional<CorrectiveAction> findCorrectiveAction(UUID id);

    List<CorrectiveAction> findCorrectiveActions(UUID incidentId);

    /** The count {@link SecurityIncident#close} gates on - SRS §D.9 hard rule 1. */
    long countOpenMandatoryCorrectiveActions(UUID incidentId);

    IncidentEvidence saveEvidence(IncidentEvidence evidence);

    List<IncidentEvidence> findEvidence(UUID incidentId);

    /** SRS §D.9 step 8: counts for the site's HSE dashboard. */
    Map<IncidentStatus, Long> countByStatus(String siteCode);

    Map<Severity, Long> countBySeverity(String siteCode);

    record IncidentQuery(String siteCode, IncidentStatus status, Severity severity, int limit) {
    }
}
