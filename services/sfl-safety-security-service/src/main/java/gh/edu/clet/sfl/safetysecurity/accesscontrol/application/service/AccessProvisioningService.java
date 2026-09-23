package gh.edu.clet.sfl.safetysecurity.accesscontrol.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.application.port.AccessControlRepository;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.event.AccessControlEventType;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.exception.AccessControlException;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessProvisioning;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ProvisioningBasis;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.ProvisioningStatus;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.integration.AccessControlIntegrationInbox;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S160a-02: joiner-mover-leaver access provisioning from HRMS/IAM events, and the manual
 * grant that is "the exception, not the norm" - see {@link AccessProvisioning}, which refuses to
 * construct a manual grant without a reason, approver and expiry.
 */
@Service
public class AccessProvisioningService {

    private final AccessControlRepository repository;
    private final AccessControlIntegrationInbox inbox;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final AccessControlAccessPolicy access;
    private final Clock clock;

    public AccessProvisioningService(AccessControlRepository repository, AccessControlIntegrationInbox inbox,
            AuditPort audit, IntegrationEventPublisher events, AccessControlAccessPolicy access, Clock clock) {
        this.repository = repository;
        this.inbox = inbox;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
    }

    public record JoinerMoverLeaverEvent(String source, String externalEventId, Instant signedAt, String signature,
            String rawPayload, Map<String, Object> payload, String siteCode, String personRef, String zoneCode,
            ProvisioningBasis basis, ActorContext actor) {
    }

    public record ManualGrant(String siteCode, String personRef, String zoneCode, String reason, String approverId,
            Instant expiresAt, ActorContext actor) {
    }

    /** HRMS/IAM emits a joiner/mover/leaver event -> maps to zone entitlements -> provisions, adjusts
     * or revokes -> synchronised (today: recorded only - see the vendor gateway's javadoc) -> audited. */
    @Transactional
    public AccessProvisioning applyJoinerMoverLeaver(JoinerMoverLeaverEvent command) {
        inbox.accept(new AccessControlIntegrationInbox.InboundMessage(command.source(), command.externalEventId(),
                AccessControlEventType.ACCESS_PROVISIONING_GRANTED.eventType(), command.siteCode(),
                command.signedAt(), command.signature(), command.rawPayload(), command.payload(),
                List.of("personRef", "zoneCode", "basis"), command.actor()));

        ActorContext actor = command.actor();
        Instant now = clock.instant();
        if (command.basis() == ProvisioningBasis.LEAVER) {
            return revokeAllForPerson(command.siteCode(), command.personRef(), actor, now);
        }
        AccessProvisioning provisioning = AccessProvisioning.fromJoinerMoverEvent(UUID.randomUUID(),
                command.siteCode(), command.personRef(), command.zoneCode(), command.basis(), actor.actorId(), now,
                SourceChannel.INTEGRATION, actor.correlationId());
        return saveAndPublishGrant(provisioning, actor);
    }

    @Transactional
    public AccessProvisioning grantManually(ManualGrant command) {
        ActorContext actor = command.actor();
        access.require(actor, SflPermission.ACCESS_PROVISIONING_MANAGE, command.siteCode(), "AccessProvisioning",
                null);
        Instant now = clock.instant();
        AccessProvisioning provisioning = AccessProvisioning.manualGrant(UUID.randomUUID(), command.siteCode(),
                command.personRef(), command.zoneCode(), command.reason(), command.approverId(), command.expiresAt(),
                actor.actorId(), now, SourceChannel.WEB, actor.correlationId());
        return saveAndPublishGrant(provisioning, actor);
    }

    @Transactional
    public AccessProvisioning revoke(UUID id, ActorContext actor) {
        access.require(actor, SflPermission.ACCESS_PROVISIONING_MANAGE, null, "AccessProvisioning", id.toString());
        AccessProvisioning provisioning = repository.findProvisioning(id)
                .orElseThrow(() -> AccessControlException.notFound("AccessProvisioning", id));
        AccessProvisioning revoked = repository.saveProvisioning(provisioning.revoke(actor.actorId(), clock.instant(),
                SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), revoked.siteCode(), "ACCESS_PROVISIONING_REVOKED",
                "AccessProvisioning", revoked.id().toString(), provisioning, revoked, null);
        events.publish(AccessControlEventType.ACCESS_PROVISIONING_REVOKED.eventType(),
                AccessControlEventType.ACCESS_PROVISIONING_REVOKED.version(), "AccessProvisioning",
                revoked.id().toString(), revoked.siteCode(), actor, Map.of("personRef", revoked.personRef()));
        return revoked;
    }

    @Transactional(readOnly = true)
    public List<AccessProvisioning> forPerson(String siteCode, String personRef, ActorContext actor) {
        access.require(actor, SflPermission.ACCESS_PROVISIONING_READ, siteCode, "AccessProvisioning", null);
        return repository.findProvisioningForPerson(siteCode, personRef);
    }

    private AccessProvisioning revokeAllForPerson(String siteCode, String personRef, ActorContext actor, Instant now) {
        List<AccessProvisioning> active = repository.findProvisioningForPerson(siteCode, personRef).stream()
                .filter(p -> p.status() == ProvisioningStatus.ACTIVE).toList();
        AccessProvisioning last = null;
        for (AccessProvisioning provisioning : active) {
            AccessProvisioning revoked = repository.saveProvisioning(provisioning.revoke(actor.actorId(), now,
                    SourceChannel.INTEGRATION, actor.correlationId()));
            audit.record(actor, SourceChannel.INTEGRATION.name(), revoked.siteCode(), "ACCESS_PROVISIONING_REVOKED",
                    "AccessProvisioning", revoked.id().toString(), provisioning, revoked, "Leaver event");
            events.publish(AccessControlEventType.ACCESS_PROVISIONING_REVOKED.eventType(),
                    AccessControlEventType.ACCESS_PROVISIONING_REVOKED.version(), "AccessProvisioning",
                    revoked.id().toString(), revoked.siteCode(), actor, Map.of("personRef", revoked.personRef(),
                            "reason", "leaver"));
            last = revoked;
        }
        if (last == null) {
            throw AccessControlException.notFound("AccessProvisioning", null);
        }
        return last;
    }

    private AccessProvisioning saveAndPublishGrant(AccessProvisioning provisioning, ActorContext actor) {
        AccessProvisioning saved = repository.saveProvisioning(provisioning);
        audit.record(actor, saved.metadata().sourceChannel().name(), saved.siteCode(), "ACCESS_PROVISIONING_GRANTED",
                "AccessProvisioning", saved.id().toString(), null, saved, null);
        events.publish(AccessControlEventType.ACCESS_PROVISIONING_GRANTED.eventType(),
                AccessControlEventType.ACCESS_PROVISIONING_GRANTED.version(), "AccessProvisioning",
                saved.id().toString(), saved.siteCode(), actor, Map.of("personRef", saved.personRef(), "zoneCode",
                        saved.zoneCode(), "basis", saved.basis().name()));
        return saved;
    }
}
