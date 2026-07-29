package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchEvidencePort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchEvidenceSupport.EvidenceMeta;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditEvent;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateActiveIdentifierException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S171-01/05: courier item register, item lifecycle, inbound registration and distribution. */
@Service
public class CourierItemService {

    private final DispatchRepository repository;
    private final DispatchAccessPolicy access;
    private final DispatchEvidencePort evidence;
    private final DispatchExceptionService exceptions;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public CourierItemService(DispatchRepository repository, DispatchAccessPolicy access, DispatchEvidencePort evidence,
            DispatchExceptionService exceptions, AuditPort audit, IntegrationEventPublisher events, Clock clock) {
        this.repository = repository; this.access = access; this.evidence = evidence; this.exceptions = exceptions;
        this.audit = audit; this.events = events; this.clock = clock;
    }

    public record RegisterItem(String siteCode, String itemNumber, CourierItem.Direction direction,
            CourierItem.Type itemType, CourierItem.Sensitivity sensitivity, String origin, String destination,
            String sender, String recipient, String assignedHandler, ActorContext actor, SourceChannel channel) {}

    public record DistributeInbound(UUID itemId, String acknowledgedBy, String distributionReference,
            EvidenceMeta evidence, ActorContext actor, SourceChannel channel) {}

    @Transactional
    public CourierItem registerItem(RegisterItem c) {
        SflPermission permission = c.direction() == CourierItem.Direction.INBOUND
                ? SflPermission.DISPATCH_INBOUND_REGISTER : SflPermission.DISPATCH_ITEM_REGISTER;
        access.require(c.actor(), permission, c.siteCode(), "CourierItem", null);
        String number = c.itemNumber() == null || c.itemNumber().isBlank()
                ? DispatchNumbers.next(c.direction() == CourierItem.Direction.INBOUND ? "IN" : "OUT")
                : c.itemNumber().strip();
        if (repository.findItemByNumber(c.siteCode(), number).isPresent()) {
            throw DuplicateActiveIdentifierException.of("CourierItem", "itemNumber", number, c.siteCode());
        }
        Instant now = clock.instant();
        boolean custody = CourierItem.custodyRequired(c.itemType(), c.sensitivity());
        var item = new CourierItem(UUID.randomUUID(), number, SiteCode.of(c.siteCode()), c.direction(), c.itemType(),
                c.sensitivity(), custody, c.origin(), c.destination(), c.sender(), c.recipient(), c.assignedHandler(),
                CourierItem.Status.RECEIVED, null, null, null, null, null, false, null,
                RecordMetadata.createdBy(c.actor().actorId(), now, c.channel(), c.actor().correlationId()));
        var saved = repository.saveItem(item);
        audit.record(c.actor(), c.channel(), saved.siteCode(), AuditAction.CREATE, "CourierItem",
                saved.id().toString(), null, saved);
        events.publish(c.direction() == CourierItem.Direction.INBOUND
                        ? FleetEventType.INBOUND_ITEM_REGISTERED : FleetEventType.DISPATCH_ITEM_REGISTERED,
                "CourierItem", saved.id().toString(), saved.siteCode(), c.actor(),
                Map.of("itemId", saved.id(), "itemNumber", saved.itemNumber(), "direction", saved.direction(),
                        "sensitivity", saved.sensitivity(), "chainOfCustodyRequired", saved.chainOfCustodyRequired()));
        return saved;
    }

