package gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvRepository;
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
import org.springframework.stereotype.Repository;

/** The one adapter behind {@link CctvRepository}, following {@code AccessControlRepositoryAdapter}'s shape. */
@Repository
public class CctvRepositoryAdapter implements CctvRepository {

    private final CameraJpaRepository cameras;
    private final EvidenceRequestJpaRepository evidenceRequests;
    private final EvidenceItemJpaRepository evidenceItems;
    private final EvidenceAccessLogJpaRepository accessLogs;
    private final AnalyticsAlertJpaRepository alerts;
    private final RetentionPolicyJpaRepository retentionPolicies;
    private final DisclosureJpaRepository disclosures;
    private final LiveViewSessionJpaRepository liveViewSessions;

    public CctvRepositoryAdapter(CameraJpaRepository cameras, EvidenceRequestJpaRepository evidenceRequests,
            EvidenceItemJpaRepository evidenceItems, EvidenceAccessLogJpaRepository accessLogs,
            AnalyticsAlertJpaRepository alerts, RetentionPolicyJpaRepository retentionPolicies,
            DisclosureJpaRepository disclosures, LiveViewSessionJpaRepository liveViewSessions) {
        this.cameras = cameras;
        this.evidenceRequests = evidenceRequests;
        this.evidenceItems = evidenceItems;
        this.accessLogs = accessLogs;
        this.alerts = alerts;
        this.retentionPolicies = retentionPolicies;
        this.disclosures = disclosures;
        this.liveViewSessions = liveViewSessions;
    }

    @Override
    public Camera saveCamera(Camera camera) {
        CameraJpaEntity entity = cameras.findById(camera.id()).orElseGet(CameraJpaEntity::new);
        entity.apply(camera);
        return cameras.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<Camera> findCamera(UUID id) {
        return cameras.findById(id).map(CameraJpaEntity::toDomain);
    }

    @Override
    public Optional<Camera> findCameraByCode(String siteCode, String cameraId) {
        return cameras.findBySiteCodeAndCameraId(siteCode, cameraId).map(CameraJpaEntity::toDomain);
    }

    @Override
    public List<Camera> findCamerasBySite(String siteCode) {
        return cameras.findBySiteCode(siteCode).stream().map(CameraJpaEntity::toDomain).toList();
    }

    @Override
    public EvidenceRequest saveEvidenceRequest(EvidenceRequest request) {
        EvidenceRequestJpaEntity entity = evidenceRequests.findById(request.id())
                .orElseGet(EvidenceRequestJpaEntity::new);
        entity.apply(request);
        return evidenceRequests.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<EvidenceRequest> findEvidenceRequest(UUID id) {
        return evidenceRequests.findById(id).map(EvidenceRequestJpaEntity::toDomain);
    }

    @Override
    public List<EvidenceRequest> findEvidenceRequestsBySite(String siteCode) {
        return evidenceRequests.findBySiteCode(siteCode).stream().map(EvidenceRequestJpaEntity::toDomain).toList();
    }

    @Override
    public EvidenceItem saveEvidenceItem(EvidenceItem item) {
        EvidenceItemJpaEntity entity = evidenceItems.findById(item.id()).orElseGet(EvidenceItemJpaEntity::new);
        entity.apply(item);
        return evidenceItems.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<EvidenceItem> findEvidenceItem(UUID id) {
        return evidenceItems.findById(id).map(EvidenceItemJpaEntity::toDomain);
    }

    @Override
    public List<EvidenceItem> findEvidenceItemsByRequest(UUID requestId) {
        return evidenceItems.findByRequestId(requestId).stream().map(EvidenceItemJpaEntity::toDomain).toList();
    }

    @Override
    public List<EvidenceItem> findEvidenceItemsByStatus(EvidenceItemStatus status) {
        return evidenceItems.findByStatus(status).stream().map(EvidenceItemJpaEntity::toDomain).toList();
    }

    @Override
    public EvidenceAccessLog saveAccessLog(EvidenceAccessLog log) {
        return accessLogs.save(EvidenceAccessLogJpaEntity.from(log)).toDomain();
    }

    @Override
    public List<EvidenceAccessLog> findAccessLogsForItem(UUID evidenceItemId) {
        return accessLogs.findByEvidenceItemIdOrderByOccurredAtDesc(evidenceItemId).stream()
                .map(EvidenceAccessLogJpaEntity::toDomain).toList();
    }

    @Override
    public AnalyticsAlert saveAlert(AnalyticsAlert alert) {
        AnalyticsAlertJpaEntity entity = alerts.findById(alert.id()).orElseGet(AnalyticsAlertJpaEntity::new);
        entity.apply(alert);
        return alerts.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<AnalyticsAlert> findAlert(UUID id) {
        return alerts.findById(id).map(AnalyticsAlertJpaEntity::toDomain);
    }

    @Override
    public List<AnalyticsAlert> findAlertsByStatus(String siteCode, AlertStatus status) {
        return alerts.findBySiteCodeAndStatus(siteCode, status).stream().map(AnalyticsAlertJpaEntity::toDomain)
                .toList();
    }

    @Override
    public RetentionPolicy saveRetentionPolicy(RetentionPolicy policy) {
        RetentionPolicyJpaEntity entity = retentionPolicies.findById(policy.id())
                .orElseGet(RetentionPolicyJpaEntity::new);
        entity.apply(policy);
        return retentionPolicies.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<RetentionPolicy> findRetentionPolicy(String siteCode, RetentionScope scope, String scopeRef) {
        return retentionPolicies.findBySiteCodeAndScopeAndScopeRef(siteCode, scope, scopeRef)
                .map(RetentionPolicyJpaEntity::toDomain);
    }

    @Override
    public List<RetentionPolicy> findRetentionPolicies() {
        return retentionPolicies.findAll().stream().map(RetentionPolicyJpaEntity::toDomain).toList();
    }

    @Override
    public Disclosure saveDisclosure(Disclosure disclosure) {
        DisclosureJpaEntity entity = disclosures.findById(disclosure.id()).orElseGet(DisclosureJpaEntity::new);
        entity.apply(disclosure);
        return disclosures.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<Disclosure> findDisclosure(UUID id) {
        return disclosures.findById(id).map(DisclosureJpaEntity::toDomain);
    }

    @Override
    public List<Disclosure> findDisclosuresBySite(String siteCode) {
        return disclosures.findBySiteCode(siteCode).stream().map(DisclosureJpaEntity::toDomain).toList();
    }

    @Override
    public LiveViewSession saveLiveViewSession(LiveViewSession session) {
        LiveViewSessionJpaEntity entity = liveViewSessions.findById(session.id())
                .orElseGet(LiveViewSessionJpaEntity::new);
        entity.apply(session);
        return liveViewSessions.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<LiveViewSession> findLiveViewSession(UUID id) {
        return liveViewSessions.findById(id).map(LiveViewSessionJpaEntity::toDomain);
    }
}
