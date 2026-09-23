package gh.edu.clet.sfl.safetysecurity.cctv.support;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvRepository;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvVendorGatewayPort;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.IncidentSeedingPort;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.SiemForwarderPort;
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
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** In-memory {@link CctvRepository} for S161 application-layer unit tests - a real implementation of
 * the port's contract, following {@code AccessControlTestDoubles}'s idiom. */
public final class CctvTestDoubles {

    private CctvTestDoubles() {
    }

    public static ActorContext actor(String subject, SflRole role, String... sites) {
        return new ActorContext(new SiteScopedPrincipal(subject, subject, Set.of(role), Set.of(sites), false),
                "corr-test");
    }

    public static final class InMemoryCctvRepository implements CctvRepository {

        private final Map<UUID, Camera> cameras = new LinkedHashMap<>();
        private final Map<UUID, EvidenceRequest> requests = new LinkedHashMap<>();
        private final Map<UUID, EvidenceItem> items = new LinkedHashMap<>();
        private final Map<UUID, EvidenceAccessLog> accessLogs = new LinkedHashMap<>();
        private final Map<UUID, AnalyticsAlert> alerts = new LinkedHashMap<>();
        private final Map<String, RetentionPolicy> retentionPolicies = new LinkedHashMap<>();
        private final Map<UUID, Disclosure> disclosures = new LinkedHashMap<>();
        private final Map<UUID, LiveViewSession> liveViewSessions = new LinkedHashMap<>();

        @Override
        public Camera saveCamera(Camera camera) {
            cameras.put(camera.id(), camera);
            return camera;
        }

        @Override
        public Optional<Camera> findCamera(UUID id) {
            return Optional.ofNullable(cameras.get(id));
        }

        @Override
        public Optional<Camera> findCameraByCode(String siteCode, String cameraId) {
            return cameras.values().stream()
                    .filter(c -> c.siteCode().equalsIgnoreCase(siteCode) && c.cameraId().equals(cameraId))
                    .findFirst();
        }

        @Override
        public List<Camera> findCamerasBySite(String siteCode) {
            return cameras.values().stream().filter(c -> c.siteCode().equalsIgnoreCase(siteCode)).toList();
        }

        @Override
        public EvidenceRequest saveEvidenceRequest(EvidenceRequest request) {
            requests.put(request.id(), request);
            return request;
        }

        @Override
        public Optional<EvidenceRequest> findEvidenceRequest(UUID id) {
            return Optional.ofNullable(requests.get(id));
        }

        @Override
        public List<EvidenceRequest> findEvidenceRequestsBySite(String siteCode) {
            return requests.values().stream().filter(r -> r.siteCode().equalsIgnoreCase(siteCode)).toList();
        }

        @Override
        public EvidenceItem saveEvidenceItem(EvidenceItem item) {
            items.put(item.id(), item);
            return item;
        }

        @Override
        public Optional<EvidenceItem> findEvidenceItem(UUID id) {
            return Optional.ofNullable(items.get(id));
        }

        @Override
        public List<EvidenceItem> findEvidenceItemsByRequest(UUID requestId) {
            return items.values().stream().filter(i -> i.requestId().equals(requestId)).toList();
        }

        @Override
        public List<EvidenceItem> findEvidenceItemsByStatus(EvidenceItemStatus status) {
            return items.values().stream().filter(i -> i.status() == status).toList();
        }

        @Override
        public EvidenceAccessLog saveAccessLog(EvidenceAccessLog log) {
            accessLogs.put(log.id(), log);
            return log;
        }

        @Override
        public List<EvidenceAccessLog> findAccessLogsForItem(UUID evidenceItemId) {
            return accessLogs.values().stream().filter(l -> l.evidenceItemId().equals(evidenceItemId)).toList();
        }

        @Override
        public AnalyticsAlert saveAlert(AnalyticsAlert alert) {
            alerts.put(alert.id(), alert);
            return alert;
        }

        @Override
        public Optional<AnalyticsAlert> findAlert(UUID id) {
            return Optional.ofNullable(alerts.get(id));
        }

