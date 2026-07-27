package gh.edu.clet.sfl.emergencynotification.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.emergencynotification.application.port.AuditPort;
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
    private final AuditPort audit;

    public EmergencyIntegrationService(EmergencyAccessPolicy access, OutboxAdminPort outboxAdmin, AuditPort audit) {
        this.access = access;
        this.outboxAdmin = outboxAdmin;
        this.audit = audit;
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
