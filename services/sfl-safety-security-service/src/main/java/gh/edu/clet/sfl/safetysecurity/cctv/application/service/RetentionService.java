package gh.edu.clet.sfl.safetysecurity.cctv.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.cctv.application.port.CctvRepository;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.event.CctvEventType;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.exception.CctvException;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItem;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.EvidenceItemStatus;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RetentionPolicy;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.RetentionScope;
import gh.edu.clet.sfl.safetysecurity.cctv.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S161-05: footage retention governance. SFL does not hold raw video (see {@link EvidenceItem}),
 * so "purge" here means retiring SFL's own reference/hash record once its camera or zone policy's
 * retention window elapses, unless a legal hold applies - the VMS purges its own recordings on its own
 * schedule regardless. The default retention window applies to any camera without its own policy.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final CctvRepository repository;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final CctvAccessPolicy access;
    private final Clock clock;
    private final int defaultRetentionDays;

    public RetentionService(CctvRepository repository, AuditPort audit, IntegrationEventPublisher events,
            CctvAccessPolicy access, Clock clock,
            @Value("${sfl.cctv.retention.default-days:30}") int defaultRetentionDays) {
        this.repository = repository;
        this.audit = audit;
        this.events = events;
        this.access = access;
        this.clock = clock;
        this.defaultRetentionDays = defaultRetentionDays;
    }

    public record DefinePolicy(String siteCode, RetentionScope scope, String scopeRef, int retentionDays,
            ActorContext actor) {
    }

    @Transactional
    public RetentionPolicy define(DefinePolicy command) {
        ActorContext actor = command.actor();
        access.require(actor, SflPermission.CCTV_RETENTION_MANAGE, command.siteCode(), "RetentionPolicy", null);
        Instant now = clock.instant();
        RetentionPolicy policy = RetentionPolicy.define(java.util.UUID.randomUUID(), command.siteCode(),
                command.scope(), command.scopeRef(), command.retentionDays(), actor.actorId(), now, SourceChannel.WEB,
                actor.correlationId());
        RetentionPolicy saved = repository.saveRetentionPolicy(policy);
        audit.record(actor, SourceChannel.WEB.name(), saved.siteCode(), "CCTV_RETENTION_POLICY_DEFINED",
                "RetentionPolicy", saved.id().toString(), null, saved, null);
        return saved;
    }

    @Transactional
    public RetentionPolicy placeLegalHold(String siteCode, RetentionScope scope, String scopeRef, String reason,
            ActorContext actor) {
        access.require(actor, SflPermission.CCTV_RETENTION_MANAGE, siteCode, "RetentionPolicy", scopeRef);
        RetentionPolicy policy = requirePolicy(siteCode, scope, scopeRef);
        RetentionPolicy held = repository.saveRetentionPolicy(policy.placeLegalHold(reason, actor.actorId(),
                clock.instant(), SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), held.siteCode(), "CCTV_RETENTION_LEGAL_HOLD_PLACED",
                "RetentionPolicy", held.id().toString(), policy, held, reason);
        return held;
    }

    @Transactional
    public RetentionPolicy releaseLegalHold(String siteCode, RetentionScope scope, String scopeRef,
            ActorContext actor) {
        access.require(actor, SflPermission.CCTV_RETENTION_MANAGE, siteCode, "RetentionPolicy", scopeRef);
        RetentionPolicy policy = requirePolicy(siteCode, scope, scopeRef);
        RetentionPolicy released = repository.saveRetentionPolicy(policy.releaseLegalHold(actor.actorId(),
                clock.instant(), SourceChannel.WEB, actor.correlationId()));
        audit.record(actor, SourceChannel.WEB.name(), released.siteCode(), "CCTV_RETENTION_LEGAL_HOLD_RELEASED",
                "RetentionPolicy", released.id().toString(), policy, released, null);
        return released;
    }

    /**
     * The scheduled purge sweep - SRS-SFL-S161-05: "a camera's retention period elapses ... its footage
     * is purged unless a legal hold applies." Evaluates every {@code ACTIVE} evidence item against its
     * camera's own policy, or the default, and purges (marks {@link EvidenceItemStatus#PURGED}) the
     * ones past their window, skipping any covered by an active legal hold.
     */
    @Scheduled(fixedDelayString = "${sfl.cctv.retention.sweep.fixed-delay:PT1H}")
    @Transactional
    public void purgeExpiredEvidence() {
        Instant now = clock.instant();
        for (EvidenceItem item : repository.findEvidenceItemsByStatus(EvidenceItemStatus.ACTIVE)) {
            Optional<RetentionPolicy> policy = repository.findRetentionPolicy(item.siteCode(), RetentionScope.CAMERA,
                    item.cameraId());
            int retentionDays = policy.map(RetentionPolicy::retentionDays).orElse(defaultRetentionDays);
            boolean legalHold = policy.map(RetentionPolicy::legalHold).orElse(false);
            if (legalHold) {
                continue;
            }
            Instant retentionExpiry = item.metadata().createdAt().plus(Duration.ofDays(retentionDays));
            if (now.isAfter(retentionExpiry)) {
                EvidenceItem purged = repository.saveEvidenceItem(item.purge("system:cctv-retention-sweep", now,
                        SourceChannel.SCHEDULER, "retention-purge-sweep"));
                audit.record(systemActor(purged.siteCode()), SourceChannel.SCHEDULER.name(), purged.siteCode(),
                        "CCTV_EVIDENCE_ITEM_PURGED", "EvidenceItem", purged.id().toString(), item, purged, null);
                events.publish(CctvEventType.RETENTION_PURGED.eventType(), CctvEventType.RETENTION_PURGED.version(),
                        "EvidenceItem", purged.id().toString(), purged.siteCode(), systemActor(purged.siteCode()),
                        Map.of("cameraId", purged.cameraId()));
                log.info("Evidence item {} purged automatically at site {}", purged.id(), purged.siteCode());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<RetentionPolicy> allPolicies(ActorContext actor) {
        access.require(actor, SflPermission.CCTV_RETENTION_MANAGE, null, "RetentionPolicy", null);
        return repository.findRetentionPolicies();
    }

    private RetentionPolicy requirePolicy(String siteCode, RetentionScope scope, String scopeRef) {
        return repository.findRetentionPolicy(siteCode, scope, scopeRef)
                .orElseThrow(() -> CctvException.notFound("RetentionPolicy", null));
    }

    private static ActorContext systemActor(String siteCode) {
        return new ActorContext(new SiteScopedPrincipal("system:cctv-retention-sweep", "CCTV Retention Purge Sweep",
                Set.of(SflRole.SFL_ADMIN), Set.of("*"), true), "retention-purge-sweep");
    }
}
