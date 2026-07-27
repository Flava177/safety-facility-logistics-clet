package gh.edu.clet.sfl.emergencynotification.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Provider-neutral integration seams for S174. Grouped here as small interfaces so the workflow depends
 * only on contracts, never on vendor SDKs. Phase-1 recorded adapters implement these; life-safety and
 * lockdown/CCTV are strictly observe-only / seam-only — SFL never actuates certified life-safety hardware.
 */
public final class IntegrationSeamPorts {

    private IntegrationSeamPorts() {
    }

    /** Resolves the recipient count for an audience directory reference (recorded adapter in Phase 1). */
    public interface AudienceDirectoryPort {
        int resolveRecipientCount(String directoryReference, String siteCode);
    }

    /** Validates an incident linkage by reference/ID only (no cross-schema coupling). */
    public interface IncidentLinkPort {
        boolean incidentExists(String incidentReference, String siteCode);
    }

    /** OBSERVE-ONLY feed of the latest fire/intrusion life-safety event; never drives actuation. */
    public interface LifeSafetyEventPort {
        Optional<String> latestLifeSafetyEvent(String siteCode);
    }

    /** SEAM-ONLY: records a lockdown-context intent for S160a; performs no certified actuation. */
    public interface AccessControlLockdownPort {
        void recordLockdownContext(UUID activationId, String zoneReference);
    }

    /** SEAM-ONLY: records a CCTV-preservation context reference for S161; copies no video. */
    public interface CctvEvidencePort {
        Optional<String> preserveContext(UUID activationId, String zoneReference);
    }

    /** Publishes a dashboard read-model snapshot to the reporting platform. */
    public interface ReportingPort {
        void publishSnapshot(String scopeKey, String siteCode, java.util.Map<String, Object> counts);
    }
}
