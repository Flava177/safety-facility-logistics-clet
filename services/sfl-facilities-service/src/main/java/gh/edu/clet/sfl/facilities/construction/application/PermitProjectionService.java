package gh.edu.clet.sfl.facilities.construction.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.construction.application.ports.ConstructionRepository;
import gh.edu.clet.sfl.facilities.construction.domain.PermitNotice;
import gh.edu.clet.sfl.facilities.construction.domain.PermitRecord;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies S164's permit events to the local projection the S176 start gate reads.
 *
 * <p>Called by {@code PermitEventHandler} inside the inbound listener's transaction, after the inbox
 * has deduplicated the message. No actor authorisation: the event came from another SFL service over
 * the authenticated broker, and the change is recorded against a service account under the
 * {@code INTEGRATION} channel so an auditor can tell it from anything a person did.
 */
@Service
public class PermitProjectionService {

    static final SiteScopedPrincipal SSEMP = new SiteScopedPrincipal("system.ssemp-permits",
            "SSEMP S164 permit events", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final ConstructionContext context;
    private final ConstructionRepository repository;

    public PermitProjectionService(ConstructionContext context) {
        this.context = context;
        this.repository = context.repository();
    }

    /** @return the projection as it now stands */
    @Transactional
    public PermitRecord apply(PermitNotice notice, String correlationId) {
        ActorContext actor = new ActorContext(SSEMP, correlationId == null ? "ssemp:" + notice.permitId()
                : correlationId);
        Optional<PermitRecord> existing = repository.findPermit(notice.permitId());
        PermitRecord next = existing
                .map(permit -> permit.apply(notice, actor.actorId(), context.now(), SourceChannel.INTEGRATION,
                        actor.correlationId()))
                .orElseGet(() -> PermitRecord.first(notice, actor.actorId(), context.now(), SourceChannel.INTEGRATION,
                        actor.correlationId()));
        if (existing.isPresent() && next == existing.get()) {
            return next;
        }
        PermitRecord saved = repository.savePermit(next);
        context.audit().record(actor, SourceChannel.INTEGRATION, AuditAction.PROJECT_PERMIT_STATUS_CHANGED,
                "PermitRecord", saved.id().toString(), saved.siteCode(), existing.orElse(null), saved);
        return saved;
    }
}
