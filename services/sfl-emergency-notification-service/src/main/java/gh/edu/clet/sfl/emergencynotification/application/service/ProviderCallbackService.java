package gh.edu.clet.sfl.emergencynotification.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.emergencynotification.application.port.AuditPort;
import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository;
import gh.edu.clet.sfl.emergencynotification.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.emergencynotification.domain.event.EmergencyEventType;
import gh.edu.clet.sfl.emergencynotification.domain.exception.EmergencyException;
import gh.edu.clet.sfl.emergencynotification.domain.model.Acknowledgement;
import gh.edu.clet.sfl.emergencynotification.domain.model.ChannelType;
import gh.edu.clet.sfl.emergencynotification.domain.model.DeliveryReceipt;
import gh.edu.clet.sfl.emergencynotification.domain.model.DeliveryStatus;
import gh.edu.clet.sfl.emergencynotification.domain.model.NotificationActivation;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;
import gh.edu.clet.sfl.emergencynotification.infrastructure.integration.EmergencyIntegrationInbox;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SRS-SFL-S174-04: provider delivery-status and acknowledgement callbacks. Every payload passes the secure
 * inbox (HMAC/allowlist/schema/idempotency) BEFORE any domain side effect; updates are idempotent by
 * {@code (activationId, provider, providerMessageId)} and {@code (activationId, recipientRef)}.
 */
@Service
public class ProviderCallbackService {

    private final EmergencyRepository repository;
    private final EmergencyIntegrationInbox inbox;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public ProviderCallbackService(EmergencyRepository repository, EmergencyIntegrationInbox inbox, AuditPort audit,
            IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.inbox = inbox;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record ProviderCallback(String provider, String signature, Instant signedAt, String rawPayload,
            Map<String, Object> payload, ActorContext actor) {}

    @Transactional
    public DeliveryReceipt deliveryStatus(ProviderCallback c) {
        String activationRef = str(c.payload(), "activationId");
        String providerMessageId = str(c.payload(), "providerMessageId");
        String siteCode = str(c.payload(), "siteCode");
        inbox.accept(new EmergencyIntegrationInbox.InboundMessage(c.provider(),
                activationRef + ":" + providerMessageId, "sfl.ssemp.emergency-notification-status-received.v1",
                siteCode, c.signedAt(), c.signature(), c.rawPayload(), c.payload(),
                List.of("activationId", "providerMessageId", "status"), c.actor()));
        NotificationActivation activation = requireActivation(activationRef);
        ChannelType channelType = ChannelType.valueOf(str(c.payload(), "channelType"));
        DeliveryStatus status = DeliveryStatus.valueOf(str(c.payload(), "status"));
        var existing = repository.findReceipt(activation.id(), c.provider(), providerMessageId);
        if (existing.isPresent()) {
            return existing.get();
        }
        var receipt = new DeliveryReceipt(UUID.randomUUID(), activation.id(), activation.siteCode(), channelType,
                c.provider(), providerMessageId, nullable(c.payload(), "recipientRef"), status,
                nullable(c.payload(), "reason"), clock.instant(), c.actor().actorId(), clock.instant(),
                SourceChannel.INTEGRATION, c.actor().correlationId());
        var saved = repository.saveReceipt(receipt);
        repository.findChannel(activation.id(), channelType).ifPresent(ch -> repository.saveChannel(
                ch.recordDelivery(status, ch.metadata().modifiedBy(c.actor().actorId(), clock.instant(),
                        SourceChannel.INTEGRATION, c.actor().correlationId()))));
        audit.record(c.actor(), SourceChannel.INTEGRATION, activation.siteCode().value(), "INTEGRATION_ACCEPTED",
                "DeliveryReceipt", saved.id().toString(), null, saved, null);
        events.publish(EmergencyEventType.EMERGENCY_NOTIFICATION_STATUS_RECEIVED, "DeliveryReceipt",
                saved.id().toString(), activation.siteCode().value(), c.actor(), Map.of("activationId", activation.id(),
                        "status", status, "channel", channelType));
        return saved;
    }

    @Transactional
    public Acknowledgement acknowledgement(ProviderCallback c) {
        String activationRef = str(c.payload(), "activationId");
        String recipientRef = str(c.payload(), "recipientRef");
        String siteCode = str(c.payload(), "siteCode");
        inbox.accept(new EmergencyIntegrationInbox.InboundMessage(c.provider(),
                activationRef + ":ack:" + recipientRef, "sfl.ssemp.emergency-acknowledgement-received.v1", siteCode,
                c.signedAt(), c.signature(), c.rawPayload(), c.payload(),
                List.of("activationId", "recipientRef"), c.actor()));
        NotificationActivation activation = requireActivation(activationRef);
        var existing = repository.findAcknowledgement(activation.id(), recipientRef);
        if (existing.isPresent()) {
            return existing.get();
        }
        ChannelType channelType = c.payload().get("channelType") == null ? null
                : ChannelType.valueOf(str(c.payload(), "channelType"));
        var ack = new Acknowledgement(UUID.randomUUID(), activation.id(), activation.siteCode(), channelType,
                recipientRef, clock.instant(), c.actor().actorId(), clock.instant(), SourceChannel.INTEGRATION,
                c.actor().correlationId());
        var saved = repository.saveAcknowledgement(ack);
        if (channelType != null) {
            repository.findChannel(activation.id(), channelType).ifPresent(ch -> repository.saveChannel(
                    ch.recordAcknowledgement(ch.metadata().modifiedBy(c.actor().actorId(), clock.instant(),
                            SourceChannel.INTEGRATION, c.actor().correlationId()))));
        }
        audit.record(c.actor(), SourceChannel.INTEGRATION, activation.siteCode().value(), "INTEGRATION_ACCEPTED",
                "Acknowledgement", saved.id().toString(), null, saved, null);
        events.publish(EmergencyEventType.EMERGENCY_ACKNOWLEDGEMENT_RECEIVED, "Acknowledgement", saved.id().toString(),
                activation.siteCode().value(), c.actor(), Map.of("activationId", activation.id(), "recipientRef",
                        recipientRef));
        return saved;
    }

    private NotificationActivation requireActivation(String ref) {
        UUID id;
        try {
            id = UUID.fromString(ref);
        } catch (IllegalArgumentException e) {
            throw EmergencyException.notFound("NotificationActivation", null);
        }
        return repository.findActivation(id)
                .orElseThrow(() -> EmergencyException.notFound("NotificationActivation", id));
    }

    private static String str(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return String.valueOf(value).strip();
    }

    private static String nullable(Map<String, Object> payload, String field) {
        Object value = payload == null ? null : payload.get(field);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).strip();
    }
}