        @Override
        public List<AnalyticsAlert> findAlertsByStatus(String siteCode, AlertStatus status) {
            return alerts.values().stream()
                    .filter(a -> a.siteCode().equalsIgnoreCase(siteCode) && a.status() == status).toList();
        }

        @Override
        public RetentionPolicy saveRetentionPolicy(RetentionPolicy policy) {
            retentionPolicies.put(key(policy.siteCode(), policy.scope(), policy.scopeRef()), policy);
            return policy;
        }

        @Override
        public Optional<RetentionPolicy> findRetentionPolicy(String siteCode, RetentionScope scope, String scopeRef) {
            return Optional.ofNullable(retentionPolicies.get(key(siteCode, scope, scopeRef)));
        }

        @Override
        public List<RetentionPolicy> findRetentionPolicies() {
            return List.copyOf(retentionPolicies.values());
        }

        @Override
        public Disclosure saveDisclosure(Disclosure disclosure) {
            disclosures.put(disclosure.id(), disclosure);
            return disclosure;
        }

        @Override
        public Optional<Disclosure> findDisclosure(UUID id) {
            return Optional.ofNullable(disclosures.get(id));
        }

        @Override
        public List<Disclosure> findDisclosuresBySite(String siteCode) {
            return disclosures.values().stream().filter(d -> d.siteCode().equalsIgnoreCase(siteCode)).toList();
        }

        @Override
        public LiveViewSession saveLiveViewSession(LiveViewSession session) {
            liveViewSessions.put(session.id(), session);
            return session;
        }

        @Override
        public Optional<LiveViewSession> findLiveViewSession(UUID id) {
            return Optional.ofNullable(liveViewSessions.get(id));
        }

        private static String key(String siteCode, RetentionScope scope, String scopeRef) {
            return siteCode.toUpperCase() + ":" + scope + ":" + scopeRef;
        }
    }

    /** Records what it would have retrieved from the VMS - never fabricates a real export, matching
     * {@code RecordedCctvVendorGateway}'s real behaviour. */
    public static final class FakeVendorGateway implements CctvVendorGatewayPort {
        public final List<EvidenceRequest> retrieved = new ArrayList<>();

        @Override
        public RetrievalResult retrieve(EvidenceRequest approvedRequest, String cameraId, Instant windowStart,
                Instant windowEnd, ActorContext actor) {
            retrieved.add(approvedRequest);
            String handle = "FAKE-" + UUID.randomUUID();
            String hash = sha256(approvedRequest.id() + "|" + cameraId + "|" + windowStart + "|" + windowEnd);
            return new RetrievalResult("FAKE", false, handle, hash, "fake provenance");
        }

        private static String sha256(String value) {
            try {
                return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static final class FakeSiemForwarder implements SiemForwarderPort {
        public final List<AnalyticsAlert> forwarded = new ArrayList<>();

        @Override
        public ForwardResult forward(AnalyticsAlert alert, ActorContext actor) {
            forwarded.add(alert);
            return new ForwardResult("FAKE", true);
        }
    }

    public static final class FakeIncidentSeedingPort implements IncidentSeedingPort {
        public final List<String> descriptions = new ArrayList<>();
        public UUID nextIncidentId = UUID.randomUUID();

        @Override
        public UUID seed(String siteCode, String description, ActorContext actor) {
            descriptions.add(description);
            return nextIncidentId;
        }
    }

    public static final class FakeAuditPort
            implements gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort {
        public int recordCalls;

        @Override
        public void record(ActorContext actor, String sourceChannel, String siteScope, String action,
                String resourceType, String resourceId, Object beforeValue, Object afterValue, String reason) {
            recordCalls++;
        }

        @Override
        public AuditVerification verifyChain() {
            return new AuditVerification(true, recordCalls, null, null);
        }
    }

    public static final class FakeEventPublisher
            implements gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher {
        public int publishCalls;

        @Override
        public void publish(String eventType, int eventVersion, String aggregateType, String aggregateId,
                String siteScope, ActorContext actor, Map<String, Object> payload) {
            publishCalls++;
        }
    }
}
