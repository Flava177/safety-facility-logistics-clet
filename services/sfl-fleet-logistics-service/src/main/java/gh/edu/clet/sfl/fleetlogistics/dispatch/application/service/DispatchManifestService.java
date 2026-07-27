package gh.edu.clet.sfl.fleetlogistics.dispatch.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchFleetReferencePort;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.CourierItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.Dispatch;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchManifestItem;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.CustodyChainPolicy;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy.DispatchClosurePolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
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

/**
 * S171-02: dispatch manifest lifecycle — create, add items, seal (seal IDs + counts), assign the optional
 * S166 carrying trip, dispatch, and gated closure. Closure is blocked while an exception is open or a
 * custody gap is unresolved ({@link DispatchClosurePolicy} + {@link CustodyChainPolicy}).
 */
@Service
public class DispatchManifestService {

    private final DispatchRepository repository;
    private final DispatchAccessPolicy access;
    private final DispatchFleetReferencePort fleet;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public DispatchManifestService(DispatchRepository repository, DispatchAccessPolicy access,
            DispatchFleetReferencePort fleet, AuditPort audit, IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.fleet = fleet;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record CreateManifest(String siteCode, String manifestNumber, String route, String assignedHandler,
            String destinationCentre, String examinationContext, UUID tripId, UUID vehicleId, UUID driverId,
            ActorContext actor, SourceChannel channel) {}

    public record AddManifestItem(UUID dispatchId, UUID courierItemId, String expectedSealId, int expectedQuantity,
            ActorContext actor, SourceChannel channel) {}

    @Transactional
    public Dispatch createManifest(CreateManifest c) {
        access.require(c.actor(), SflPermission.DISPATCH_MANIFEST_CREATE, c.siteCode(), "Dispatch", null);
        fleet.validate(c.tripId(), c.vehicleId(), c.driverId(), c.siteCode());
        String number = c.manifestNumber() == null || c.manifestNumber().isBlank()
                ? DispatchNumbers.next("DSP") : c.manifestNumber().strip();
        if (repository.findDispatchByNumber(c.siteCode(), number).isPresent()) {
            throw DuplicateActiveIdentifierException.of("Dispatch", "manifestNumber", number, c.siteCode());
        }
        Instant now = clock.instant();
        var dispatch = new Dispatch(UUID.randomUUID(), number, SiteCode.of(c.siteCode()), c.route(),
                c.assignedHandler(), c.destinationCentre(), c.examinationContext(), c.tripId(), c.vehicleId(),
                c.driverId(), 0, List.of(), Dispatch.Status.DRAFT, null, null, null, null,
                RecordMetadata.createdBy(c.actor().actorId(), now, c.channel(), c.actor().correlationId()));
        var saved = repository.saveDispatch(dispatch);
        audit.record(c.actor(), c.channel(), saved.siteCode(), AuditAction.CREATE, "Dispatch", saved.id().toString(),
                null, saved);
        events.publish(FleetEventType.DISPATCH_CREATED, "Dispatch", saved.id().toString(), saved.siteCode(), c.actor(),
                Map.of("dispatchId", saved.id(), "manifestNumber", saved.manifestNumber(), "route", saved.route()));
        return saved;
    }

    @Transactional
    public DispatchManifestItem addItem(AddManifestItem c) {
        var dispatch = dispatch(c.dispatchId(), c.actor());
        access.require(c.actor(), SflPermission.DISPATCH_MANIFEST_CREATE, dispatch.siteCode().value(), "Dispatch",
                dispatch.id().toString());
        if (dispatch.status() != Dispatch.Status.DRAFT) {
            throw new IllegalStateException("Items can only be added while the manifest is a draft");
        }
        // Reject a dispatch of an unregistered item (SRS-SFL-S171 UNREGISTERED_ITEM).
        var item = repository.findItem(c.courierItemId())
                .orElseThrow(() -> RecordNotFoundException.of("CourierItem", c.courierItemId()));
        if (!item.siteCode().value().equals(dispatch.siteCode().value())) {
            throw RecordNotFoundException.of("CourierItem", c.courierItemId());
        }
        if (!item.active()) {
            throw new IllegalStateException("Only an active courier item can be added to a manifest");
        }
        Instant now = clock.instant();
        var manifestItem = new DispatchManifestItem(UUID.randomUUID(), dispatch.id(), item.id(), dispatch.siteCode(),
                repository.nextManifestSequence(dispatch.id()), c.expectedSealId(),
                c.expectedQuantity() <= 0 ? 1 : c.expectedQuantity(), DispatchManifestItem.ReturnStatus.PENDING, null,
                null, now);
        var saved = repository.saveManifestItem(manifestItem);
        int count = repository.findManifestItems(dispatch.id()).size();
        var meta = dispatch.metadata().modifiedBy(c.actor().actorId(), now, c.channel(), c.actor().correlationId());
        repository.saveDispatch(dispatch.updateManifest(count, dispatch.sealIds(), meta));
        audit.record(c.actor(), c.channel(), dispatch.siteCode(), AuditAction.UPDATE, "Dispatch",
                dispatch.id().toString(), null, Map.of("addedItemId", item.id(), "manifestItemId", saved.id()));
        return saved;
    }

    @Transactional
    public Dispatch seal(UUID dispatchId, List<String> sealIds, ActorContext actor, SourceChannel channel) {
        var before = dispatch(dispatchId, actor);
        access.require(actor, SflPermission.DISPATCH_MANIFEST_CREATE, before.siteCode().value(), "Dispatch",
                dispatchId.toString());
        int count = repository.findManifestItems(dispatchId).size();
        var meta = before.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId());
        var sealed = repository.saveDispatch(before.updateManifest(count, sealIds, meta).seal(meta));
        audit.record(actor, channel, sealed.siteCode(), AuditAction.STATE_TRANSITION, "Dispatch",
                dispatchId.toString(), before, sealed);
        return sealed;
    }

