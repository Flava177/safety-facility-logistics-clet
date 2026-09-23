package gh.edu.clet.sfl.safetysecurity.cctv.application.port;

import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AlertStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.AnalyticsAlert;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.Camera;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.Disclosure;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceAccessLog;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItem;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItemStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceRequest;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.LiveViewSession;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RetentionPolicy;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RetentionScope;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one port for every S161 aggregate, following {@code AccessControlRepository}'s "one port per
 * module, several aggregates" shape - S161's five requirement areas are one cohesive workflow (a
 * camera's health feeds the SOC, an evidence request produces an item, a retention policy governs
 * that same item, a disclosure releases it), not five unrelated slices.
 */
public interface CctvRepository {

    Camera saveCamera(Camera camera);

    Optional<Camera> findCamera(UUID id);

    Optional<Camera> findCameraByCode(String siteCode, String cameraId);

    List<Camera> findCamerasBySite(String siteCode);

    EvidenceRequest saveEvidenceRequest(EvidenceRequest request);

    Optional<EvidenceRequest> findEvidenceRequest(UUID id);

    List<EvidenceRequest> findEvidenceRequestsBySite(String siteCode);

    EvidenceItem saveEvidenceItem(EvidenceItem item);

    Optional<EvidenceItem> findEvidenceItem(UUID id);

    List<EvidenceItem> findEvidenceItemsByRequest(UUID requestId);

    /** Every {@code ACTIVE} evidence item, whatever the site scope - the retention purge sweep's feed. */
    List<EvidenceItem> findEvidenceItemsByStatus(EvidenceItemStatus status);

    EvidenceAccessLog saveAccessLog(EvidenceAccessLog log);

    List<EvidenceAccessLog> findAccessLogsForItem(UUID evidenceItemId);

    AnalyticsAlert saveAlert(AnalyticsAlert alert);

    Optional<AnalyticsAlert> findAlert(UUID id);

    List<AnalyticsAlert> findAlertsByStatus(String siteCode, AlertStatus status);

    RetentionPolicy saveRetentionPolicy(RetentionPolicy policy);

    Optional<RetentionPolicy> findRetentionPolicy(String siteCode, RetentionScope scope, String scopeRef);

    List<RetentionPolicy> findRetentionPolicies();

    Disclosure saveDisclosure(Disclosure disclosure);

    Optional<Disclosure> findDisclosure(UUID id);

    List<Disclosure> findDisclosuresBySite(String siteCode);

    LiveViewSession saveLiveViewSession(LiveViewSession session);

    Optional<LiveViewSession> findLiveViewSession(UUID id);
}
