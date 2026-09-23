package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvRepository;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvErrorCode;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.LiveViewSession;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S161-04: "authorised, role-restricted live viewing of permitted cameras through the VMS,
 * with every live-view session logged (operator, cameras, time)." SFL logs the session; the VMS is the
 * one actually streaming. An operator without {@code CCTV_LIVE_VIEW_START} is blocked and the attempt
 * is logged via the same {@link CctvAccessPolicy#require} path every other S161 permission check uses.
 */
@Service
public class LiveViewService {

    private final CctvRepository repository;
    private final AuditPort audit;
    private final CctvAccessPolicy access;
    private final Clock clock;

    public LiveViewService(CctvRepository repository, AuditPort audit, CctvAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.access = access;
        this.clock = clock;
    }

    public record StartSession(String siteCode, List<String> cameraIds, ActorContext actor) {
    }

    @Transactional
    public LiveViewSession start(StartSession command) {
        ActorContext actor = command.actor();
        if (!access.has(actor, SflPermission.CCTV_LIVE_VIEW_START) || !actor.principal().canAccessSite(
                command.siteCode())) {
            audit.record(actor, "WEB", command.siteCode(), "CCTV_LIVE_VIEW_BLOCKED", "LiveViewSession", null, null,
                    null, "cameras=" + command.cameraIds());
            throw CctvException.of(CctvErrorCode.CCTV_LIVE_VIEW_NOT_PERMITTED);
        }
        Instant now = clock.instant();
        LiveViewSession session = LiveViewSession.start(UUID.randomUUID(), command.siteCode(), actor.actorId(),
                command.cameraIds(), now);
        LiveViewSession saved = repository.saveLiveViewSession(session);
        audit.record(actor, "WEB", saved.siteCode(), "CCTV_LIVE_VIEW_STARTED", "LiveViewSession", saved.id().toString(),
                null, saved, null);
        return saved;
    }

    @Transactional
    public LiveViewSession end(UUID id, ActorContext actor) {
        LiveViewSession session = repository.findLiveViewSession(id)
                .orElseThrow(() -> CctvException.notFound("LiveViewSession", id));
        access.require(actor, SflPermission.CCTV_LIVE_VIEW_START, session.siteCode(), "LiveViewSession",
                id.toString());
        LiveViewSession ended = repository.saveLiveViewSession(session.end(clock.instant()));
        audit.record(actor, "WEB", ended.siteCode(), "CCTV_LIVE_VIEW_ENDED", "LiveViewSession", ended.id().toString(),
                session, ended, null);
        return ended;
    }
}