    @Transactional
    public Dispatch assignTrip(UUID dispatchId, UUID tripId, UUID vehicleId, UUID driverId, ActorContext actor,
            SourceChannel channel) {
        var before = dispatch(dispatchId, actor);
        access.require(actor, SflPermission.DISPATCH_MANIFEST_CREATE, before.siteCode().value(), "Dispatch",
                dispatchId.toString());
        fleet.validate(tripId, vehicleId, driverId, before.siteCode().value());
        var meta = before.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId());
        var after = repository.saveDispatch(before.assignTrip(tripId, vehicleId, driverId, meta));
        audit.record(actor, channel, after.siteCode(), AuditAction.UPDATE, "Dispatch", dispatchId.toString(), before,
                after);
        return after;
    }

    @Transactional
    public Dispatch dispatch(UUID dispatchId, ActorContext actor, SourceChannel channel) {
        var before = dispatch(dispatchId, actor);
        access.require(actor, SflPermission.DISPATCH_MANIFEST_CREATE, before.siteCode().value(), "Dispatch",
                dispatchId.toString());
        Instant now = clock.instant();
        var meta = before.metadata().modifiedBy(actor.actorId(), now, channel, actor.correlationId());
        var dispatched = repository.saveDispatch(before.dispatch(now, meta));
        for (var manifestItem : repository.findManifestItems(dispatchId)) {
            if (manifestItem.returnStatus() != DispatchManifestItem.ReturnStatus.RETURNED) {
                repository.saveManifestItem(manifestItem.markPending());
            }
        }
        audit.record(actor, channel, dispatched.siteCode(), AuditAction.STATE_TRANSITION, "Dispatch",
                dispatchId.toString(), before, dispatched);
        events.publish(FleetEventType.DISPATCH_DISPATCHED, "Dispatch", dispatchId.toString(), dispatched.siteCode(),
                actor, Map.of("dispatchId", dispatchId, "manifestNumber", dispatched.manifestNumber(), "itemCount",
                        dispatched.itemCount(), "tripId", dispatched.tripId() == null ? "" : dispatched.tripId()));
        return dispatched;
    }

    @Transactional
    public Dispatch inTransit(UUID dispatchId, ActorContext actor, SourceChannel channel) {
        var before = dispatch(dispatchId, actor);
        access.require(actor, SflPermission.DISPATCH_MANIFEST_CREATE, before.siteCode().value(), "Dispatch",
                dispatchId.toString());
        var meta = before.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId());
        var after = repository.saveDispatch(before.inTransit(meta));
        audit.record(actor, channel, after.siteCode(), AuditAction.STATE_TRANSITION, "Dispatch", dispatchId.toString(),
                before, after);
        return after;
    }

    @Transactional
    public Dispatch close(UUID dispatchId, String reason, ActorContext actor, SourceChannel channel) {
        var before = dispatch(dispatchId, actor);
        access.require(actor, SflPermission.DISPATCH_MANIFEST_CREATE, before.siteCode().value(), "Dispatch",
                dispatchId.toString());
        boolean custodyClosable = CustodyChainPolicy.closable(repository.findHandovers(dispatchId),
                before.itemCount());
        DispatchClosurePolicy.requireClosable(repository.hasOpenException(dispatchId), custodyClosable);
        var meta = before.metadata().modifiedBy(actor.actorId(), clock.instant(), channel, actor.correlationId());
        var closed = repository.saveDispatch(before.close(reason, meta));
        audit.record(actor, channel, closed.siteCode(), AuditAction.CLOSE, "Dispatch", dispatchId.toString(), before,
                closed);
        return closed;
    }

    public Dispatch dispatch(UUID id, ActorContext actor) {
        var dispatch = repository.findDispatch(id).orElseThrow(() -> RecordNotFoundException.of("Dispatch", id));
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, dispatch.siteCode().value(), "Dispatch",
                id.toString());
        return dispatch;
    }

    public List<DispatchManifestItem> manifestItems(UUID dispatchId, ActorContext actor) {
        dispatch(dispatchId, actor);
        return repository.findManifestItems(dispatchId);
    }

    public List<Dispatch> dispatches(String site, Dispatch.Status status, String destinationCentre, UUID tripId,
            Instant from, Instant to, int limit, ActorContext actor) {
        access.require(actor, SflPermission.DISPATCH_MANIFEST_READ, site, "Dispatch", null);
        return repository.findDispatches(List.of(SiteCode.of(site).value()), status, destinationCentre, tripId, from,
                to, limit);
    }
}
