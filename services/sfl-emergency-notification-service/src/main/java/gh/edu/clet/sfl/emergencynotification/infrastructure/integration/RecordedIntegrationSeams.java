package gh.edu.clet.sfl.emergencynotification.infrastructure.integration;

import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.AccessControlLockdownPort;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.AudienceDirectoryPort;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.CctvEvidencePort;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.IncidentLinkPort;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.LifeSafetyEventPort;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.ReportingPort;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase-1 recorded implementations of the S174 integration seams. Life-safety is OBSERVE-ONLY and
 * lockdown/CCTV are SEAM-ONLY: this adapter records context references but performs no certified
 * life-safety actuation (Arch §0E). Real vendor adapters replace these without any domain change.
 */
@Component
public class RecordedIntegrationSeams implements AudienceDirectoryPort, IncidentLinkPort, LifeSafetyEventPort,
        AccessControlLockdownPort, CctvEvidencePort, ReportingPort {

    private static final Logger log = LoggerFactory.getLogger(RecordedIntegrationSeams.class);

    @Override
    public int resolveRecipientCount(String directoryReference, String siteCode) {
        return 0; // No live directory in Phase 1; audience sizing comes from the AudienceGroup record.
    }

    @Override
    public boolean incidentExists(String incidentReference, String siteCode) {
        return incidentReference != null && !incidentReference.isBlank();
    }

    @Override
    public Optional<String> latestLifeSafetyEvent(String siteCode) {
        return Optional.empty(); // Observe-only seam; no live fire/intrusion feed in Phase 1.
    }

    @Override
    public void recordLockdownContext(UUID activationId, String zoneReference) {
        log.info("Recorded lockdown-context intent (no actuation) activation={} zone={}", activationId, zoneReference);
    }

    @Override
    public Optional<String> preserveContext(UUID activationId, String zoneReference) {
        return Optional.of("cctv-preserve://" + activationId);
    }

    @Override
    public void publishSnapshot(String scopeKey, String siteCode, Map<String, Object> counts) {
        log.debug("Recorded dashboard snapshot publish scope={} site={} keys={}", scopeKey, siteCode, counts.keySet());
    }
}
