package gh.edu.clet.sfl.emergencynotification.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.emergencynotification.application.port.CommandIdempotencyPort;
import gh.edu.clet.sfl.emergencynotification.application.port.AuditPort;
import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository;
import gh.edu.clet.sfl.emergencynotification.application.port.EvidencePort;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.AccessControlLockdownPort;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.CctvEvidencePort;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationSeamPorts.LifeSafetyEventPort;
import gh.edu.clet.sfl.emergencynotification.application.port.NotificationGatewayPort;
import gh.edu.clet.sfl.emergencynotification.domain.event.EmergencyEventType;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyErrorCode;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyException;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelStatus;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationActivation;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationChannel;
import gh.edu.clet.sfl.emergencynotification.domain.model.Priority;
import gh.edu.clet.sfl.emergencynotification.domain.model.RecordMetadata;
import gh.edu.clet.sfl.emergencynotification.domain.model.RetentionClass;
import gh.edu.clet.sfl.emergencynotification.domain.model.SiteCode;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;
import gh.edu.clet.sfl.emergencynotification.domain.policy.BreakGlassPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS-SFL-S174-02: the emergency notification activation workflow, including break-glass and all-clear. */
@Service
public class ActivationService {

    private final EmergencyRepository repository;
    private final EmergencyAccessPolicy access;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final NotificationGatewayPort gateway;
    private final EvidencePort evidence;
    private final LifeSafetyEventPort lifeSafety;
    private final AccessControlLockdownPort lockdown;
    private final CctvEvidencePort cctv;
    private final CommandIdempotencyPort idempotency;
    private final Clock clock;

    public ActivationService(EmergencyRepository repository, EmergencyAccessPolicy access, AuditPort audit,
            IntegrationEventPublisher events, NotificationGatewayPort gateway, EvidencePort evidence,
            LifeSafetyEventPort lifeSafety, AccessControlLockdownPort lockdown, CctvEvidencePort cctv,
            CommandIdempotencyPort idempotency, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.events = events;
        this.gateway = gateway;
        this.evidence = evidence;
        this.lifeSafety = lifeSafety;
        this.lockdown = lockdown;
        this.cctv = cctv;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public record CreateActivation(String siteCode, UUID scenarioId, UUID templateId, List<UUID> audienceGroupIds,
            List<UUID> recipientZoneIds, List<ChannelType> channels, Priority priority, String incidentReference,
            String idempotencyKey, ActorContext actor, SourceChannel channel) {}

    public record EvidenceMeta(String fileName, String contentType, String storageReference, String sha256Hash,
            RetentionClass retentionClass) {}

    public record ActivationStatusView(NotificationActivation activation, List<NotificationChannel> channels,
            long acknowledgements) {}

    @Transactional
    public NotificationActivation createDraft(CreateActivation c) {
        SiteCode site = requireSite(c.siteCode());
        access.require(c.actor(), SflPermission.EMERGENCY_ACTIVATION_CREATE, site.value(), "NotificationActivation",
                null);
        String fingerprint = idempotency.fingerprint(activationPayload(c));
        var replay = idempotency.findExistingResult("create-emergency-activation", c.idempotencyKey(), fingerprint);
        if (replay.isPresent()) {
            return activation(replay.get(), c.actor(), SflPermission.EMERGENCY_ACTIVATION_READ);
        }
        var activation = new NotificationActivation(UUID.randomUUID(), EmergencyNumbers.next("ACT"), site,
                c.scenarioId(), c.templateId(), c.audienceGroupIds(), c.recipientZoneIds(), c.channels(),
                NotificationActivation.Mode.ROUTINE, NotificationActivation.Status.DRAFT,
                c.priority() == null ? Priority.HIGH : c.priority(), c.incidentReference(), null, null, null, null,
                null, null, null, null, null, null, null, 0, false, null, null, meta(c.actor(), c.channel()));
        var saved = repository.saveActivation(activation);
        history(saved, null, "create", c.actor());
        audit.record(c.actor(), c.channel(), site.value(), "CREATE", "NotificationActivation", saved.id().toString(),
                null, saved, null);
        idempotency.recordResult("create-emergency-activation", c.idempotencyKey(), fingerprint, saved.id(),
                site.value(), c.actor().actorId());
        return saved;
    }

    @Transactional
    public NotificationActivation submit(UUID id, ActorContext actor, SourceChannel channel) {
        var before = activation(id, actor, SflPermission.EMERGENCY_ACTIVATION_CREATE);
        var after = transition(before, before.submit(meta(before, actor, channel)), "submit", actor, channel);
        events.publish(EmergencyEventType.EMERGENCY_ACTIVATION_SUBMITTED, "NotificationActivation", id.toString(),
                after.siteCode().value(), actor, Map.of("activationId", id, "status", after.status()));
        return after;
    }

    @Transactional
    public NotificationActivation approve(UUID id, ActorContext actor, SourceChannel channel) {
        var before = requireActivation(id);
        access.requireApproval(actor, SflPermission.EMERGENCY_ACTIVATION_APPROVE, before.siteCode().value(),
                "NotificationActivation", id.toString());
        var after = transition(before, before.approve(actor.actorId(), meta(before, actor, channel)), "approve",
                actor, channel);
        events.publish(EmergencyEventType.EMERGENCY_ACTIVATION_APPROVED, "NotificationActivation", id.toString(),
                after.siteCode().value(), actor, Map.of("activationId", id, "approvedBy", actor.actorId()));
        return after;
    }

    @Transactional
    public NotificationActivation reject(UUID id, String reason, ActorContext actor, SourceChannel channel) {
        var before = requireActivation(id);
        access.requireApproval(actor, SflPermission.EMERGENCY_ACTIVATION_APPROVE, before.siteCode().value(),
                "NotificationActivation", id.toString());
        return transition(before, before.reject(reason, meta(before, actor, channel)), "reject", actor, channel);
    }

    @Transactional
    public NotificationActivation activate(UUID id, ActorContext actor, SourceChannel channel) {
        var before = activation(id, actor, SflPermission.EMERGENCY_ACTIVATION_SEND);
        long start = clock.millis();
        var activated = before.activate(meta(before, actor, channel));
        activated = fanOut(activated, false, actor, channel);
        activated = activated.withFastLaneMillis(clock.millis() - start,
                activated.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId()));
        var after = transition(before, activated, "activate", actor, channel);
        events.publish(EmergencyEventType.EMERGENCY_NOTIFICATION_ACTIVATED, "NotificationActivation", id.toString(),
                after.siteCode().value(), actor, Map.of("activationId", id, "channels", channelNames(after),
                        "mode", after.mode()));
        return after;
    }