    @Transactional
    public CourierItem advanceItem(UUID id, String action, ActorContext actor, SourceChannel channel) {
        var before = item(id, actor);
        access.require(actor, SflPermission.DISPATCH_ITEM_MANAGE, before.siteCode().value(), "CourierItem", id.toString());
        var meta = before.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId());
        var after = switch (action) {
            case "stage" -> before.stage(meta);
            case "dispatch" -> before.dispatched(meta);
            case "in-transit" -> before.inTransit(meta);
            case "deliver" -> before.delivered(meta);
            case "return" -> before.returnedToOrigin(meta);
            case "close" -> before.close(meta);
            default -> throw new IllegalArgumentException("Unknown item transition");
        };
        after = repository.saveItem(after);
        audit.record(actor, channel, after.siteCode(), AuditAction.STATE_TRANSITION, "CourierItem", id.toString(),
                before, after);
        return after;
    }

    @Transactional
    public CourierItem misrouteItem(UUID id, String reason, String handler, ActorContext actor, SourceChannel channel) {
        var before = item(id, actor);
        access.require(actor, SflPermission.DISPATCH_ITEM_MANAGE, before.siteCode().value(), "CourierItem", id.toString());
        var after = repository.saveItem(before.reroute(reason, handler,
                before.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId())));
        audit.record(actor, channel, after.siteCode(), AuditAction.UPDATE, "CourierItem", id.toString(), before, after);
        return after;
    }

    @Transactional
    public CourierItem distributeInbound(DistributeInbound c) {
        var before = item(c.itemId(), c.actor());
        access.require(c.actor(), SflPermission.DISPATCH_INBOUND_DISTRIBUTE, before.siteCode().value(), "CourierItem",
                c.itemId().toString());
        UUID evidenceId = DispatchEvidenceSupport.registerIfPresent(evidence, before.siteCode(), "CourierItem",
                before.id().toString(), "DISTRIBUTION_ACKNOWLEDGEMENT", c.evidence(), c.actor(), c.channel());
        Instant now = clock.instant();
        var meta = before.metadata().modifiedBy(c.actor().actorId(), now, c.channel(), c.actor().correlationId());
        var distributed = before.distribute(c.acknowledgedBy(), now, evidenceId, c.distributionReference(), meta);
        var closed = repository.saveItem(distributed.close(distributed.metadata()
                .modifiedBy(c.actor().actorId(), now, c.channel(), c.actor().correlationId())));
        audit.record(c.actor(), c.channel(), closed.siteCode(), AuditAction.STATE_TRANSITION, "CourierItem",
                closed.id().toString(), before, closed);
        events.publish(FleetEventType.INBOUND_ITEM_DISTRIBUTED, "CourierItem", closed.id().toString(),
                closed.siteCode(), c.actor(), Map.of("itemId", closed.id(), "acknowledgedBy", c.acknowledgedBy(),
                        "evidenceId", evidenceId == null ? "" : evidenceId.toString()));
        return closed;
    }

    /** Scheduled/authorised flag of an undelivered inbound item; opens an accountable exception case. */
    @Transactional
    public CourierItem flagUndelivered(UUID id, String reason, ActorContext actor, SourceChannel channel) {
        var before = repository.findItem(id).orElseThrow(() -> RecordNotFoundException.of("CourierItem", id));
        access.require(actor, SflPermission.DISPATCH_ITEM_MANAGE, before.siteCode().value(), "CourierItem", id.toString());
        if (before.undelivered() || before.status() == CourierItem.Status.EXCEPTION) return before;
        var after = repository.saveItem(before.markUndelivered(reason,
                before.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId())));
        audit.record(actor, channel, after.siteCode(), AuditAction.STATE_TRANSITION, "CourierItem", id.toString(),
                before, after);
        exceptions.openCase(new DispatchExceptionService.OpenCase(after.siteCode().value(),
                DispatchExceptionCase.Type.UNDELIVERED_ITEM, DispatchExceptionCase.Severity.MEDIUM, false,
                "UNDELIVERED_ITEM:" + after.id(), after.id(), null, null, null, null, List.of(reason), actor, channel));
        return after;
    }

    public CourierItem item(UUID id, ActorContext actor) {
        var item = repository.findItem(id).orElseThrow(() -> RecordNotFoundException.of("CourierItem", id));
        access.require(actor, SflPermission.DISPATCH_ITEM_READ, item.siteCode().value(), "CourierItem", id.toString());
        return item;
    }

    public DispatchRepository.DispatchPage<CourierItem> items(String site, CourierItem.Direction direction,
            CourierItem.Status status, CourierItem.Sensitivity sensitivity, String handler, String reference,
            UUID dispatchId, Boolean undelivered, Instant from, Instant to, DispatchRepository.Paging paging,
            ActorContext actor) {
        access.require(actor, SflPermission.DISPATCH_ITEM_READ, site, "CourierItem", null);
        return repository.findItems(new DispatchRepository.ItemQuery(List.of(SiteCode.of(site).value()), direction,
                status, sensitivity, handler, reference, dispatchId, undelivered, from, to, paging));
    }

    /**
     * The item's transition history.
     *
     * <p>Every state change already reaches the audit log through {@link AuditPort}; what was missing
     * was a dispatch-side read authorised against the record itself, so a detail screen could show a
     * real timeline instead of reconstructing one from whatever fields the record still carried.
     */
    public List<AuditEvent> history(UUID id, ActorContext actor) {
        var item = item(id, actor);
        return audit.search(new AuditPort.AuditQuery(List.of(item.siteCode().value()), "CourierItem", id.toString(),
                null, null, null, null, 0, 200));
    }
}
