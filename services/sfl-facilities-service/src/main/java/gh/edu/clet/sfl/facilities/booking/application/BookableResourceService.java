package gh.edu.clet.sfl.facilities.booking.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.booking.application.ports.BookingRepository;
import gh.edu.clet.sfl.facilities.booking.domain.BookableResource;
import gh.edu.clet.sfl.facilities.booking.domain.Booking;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceAllocation;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bookable-resource register, and attaching resources to bookings — SRS-SFL-S159-01, -02.
 *
 * <h2>Why reducing a quantity does not check what is already allocated</h2>
 *
 * {@link #update} lets a manager say there are now thirty chairs where there were forty, even if
 * bookings next week are holding thirty-five. Refusing would be the tidier rule and the wrong one:
 * the chairs are genuinely gone, and a register that insists otherwise is a register that has stopped
 * describing the estate. The oversubscription surfaces on the availability screen, where a human can
 * decide which booking loses out — which is a decision, not an arithmetic error.
 */
@Service
public class BookableResourceService {

    private final BookingRepository bookings;
    private final BookingApplicationService booking;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final Clock clock;

    public BookableResourceService(BookingRepository bookings, BookingApplicationService booking,
            FacilitiesAuthorization authorization, AuditPort audit, IdempotencyPort idempotency,
            Clock clock) {
        this.bookings = bookings;
        this.booking = booking;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional
    public BookableResource register(BookingCommands.RegisterResource command) {
        ActorContext actor = command.actor();
        authorization.require(actor, SflPermission.FACILITIES_RESOURCE_MANAGE, command.siteCode(),
                command.channel(), "BookableResource", "new");

        if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
            Optional<BookableResource> replayed = idempotency
                    .findExistingResult("register-resource", command.idempotencyKey(),
                            idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(bookings::findResource);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        bookings.findResourceByCode(command.siteCode(), command.resourceCode())
                .filter(existing -> existing.lifecycleStatus().occupiesIdentifier())
                .ifPresent(existing -> {
                    throw new FacilitiesException.DuplicateIdentifierException("bookable resource",
                            command.resourceCode(), command.siteCode());
                });

        Instant at = clock.instant();
        BookableResource saved = bookings.saveResource(BookableResource.register(UUID.randomUUID(),
                command.siteCode(), command.resourceCode(), command.name(), command.category(),
                command.description(), command.quantity(), command.homeRoomId(), command.assetId(),
                command.requiresSetup(), actor.actorId(), at, command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.BOOKABLE_RESOURCE_REGISTERED, "BookableResource",
                saved.id().toString(), saved.siteCode(), null, saved);
        idempotency.recordResult("register-resource", command.idempotencyKey(),
                idempotency.fingerprint(command.idempotencyPayload()), saved.id(), saved.siteCode(),
                actor.actorId());
        return saved;
    }

    @Transactional
    public BookableResource update(BookingCommands.UpdateResource command) {
        ActorContext actor = command.actor();
        BookableResource resource = requireResource(command.resourceId());
        authorization.require(actor, SflPermission.FACILITIES_RESOURCE_MANAGE, resource.siteCode(),
                command.channel(), "BookableResource", resource.id().toString());
        resource.metadata().requireVersion(command.expectedVersion(), "Bookable resource", resource.id());

        BookableResource updated = bookings.saveResource(resource.update(command.name(),
                command.description(), command.quantity(), command.homeRoomId(), command.requiresSetup(),
                actor.actorId(), clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.BOOKABLE_RESOURCE_UPDATED, "BookableResource",
                updated.id().toString(), updated.siteCode(), resource, updated);
        return updated;
    }

    @Transactional
    public BookableResource changeLifecycle(BookingCommands.ChangeResourceLifecycle command) {
        ActorContext actor = command.actor();
        BookableResource resource = requireResource(command.resourceId());
        authorization.require(actor, SflPermission.FACILITIES_RESOURCE_MANAGE, resource.siteCode(),
                command.channel(), "BookableResource", resource.id().toString());
        resource.metadata().requireVersion(command.expectedVersion(), "Bookable resource", resource.id());

        BookableResource changed = bookings.saveResource(resource.changeLifecycle(command.target(),
                actor.actorId(), clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.BOOKABLE_RESOURCE_LIFECYCLE_CHANGED,
                "BookableResource", changed.id().toString(), changed.siteCode(), resource, changed);
        return changed;
    }

    /** Adds resources to a booking that already exists, re-running the availability arithmetic. */
    @Transactional
    public List<ResourceAllocation> allocate(BookingCommands.AllocateResources command) {
        ActorContext actor = command.actor();
        Booking target = booking.requireBooking(command.bookingId());
        booking.requireMayAct(actor, target, command.channel());
        if (!target.holdsTheSpace()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Resources cannot be added to a " + target.status() + " booking.");
        }

        Map<UUID, Integer> requested = new LinkedHashMap<>();
        command.resources().forEach((id, quantity) -> requested.put(id, quantity == null ? 1 : quantity));
        List<BookableResource> resources = booking.requireResources(target.siteCode(), requested.keySet());
        booking.assertResourcesAreFree(target.window(), requested, resources, target.id());

        booking.allocate(target, requested, resources, actor, clock.instant(), command.channel());
        booking.raiseSetupTasks(target, resources, actor, clock.instant(), command.channel());
        return bookings.findAllocationsForBooking(target.id());
    }

    @Transactional
    public void release(BookingCommands.ReleaseAllocation command) {
        ActorContext actor = command.actor();
        Booking target = booking.requireBooking(command.bookingId());
        booking.requireMayAct(actor, target, command.channel());

        ResourceAllocation allocation = bookings.findAllocation(command.allocationId())
                .filter(candidate -> candidate.bookingId().equals(target.id()))
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Resource allocation",
                        command.allocationId()));
        if (!allocation.isLive()) {
            return;
        }
        ResourceAllocation released = bookings.saveAllocation(allocation.release());
        audit.record(actor, command.channel(), AuditAction.BOOKING_RESOURCE_RELEASED, "ResourceAllocation",
                released.id().toString(), target.siteCode(), allocation, released);
    }

    // ---- queries ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BookableResource> search(String siteCode, ResourceCategory category, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_RESOURCE_READ, channel, "BookableResource",
                "list", siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "BookableResource");
        return authorization.filterBySite(actor, bookings.findResources(siteCode, category),
                BookableResource::siteCode);
    }

    @Transactional(readOnly = true)
    public BookableResource findById(UUID id, ActorContext actor, SourceChannel channel) {
        BookableResource resource = requireResource(id);
        authorization.require(actor, SflPermission.FACILITIES_RESOURCE_READ, resource.siteCode(), channel,
                "BookableResource", id.toString());
        return resource;
    }

    private BookableResource requireResource(UUID id) {
        return bookings.findResource(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Bookable resource", id));
    }
}
