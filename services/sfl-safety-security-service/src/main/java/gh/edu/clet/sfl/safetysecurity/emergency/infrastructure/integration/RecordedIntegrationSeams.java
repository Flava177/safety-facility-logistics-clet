package gh.edu.clet.sfl.safetysecurity.emergency.infrastructure.integration;

import gh.edu.clet.sfl.safetysecurity.emergency.application.port.IntegrationSeamPorts.AccessControlLockdownPort;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.IntegrationSeamPorts.AudienceDirectoryPort;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.IntegrationSeamPorts.CctvEvidencePort;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.IntegrationSeamPorts.IncidentLinkPort;
import gh.edu.clet.sfl.safetysecurity.emergency.application.port.IntegrationSeamPorts.ReportingPort;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase-1 recorded implementations of the remaining S174 integration seams. Lockdown/CCTV are
 * SEAM-ONLY: this adapter records context references but performs no certified life-safety actuation
 * (Arch §0E). {@code LifeSafetyEventPort} used to be stubbed here too (an always-empty Phase-1
 * placeholder); now that S162a exists to observe something, {@code
 * lifesafety.infrastructure.integration.LifeSafetyEventPortAdapter} is its real implementation, so
 * this class no longer declares it - the real vendor adapter this class's own javadoc anticipated
 * turned out to be one of this codebase's own later modules rather than an outside vendor.
 */
@Component
public class RecordedIntegrationSeams implements AudienceDirectoryPort, IncidentLinkPort,
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