    /** SRS/§0E break-glass: authorised role + break-glass-eligible template fires WITHOUT pre-approval. */
    @Transactional
    public NotificationActivation breakGlass(CreateActivation c) {
        SiteCode site = requireSite(c.siteCode());
        access.require(c.actor(), SflPermission.EMERGENCY_BREAK_GLASS_SEND, site.value(), "NotificationActivation",
                null);
        String fingerprint = idempotency.fingerprint(activationPayload(c));
        var replay = idempotency.findExistingResult("break-glass-emergency-activation", c.idempotencyKey(),
                fingerprint);
        if (replay.isPresent()) {
            return activation(replay.get(), c.actor(), SflPermission.EMERGENCY_ACTIVATION_READ);
        }
        boolean templateEligible = c.templateId() != null && repository.findTemplate(c.templateId())
                .map(gh.edu.clet.sfl.emergencynotification.domain.model.NotificationTemplate::breakGlassEligible)
                .orElse(false);
        boolean scenarioEligible = c.scenarioId() != null && repository.findScenario(c.scenarioId())
                .map(gh.edu.clet.sfl.emergencynotification.domain.model.EmergencyScenario::breakGlassEligible)
                .orElse(false);
        BreakGlassPolicy.requireEligible(templateEligible, scenarioEligible);
        long start = clock.millis();
        var activation = new NotificationActivation(UUID.randomUUID(), EmergencyNumbers.next("BG"), site,
                c.scenarioId(), c.templateId(), c.audienceGroupIds(), c.recipientZoneIds(), c.channels(),
                NotificationActivation.Mode.BREAK_GLASS, NotificationActivation.Status.DRAFT,
                c.priority() == null ? Priority.CRITICAL : c.priority(), c.incidentReference(), null, null, null, null,
                null, null, null, null, null, null, null, 0, false, null, null, meta(c.actor(), c.channel()));
        var saved = repository.saveActivation(activation);
        var live = saved.breakGlassActivate(meta(saved, c.actor(), c.channel()));
        live = fanOut(live, false, c.actor(), c.channel());
        live = live.withFastLaneMillis(clock.millis() - start,
                live.metadata().modifiedBy(c.actor().actorId(), clock.instant(), c.channel(), c.actor().correlationId()));
        var after = transition(saved, live, "break-glass", c.actor(), c.channel());
        events.publish(EmergencyEventType.EMERGENCY_BREAK_GLASS_ACTIVATED, "NotificationActivation",
                after.id().toString(), site.value(), c.actor(), Map.of("activationId", after.id(),
                        "channels", channelNames(after), "priority", after.priority()));
        events.publish(EmergencyEventType.EMERGENCY_NOTIFICATION_ACTIVATED, "NotificationActivation",
                after.id().toString(), site.value(), c.actor(), Map.of("activationId", after.id(), "mode",
                        after.mode()));
        idempotency.recordResult("break-glass-emergency-activation", c.idempotencyKey(), fingerprint, after.id(),
                site.value(), c.actor().actorId());
        return after;
    }

