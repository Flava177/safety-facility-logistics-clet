package gh.edu.clet.sfl.emergencynotification.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.emergencynotification.application.port.AuditPort;
import gh.edu.clet.sfl.emergencynotification.application.port.InboxAdminPort;
import gh.edu.clet.sfl.emergencynotification.application.port.OutboxAdminPort;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS-SFL-S174-04: outbound integration health and privileged dead-letter replay. */
@Service
public class EmergencyIntegrationService {

    private final EmergencyAccessPolicy access;
    private final OutboxAdminPort outboxAdmin;
    private final InboxAdminPort inbox;
    private final AuditPort audit;

    public EmergencyIntegrationService(EmergencyAccessPolicy access, OutboxAdminPort outboxAdmin,
            InboxAdminPort inbox, AuditPort audit) {
        this.access = access;
        this.outboxAdmin = outboxAdmin;
        this.inbox = inbox;
        this.audit = audit;
    }

    /**
     * The inbound provider feed's health.
     *
     * <p>Closes gap 3. Authorised on {@code EMERGENCY_INTEGRATION_REPLAY} like the outbox read,
     * because both answer the same question for the same operator — even though nothing here is
     * replayable. A rejected inbound message failed signature or schema validation, so the sending
     * system has to correct and re-send it; there is deliberately no inbound replay.
     */
    public InboxAdminPort.InboxHealth inboxHealth(int recentLimit, ActorContext actor) {
        access.requirePermission(actor, SflPermission.EMERGENCY_INTEGRATION_REPLAY, "EmergencyInbox");
        return inbox.health(recentLimit);
    }

    public OutboxAdminPort.OutboxHealth health(ActorContext actor) {
        access.requirePermission(actor, SflPermission.EMERGENCY_INTEGRATION_REPLAY, "EmergencyOutbox");
        return outboxAdmin.health();
    }

    @Transactional
    public boolean replay(UUID messageId, ActorContext actor, SourceChannel channel) {
        access.requirePermission(actor, SflPermission.EMERGENCY_INTEGRATION_REPLAY, "EmergencyOutbox");
        boolean requeued = outboxAdmin.replay(messageId);
        audit.record(actor, channel, "SYSTEM", "INTEGRATION_REPLAYED", "OutboxMessage", messageId.toString(), null,
                Map.of("requeued", requeued), null);
        return requeued;
    }
}
