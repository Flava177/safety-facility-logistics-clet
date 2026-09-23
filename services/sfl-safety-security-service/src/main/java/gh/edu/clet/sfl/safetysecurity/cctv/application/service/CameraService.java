package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvRepository;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.event.CctvEventType;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.Camera;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.CameraHealthStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RecordingStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.cctv.infrastructure.integration.CctvIntegrationInbox;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S161-01: camera inventory and health monitoring. A camera going offline or faulty publishes
 * {@code CAMERA_HEALTH_CHANGED} on the shared outbox and stops there - S161 does not itself call
 * sfl-facilities-service to raise an IFIMP work order or touch hall-readiness scoring; that is a
 * different service's schema entirely, and no drainer exists yet anywhere in this codebase to deliver
 * the event (see the implementation notes).
 */
@Service
public class CameraService {

    private final CctvRepository repository;
    private final CctvIntegrationInbox inbox;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final CctvAccessPolicy access;
    private final Clock clock;

    public CameraService(CctvRepository repository, CctvIntegrationInbox inbox, AuditPort audit,
            IntegrationEventPublisher events, CctvAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.inbox = inbox;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    public record RegisterCamera(String siteCode, String cameraId, String name, String locationRef,
            String coverageArea, boolean examinationArea, ActorContext actor) {
    }

    public record ReportHealth(String source, String externalEventId, Instant signedAt, String signature,
            String rawPayload, Map<String, Object> payload, String siteCode, String cameraId,
            CameraHealthStatus status, RecordingStatus recordingStatus, Instant observedAt, ActorContext actor) {
    }

    @Transactional
    public Camera register(RegisterCamera command) {
        ActorContext actor = command.actor();
        access.require(actor, SflPermission.CCTV_CAMERA_READ, command.siteCode(), "Camera", null);
        Instant now = clock.instant();
        Camera camera = Camera.register(UUID.randomUUID(), command.siteCode(), command.cameraId(), command.name(),
                command.locationRef(), command.coverageArea(), command.examinationArea(), actor.actorId(), now,
                SourceChannel.WEB, actor.correlationId());
        Camera saved = repository.saveCamera(camera);
        audit.record(actor, SourceChannel.WEB.name(), saved.siteCode(), "CCTV_CAMERA_REGISTERED", "Camera",
                saved.id().toString(), null, saved, null);
        return saved;
    }

    @Transactional
    public Camera reportHealth(ReportHealth command) {
        inbox.accept(new CctvIntegrationInbox.InboundMessage(command.source(), command.externalEventId(),
                CctvEventType.CAMERA_HEALTH_CHANGED.eventType(), command.siteCode(), command.signedAt(),
                command.signature(), command.rawPayload(), command.payload(), List.of("cameraId", "status"),
                command.actor()));

        ActorContext actor = command.actor();
        Instant now = clock.instant();
        Camera camera = repository.findCameraByCode(command.siteCode(), command.cameraId())
                .orElseThrow(() -> CctvException.notFound("Camera", null));
        CameraHealthStatus previousStatus = camera.healthStatus();
        Camera updated = camera.updateHealth(command.status(), command.recordingStatus(), command.observedAt(),
                actor.actorId(), now, SourceChannel.INTEGRATION, actor.correlationId());
        Camera saved = repository.saveCamera(updated);

        audit.record(actor, SourceChannel.INTEGRATION.name(), saved.siteCode(), "CCTV_CAMERA_HEALTH_CHANGED",
                "Camera", saved.id().toString(), camera, saved, null);
        if (previousStatus != saved.healthStatus()) {
            events.publish(CctvEventType.CAMERA_HEALTH_CHANGED.eventType(),
                    CctvEventType.CAMERA_HEALTH_CHANGED.version(), "Camera", saved.id().toString(), saved.siteCode(),
                    actor, Map.of("cameraId", saved.cameraId(), "status", saved.healthStatus().name(),
                            "examinationArea", saved.examinationArea()));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Camera> forSite(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.CCTV_CAMERA_READ, siteCode, "Camera", null);
        return repository.findCamerasBySite(siteCode);
    }

    @Transactional(readOnly = true)
    public Optional<Camera> find(UUID id, ActorContext actor) {
        Optional<Camera> camera = repository.findCamera(id);
        camera.ifPresent(c -> access.require(actor, SflPermission.CCTV_CAMERA_READ, c.siteCode(), "Camera",
                id.toString()));
        return camera;
    }
}