    private Map<String, Object> activationPayload(CreateActivation c) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("siteCode", c.siteCode());
        payload.put("scenarioId", c.scenarioId());
        payload.put("templateId", c.templateId());
        payload.put("audienceGroupIds", c.audienceGroupIds());
        payload.put("recipientZoneIds", c.recipientZoneIds());
        payload.put("channels", c.channels());
        payload.put("priority", c.priority());
        payload.put("incidentReference", c.incidentReference());
        return payload;
    }

    @Transactional
    public NotificationActivation afterActionApprove(UUID id, String justification, ActorContext actor,
            SourceChannel channel) {
        var before = requireActivation(id);
        access.requireApproval(actor, SflPermission.EMERGENCY_AFTER_ACTION_APPROVE, before.siteCode().value(),
                "NotificationActivation", id.toString());
        var after = transition(before, before.afterActionApprove(actor.actorId(), justification,
                meta(before, actor, channel)), "after-action-approve", actor, channel);
        events.publish(EmergencyEventType.EMERGENCY_AFTER_ACTION_APPROVED, "NotificationActivation", id.toString(),
                after.siteCode().value(), actor, Map.of("activationId", id, "approvedBy", actor.actorId()));
        return after;
    }

    @Transactional
    public NotificationActivation allClear(UUID id, ActorContext actor, SourceChannel channel) {
        var before = activation(id, actor, SflPermission.EMERGENCY_ALL_CLEAR_SEND);
        var after = transition(before, before.allClear(meta(before, actor, channel)), "all-clear", actor, channel);
        events.publish(EmergencyEventType.EMERGENCY_ALL_CLEAR_SENT, "NotificationActivation", id.toString(),
                after.siteCode().value(), actor, Map.of("activationId", id));
        return after;
    }

    @Transactional
    public NotificationActivation close(UUID id, String reason, EvidenceMeta evidenceMeta, ActorContext actor,
            SourceChannel channel) {
        var before = activation(id, actor, SflPermission.EMERGENCY_ACTIVATION_SEND);
        if (evidenceMeta == null || evidenceMeta.storageReference() == null
                || evidenceMeta.storageReference().isBlank()) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_CLOSURE_EVIDENCE_MISSING,
                    Map.of("activationId", id.toString()));
        }
        if (evidenceMeta.retentionClass() == null) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_RETENTION_CLASS_MISSING,
                    Map.of("activationId", id.toString()));
        }
        UUID evidenceId = evidence.register(new EvidencePort.EvidenceRegistration(before.siteCode().value(), id,
                "CLOSURE_SUMMARY", evidenceMeta.fileName(), evidenceMeta.contentType(),
                evidenceMeta.storageReference(), evidenceMeta.sha256Hash(), evidenceMeta.retentionClass(), actor,
                channel));
        var channels = repository.findChannels(id);
        String deliverySummary = deliverySummary(channels);
        String ackSummary = "acknowledged=" + repository.countAcknowledgements(id) + "; " + deliverySummary;
        var after = transition(before, before.close(reason, deliverySummary, ackSummary, evidenceId,
                meta(before, actor, channel)), "close", actor, channel);
        events.publish(EmergencyEventType.EMERGENCY_ACTIVATION_CLOSED, "NotificationActivation", id.toString(),
                after.siteCode().value(), actor, Map.of("activationId", id, "closureReason", reason));
        return after;
    }

    /** Scheduled/authorised SLA escalation of an active activation with outstanding acknowledgements. */
    @Transactional
    public void escalateForSla(UUID id, ActorContext actor, SourceChannel channel) {
        var before = repository.findActivation(id).orElse(null);
        if (before == null || !before.active() || before.status() == NotificationActivation.Status.ESCALATED) {
            return;
        }
        var after = transition(before, before.escalate("Acknowledgement SLA breached", meta(before, actor, channel)),
                "escalate", actor, channel);
        events.publish(EmergencyEventType.EMERGENCY_NOTIFICATION_STATUS_RECEIVED, "NotificationActivation",
                id.toString(), after.siteCode().value(), actor, Map.of("activationId", id, "status", after.status(),
                        "escalationLevel", after.escalationLevel()));
    }

    // ---- queries ---------------------------------------------------------------------------------

    public NotificationActivation get(UUID id, ActorContext actor) {
        return activation(id, actor, SflPermission.EMERGENCY_ACTIVATION_READ);
    }

    public List<NotificationActivation> list(String site, NotificationActivation.Status status, ActorContext actor) {
        access.require(actor, SflPermission.EMERGENCY_ACTIVATION_READ, site, "NotificationActivation", null);
        return repository.findActivations(List.of(SiteCode.of(site).value()), status, 200);
    }

    public ActivationStatusView status(UUID id, ActorContext actor) {
        var activation = activation(id, actor, SflPermission.EMERGENCY_ACTIVATION_READ);
        return new ActivationStatusView(activation, repository.findChannels(id),
                repository.countAcknowledgements(id));
    }

    // ---- internals -------------------------------------------------------------------------------

    private NotificationActivation fanOut(NotificationActivation activation, boolean degraded, ActorContext actor,
            SourceChannel channel) {
        int target = targetCount(activation);
        for (ChannelType type : activation.channels()) {
            var result = gateway.send(activation.id(), type, activation.siteCode().value(), target, degraded, actor);
            var existing = repository.findChannel(activation.id(), type);
            var record = existing.orElseGet(() -> new NotificationChannel(UUID.randomUUID(), activation.id(),
                    activation.siteCode(), type, ChannelStatus.PENDING, target, 0, 0, 0, 0,
                    meta(activation, actor, channel)));
            var sent = new NotificationChannel(record.id(), activation.id(), activation.siteCode(), type,
                    ChannelStatus.SENDING, target, result.accepted(), 0, 0, 0,
                    record.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId()));
            repository.saveChannel(sent);
        }
        // Observe-only / seam-only context (no certified life-safety actuation — Arch §0E).
        lifeSafety.latestLifeSafetyEvent(activation.siteCode().value());
        for (UUID zone : activation.recipientZoneIds()) {
            lockdown.recordLockdownContext(activation.id(), zone.toString());
            cctv.preserveContext(activation.id(), zone.toString());
        }
        return activation;
    }

    private int targetCount(NotificationActivation activation) {
        int total = 0;
        for (UUID audienceId : activation.audienceGroupIds()) {
            total += repository.findAudienceGroup(audienceId)
                    .map(gh.edu.clet.sfl.emergencynotification.domain.model.AudienceGroup::recipientCount).orElse(0);
        }
        return total;
    }

    private static String deliverySummary(List<NotificationChannel> channels) {
        int sent = 0;
        int delivered = 0;
        int failed = 0;
        for (NotificationChannel c : channels) {
            sent += c.sentCount();
            delivered += c.deliveredCount();
            failed += c.failedCount();
        }
        return "channels=" + channels.size() + "; sent=" + sent + "; delivered=" + delivered + "; failed=" + failed;
    }

    private static List<String> channelNames(NotificationActivation a) {
        return a.channels().stream().map(Enum::name).toList();
    }

    private NotificationActivation transition(NotificationActivation before, NotificationActivation after,
            String action, ActorContext actor, SourceChannel channel) {
        var saved = repository.saveActivation(after);
        history(saved, before.status().name(), action, actor);
        audit.record(actor, channel, saved.siteCode().value(), "STATE_TRANSITION", "NotificationActivation",
                saved.id().toString(), before, saved, null);
        return saved;
    }

    private void history(NotificationActivation a, String fromStatus, String action, ActorContext actor) {
        repository.saveActivationHistory(a.id(), fromStatus, a.status().name(), action, actor.actorId(), null,
                clock.instant(), actor.correlationId());
    }

    private NotificationActivation activation(UUID id, ActorContext actor, SflPermission permission) {
        var a = requireActivation(id);
        access.require(actor, permission, a.siteCode().value(), "NotificationActivation", id.toString());
        return a;
    }

    private NotificationActivation requireActivation(UUID id) {
        return repository.findActivation(id).orElseThrow(() -> EmergencyException.notFound("NotificationActivation", id));
    }

    private RecordMetadata meta(ActorContext actor, SourceChannel channel) {
        Instant now = clock.instant();
        return RecordMetadata.createdBy(actor.actorId(), now, channel, actor.correlationId());
    }

    private RecordMetadata meta(NotificationActivation a, ActorContext actor, SourceChannel channel) {
        return a.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId());
    }

    private static SiteCode requireSite(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) {
            throw new EmergencyException(EmergencyErrorCode.EMERGENCY_MISSING_SITE_SCOPE);
        }
        return SiteCode.of(siteCode);
    }
}
