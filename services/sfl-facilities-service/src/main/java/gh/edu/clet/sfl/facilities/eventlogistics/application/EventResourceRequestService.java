package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.BookingGatewayPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CateringGatewayPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CleaningCapacityPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.EventLogisticsRepository;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.GatewayOutcome;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.MaintenanceGatewayPort;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventTemplateLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.OwningSystem;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resource requests: decomposition, routing to the owning systems, and tracking their answers -
 * SRS-SFL-S173-01 (decomposition) and S173-02 (routing and roll-up).
 *
 * <h2>Routing, per type</h2>
 * <ul>
 *   <li><strong>VENUE</strong> - an S159 booking of the event's room for its window. Confirmed, awaiting
 *       S159 approval (REQUESTED), or a conflict naming the booking that holds the hall.</li>
 *   <li><strong>AV / STAGING / SECURITY / SIGNAGE</strong> - an S159 bookable resource allocated onto the
 *       venue booking. Until the venue is booked there is nothing to allocate onto, and the request stays
 *       REQUESTED saying so; booking the venue routes the waiting lines.</li>
 *   <li><strong>PRE_EVENT_MAINTENANCE</strong> - an S153 work order through the automated intake,
 *       category {@code EVENT_PRE_MAINTENANCE}.</li>
 *   <li><strong>CLEANING</strong> - an S169 capacity reservation. A conflict carries S169's named
 *       competing commitment and the readiness view shows it verbatim.</li>
 *   <li><strong>CATERING</strong> - S172, which is Phase 3. Recorded as MANUAL_COORDINATION, marked as
 *       such, never dropped and never shown fulfilled (S173-02 acceptance criterion).</li>
 * </ul>
 *
 * <p>Any owning system switched off in the availability registry
 * ({@code event-logistics.owning-system.<S>.available}) is routed the same way catering is: a manual
 * coordination item with the reason on it.
 *
 * <h2>Refusals are values, not exceptions</h2>
 *
 * <p>An owning system saying "no" is the normal course of events, and it is recorded as CONFLICTED with
 * its cause. The S159 adapter asks S159's availability read model first rather than catching S159's
 * conflict exception, because an exception thrown out of S159's transactional service marks the
 * shared transaction rollback-only and would take the whole decomposition with it. Only a genuine
 * race - somebody took the hall between the availability read and the booking - surfaces as S159's own
 * {@code BOOKING_CONFLICT}, and the coordinator simply routes again.
 */
@Service
public class EventResourceRequestService {

    private final EventLogisticsRepository repository;
    private final BookingGatewayPort booking;
    private final MaintenanceGatewayPort maintenance;
    private final CleaningCapacityPort cleaning;
    private final CateringGatewayPort catering;
    private final EventLogisticsConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public EventResourceRequestService(EventLogisticsRepository repository, BookingGatewayPort booking,
            MaintenanceGatewayPort maintenance, CleaningCapacityPort cleaning, CateringGatewayPort catering,
            EventLogisticsConfiguration configuration, FacilitiesAuthorization authorization, AuditPort audit,
            ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.booking = booking;
        this.maintenance = maintenance;
        this.cleaning = cleaning;
        this.catering = catering;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    // =============================================================================================
    // Commands
    // =============================================================================================

    /** S173-01: decompose into typed requests, then route every new one to its owning system (S173-02). */
    @Transactional
    public List<EventResourceRequest> decompose(EventLogisticsCommands.DecomposeSetupTask command) {
        ActorContext actor = command.actor();
        EventSetupTask task = requireTask(command.setupTaskId());
        authorization.require(actor, SflPermission.FACILITIES_EVENT_COORDINATE, task.siteCode(), command.channel(),
                "EventSetupTask", task.id().toString());
        task.requireLive("decompose");
        Instant at = clock.instant();

        List<EventResourceRequest> existing = repository.findRequestsForTask(task.id());
        Set<EventResourceType> listed = EnumSet.noneOf(EventResourceType.class);
        existing.stream().filter(request -> request.status().isLive())
                .forEach(request -> listed.add(request.resourceType()));
        command.requests().forEach(request -> {
            if (request.resourceType() == null) {
                throw new FacilitiesException.ValidationFailedException("Every resource request needs a type.");
            }
            listed.add(request.resourceType());
        });

        List<EventResourceRequest> raised = new ArrayList<>();
        for (EventLogisticsCommands.NewResourceRequest line : command.requests()) {
            raised.add(EventResourceRequest.raise(UUID.randomUUID(), task, line.resourceType(), line.description(),
                    line.quantity() == null ? 1 : line.quantity(), line.bookableResourceId(), line.neededFrom(),
                    line.neededTo(), null, actor.actorId(), at, command.channel(), actor.correlationId()));
        }
        if (command.applyTemplate()) {
            int threshold = configuration.templateGapThreshold(task.siteCode());
            for (EventTemplateLine template : repository.findTemplateLines(task.siteCode(),
                    task.details().eventCategory())) {
                if (!template.isPersistent(threshold) || listed.contains(template.resourceType())) {
                    continue;
                }
                listed.add(template.resourceType());
                raised.add(EventResourceRequest.raise(UUID.randomUUID(), task, template.resourceType(),
                        template.description() + " - lesson: " + template.lesson(), template.quantity(),
                        template.bookableResourceId(), null, null, template.id(), actor.actorId(), at,
                        command.channel(), actor.correlationId()));
            }
        }
        long venues = existing.stream().filter(request -> request.status().isLive())
                .filter(request -> request.resourceType() == EventResourceType.VENUE).count()
                + raised.stream().filter(request -> request.resourceType() == EventResourceType.VENUE).count();
        if (venues > 1) {
            throw new FacilitiesException.ValidationFailedException(
                    "A set-up task has one VENUE request: it is the booking every other S159 resource sits on.");
        }
        if (raised.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException("Nothing to request.");
        }

        EventSetupTask owned = task.coordinatorId() == null
                ? repository.saveTask(task.withCoordinator(actor.actorId(), at, command.channel(),
                        actor.correlationId()))
                : task;
        List<EventResourceRequest> saved = new ArrayList<>();
        for (EventResourceRequest request : raised) {
            EventResourceRequest stored = repository.saveRequest(request);
            audit.record(actor, command.channel(), AuditAction.EVENT_RESOURCE_REQUEST_RAISED, "EventResourceRequest",
                    stored.id().toString(), stored.siteCode(), null, stored);
            saved.add(stored);
        }
        // Venue first: every other S159 line is allocated onto it.
        saved.sort(Comparator.comparing(request -> request.resourceType() == EventResourceType.VENUE ? 0 : 1));
        for (EventResourceRequest request : saved) {
            route(owned, repository.findRequest(request.id()).orElseThrow(), actor, command.channel());
        }
        return repository.findRequestsForTask(task.id());
    }

    /** Re-send one request - after a conflict was resolved elsewhere, or once the venue is booked. */
    @Transactional
    public EventResourceRequest reroute(EventLogisticsCommands.RouteResourceRequest command) {
        EventResourceRequest request = requireRequest(command.resourceRequestId());
        EventSetupTask task = requireTask(request.setupTaskId());
        authorization.require(command.actor(), SflPermission.FACILITIES_EVENT_COORDINATE, task.siteCode(),
                command.channel(), "EventResourceRequest", request.id().toString());
        task.requireLive("route a request for");
        if (request.status() != ResourceRequestStatus.REQUESTED && request.status() != ResourceRequestStatus.CONFLICTED
                && request.status() != ResourceRequestStatus.MANUAL_COORDINATION) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only a requested, conflicted or manual request can be routed again; this one is "
                            + request.status() + ".");
        }
        if (request.holdsExternalCommitment()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "This request is already held by " + request.owningSystem() + " (" + request.externalReference()
                            + "); it is awaiting that system's answer, not a new request.");
        }
        return route(task, request, command.actor(), command.channel());
    }

    /** S173-04: a named person accepts responsibility for a manual coordination item. */
    @Transactional
    public EventResourceRequest acceptManualCoordination(EventLogisticsCommands.AcceptManualCoordination command) {
        EventResourceRequest request = requireRequest(command.resourceRequestId());
        EventSetupTask task = requireTask(request.setupTaskId());
        authorization.require(command.actor(), SflPermission.FACILITIES_EVENT_COORDINATE, task.siteCode(),
                command.channel(), "EventResourceRequest", request.id().toString());
        task.requireLive("accept a manual item for");
        EventResourceRequest accepted = repository.saveRequest(request.acceptManualCoordination(
                command.actor().actorId(), command.arrangedWith(), clock.instant(), command.channel(),
                command.actor().correlationId()));
        audit.record(command.actor(), command.channel(), AuditAction.EVENT_MANUAL_COORDINATION_ACCEPTED,
                "EventResourceRequest", accepted.id().toString(), accepted.siteCode(), request, accepted);
        Map<String, Object> facts = requestFacts(accepted);
        facts.put("acceptedBy", accepted.manualAcceptedBy());
        outbox.record("sfl.ifimp.event-manual-coordination-recorded.v1", 1, "EventResourceRequest", accepted.id(),
                accepted.siteCode(), command.actor().correlationId(), command.actor().actorId(), facts);
        return accepted;
    }

    @Transactional
    public EventResourceRequest cancel(EventLogisticsCommands.CancelResourceRequest command) {
        EventResourceRequest request = requireRequest(command.resourceRequestId());
        EventSetupTask task = requireTask(request.setupTaskId());
        authorization.require(command.actor(), SflPermission.FACILITIES_EVENT_COORDINATE, task.siteCode(),
                command.channel(), "EventResourceRequest", request.id().toString());
        if (command.reason() == null || command.reason().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("Say why the request is no longer needed.");
        }
        return cancelOne(request, command.reason().strip(), command.actor(), command.channel());
    }

    // =============================================================================================
    // Reactions - hand-off changes, owning-system changes, the synchronisation sweep
    // =============================================================================================

    /** S078 cancelled the event. Every live request goes, and so does what it holds elsewhere. */
    void cancelAllForTask(EventSetupTask task, String reason, ActorContext actor, SourceChannel channel) {
        for (EventResourceRequest request : venueLast(repository.findRequestsForTask(task.id()))) {
            if (!request.status().isTerminal()) {
                cancelOne(request, reason, actor, channel);
            }
        }
    }

    /**
     * S078 moved the event. What was held for the old window or room is released and the request goes
     * back to REQUESTED, saying why, for the coordinator to route again. Maintenance is left alone:
     * the air-conditioning still needs fixing before the event whichever day it is.
     */
    void requeueAfterMove(EventSetupTask task, ActorContext actor, SourceChannel channel) {
        Instant at = clock.instant();
        String reason = "The event moved in S078 on " + at + "; route again against the new details.";
        for (EventResourceRequest request : venueLast(repository.findRequestsForTask(task.id()))) {
            if (!request.status().isLive() || request.status() == ResourceRequestStatus.FULFILLED
                    || request.resourceType() == EventResourceType.PRE_EVENT_MAINTENANCE
                    || request.status() == ResourceRequestStatus.MANUAL_COORDINATION) {
                continue;
            }
            EventResourceRequest requeued = repository.saveRequest(request.requeue(reason, actor.actorId(), at,
                    channel, actor.correlationId()));
            releaseExternal(request, reason, actor);
            recordStatusChange(request, requeued, actor, channel);
        }
    }

    /**
     * S159 says a booking changed - the observer's entry point. Every live request on that booking takes
     * the booking's new answer, which the observer passes in: an S159 observer must not call back into
     * S159 from inside its transaction.
     */
    @Transactional
    public void bookingChanged(String bookingId, GatewayOutcome state, ActorContext actor) {
        for (EventResourceRequest request : repository.findRequestsForBooking(bookingId)) {
            if (!request.status().isTerminal()) {
                apply(request, state, actor, SourceChannel.SYSTEM);
            }
        }
    }

    /**
     * Pulls every routed request's current state from its owning system - the safety net under the S159
     * observer, and the only way S153 completion and S169 fulfilment reach S173.
     *
     * @return how many requests changed
     */
    @Transactional
    public int synchronise(ActorContext actor) {
        int changed = 0;
        for (EventResourceRequest request : repository.findRoutedLiveRequests(configuration.sweepBatchSize())) {
            Optional<GatewayOutcome> state = switch (request.resourceType()) {
                case VENUE -> booking.bookingState(request.externalReference());
                case AV, STAGING, SECURITY, SIGNAGE -> request.externalParentReference() == null ? Optional.empty()
                        : booking.bookingState(request.externalParentReference());
                case PRE_EVENT_MAINTENANCE -> maintenance.state(request.externalReference());
                case CLEANING -> cleaning.state(request.externalReference());
                case CATERING -> Optional.empty();
            };
            if (state.isPresent() && apply(request, state.get(), actor, SourceChannel.SCHEDULER)) {
                changed++;
            }
        }
        return changed;
    }

    // =============================================================================================
    // Routing
    // =============================================================================================

    private EventResourceRequest route(EventSetupTask task, EventResourceRequest request, ActorContext actor,
            SourceChannel channel) {
        GatewayOutcome outcome = outcomeOf(task, request, actor);
        EventResourceRequest routed = repository.saveRequest(request.routed(outcome.status(), outcome.reference(),
                outcome.parentReference(), outcome.detail(), outcome.competingCommitment(), actor.actorId(),
                clock.instant(), channel, actor.correlationId()));
        recordStatusChange(request, routed, actor, channel);
        if (routed.status() == ResourceRequestStatus.MANUAL_COORDINATION) {
            outbox.record("sfl.ifimp.event-manual-coordination-recorded.v1", 1, "EventResourceRequest", routed.id(),
                    routed.siteCode(), actor.correlationId(), actor.actorId(), requestFacts(routed));
        }
        if (routed.resourceType() == EventResourceType.VENUE && routed.externalReference() != null) {
            routeWaitingResources(task, actor, channel);
        }
        return routed;
    }

    /** Booking the venue releases every AV/staging/security/signage line that was waiting for it. */
    private void routeWaitingResources(EventSetupTask task, ActorContext actor, SourceChannel channel) {
        for (EventResourceRequest waiting : repository.findRequestsForTask(task.id())) {
            if (waiting.resourceType().isBookableResource() && waiting.status() == ResourceRequestStatus.REQUESTED
                    && waiting.externalReference() == null && waiting.routeAttempts() > 0) {
                route(task, waiting, actor, channel);
            }
        }
    }

    private GatewayOutcome outcomeOf(EventSetupTask task, EventResourceRequest request, ActorContext actor) {
        OwningSystem owner = request.owningSystem();
        if (!configuration.owningSystemAvailable(owner, task.siteCode())) {
            return GatewayOutcome.unavailable(unavailableReason(owner));
        }
        int attempt = request.routeAttempts() + 1;
        return switch (request.resourceType()) {
            case VENUE -> booking.requestVenue(new BookingGatewayPort.VenueRequest(task.siteCode(),
                    task.details().roomId(), task.details().title() + " (" + task.taskReference() + ")",
                    request.neededFrom(), request.neededTo(), task.details().expectedAttendance(), actor.actorId(),
                    request.id().toString(), "event-logistics-venue:" + request.id() + ":" + attempt,
                    actor.correlationId()));
            case AV, STAGING, SECURITY, SIGNAGE -> allocateOntoVenue(task, request, actor);
            case PRE_EVENT_MAINTENANCE -> maintenance.raise(new MaintenanceGatewayPort.PreEventMaintenance(
                    task.siteCode(), task.details().roomId(), task.details().roomCode(),
                    "Pre-event maintenance for " + task.taskReference() + ": " + request.description(),
                    "Needed before " + task.details().title() + " starting " + task.details().startsAt() + ".",
                    request.id().toString(), actor.actorId(), actor.correlationId(),
                    "event-logistics-maintenance:" + request.id() + ":" + attempt));
            case CLEANING -> cleaning.reserve(new CleaningCapacityPort.CleaningSlot(task.siteCode(),
                    task.details().roomId(), task.details().roomCode(), request.neededFrom(), request.neededTo(),
                    request.description(), request.id().toString(), actor.actorId()));
            case CATERING -> catering.request(new CateringGatewayPort.CateringRequest(task.siteCode(),
                    task.details().roomCode(), request.neededFrom(), request.neededTo(), request.quantity(),
                    request.description(), request.id().toString(), actor.actorId()))
                    .orElseGet(() -> GatewayOutcome.unavailable("S172 is marked available but no catering adapter"
                            + " is installed in this deployment. Coordinate catering off-system and record who"
                            + " accepted it."));
        };
    }

    private GatewayOutcome allocateOntoVenue(EventSetupTask task, EventResourceRequest request, ActorContext actor) {
        Optional<EventResourceRequest> venue = repository.findRequestsForTask(task.id()).stream()
                .filter(candidate -> candidate.resourceType() == EventResourceType.VENUE)
                .filter(EventResourceRequest::holdsExternalCommitment)
                .findFirst();
        if (venue.isEmpty()) {
            return GatewayOutcome.of(ResourceRequestStatus.REQUESTED, null, "Waiting for the venue: an S159 "
                    + request.resourceType() + " resource is allocated onto the event's venue booking, and the"
                    + " VENUE request is not booked yet. It is routed as soon as the venue is.");
        }
        GatewayOutcome allocated = booking.allocate(new BookingGatewayPort.AllocationRequest(
                venue.get().externalReference(), request.bookableResourceId(), request.quantity(),
                actor.correlationId()));
        if (allocated.status() == ResourceRequestStatus.CONFLICTED) {
            return allocated;
        }
        // An allocation is only as firm as the booking it sits on.
        return new GatewayOutcome(venue.get().status() == ResourceRequestStatus.CONFIRMED
                ? ResourceRequestStatus.CONFIRMED : ResourceRequestStatus.REQUESTED, allocated.reference(),
                venue.get().externalReference(), allocated.detail(), null);
    }

    static String unavailableReason(OwningSystem owner) {
        return owner == OwningSystem.S172
                ? "S172 Catering & Cafeteria Management is Phase 3 and not built. This is a manual coordination"
                        + " item: arrange it off-system and record who accepted it. It is not a system request and"
                        + " is never shown as fulfilled."
                : owner + " " + owner.displayName() + " is marked unavailable in this deployment's owning-system"
                        + " registry. Coordinate off-system and record who accepted it.";
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    /** @return whether the request changed */
    private boolean apply(EventResourceRequest request, GatewayOutcome state, ActorContext actor,
            SourceChannel channel) {
        ResourceRequestStatus next = state.status();
        if (next == request.status() || !request.status().canTransitionTo(next)
                || next == ResourceRequestStatus.MANUAL_COORDINATION) {
            return false;
        }
        EventResourceRequest changed = repository.saveRequest(request.withStatus(next, state.detail(),
                state.competingCommitment(), actor.actorId(), clock.instant(), channel, actor.correlationId()));
        recordStatusChange(request, changed, actor, channel);
        return true;
    }

    private EventResourceRequest cancelOne(EventResourceRequest request, String reason, ActorContext actor,
            SourceChannel channel) {
        EventResourceRequest cancelled = repository.saveRequest(request.cancel(reason, actor.actorId(),
                clock.instant(), channel, actor.correlationId()));
        releaseExternal(request, reason, actor);
        recordStatusChange(request, cancelled, actor, channel);
        return cancelled;
    }

    /** Lets the owning system have back what the request held. */
    private void releaseExternal(EventResourceRequest request, String reason, ActorContext actor) {
        if (!request.holdsExternalCommitment()) {
            return;
        }
        switch (request.resourceType()) {
            case VENUE -> booking.cancelBooking(request.externalReference(), reason, actor.correlationId());
            case AV, STAGING, SECURITY, SIGNAGE -> {
                if (request.externalParentReference() != null) {
                    booking.releaseAllocation(request.externalParentReference(), request.externalReference(),
                            actor.correlationId());
                }
            }
            case CLEANING -> cleaning.release(request.externalReference(), reason);
            case PRE_EVENT_MAINTENANCE, CATERING -> {
                // A raised work order is S153's to cancel, by a person who can see whether it is still
                // needed; S173 does not reach into another module's queue to withdraw work. Recorded on
                // the request's status detail and in the gap report.
            }
        }
    }

    private void recordStatusChange(EventResourceRequest before, EventResourceRequest after, ActorContext actor,
            SourceChannel channel) {
        audit.record(actor, channel, AuditAction.EVENT_RESOURCE_REQUEST_STATUS_CHANGED, "EventResourceRequest",
                after.id().toString(), after.siteCode(), before, after);
        Map<String, Object> facts = requestFacts(after);
        facts.put("previousStatus", before.status().name());
        outbox.record("sfl.ifimp.event-resource-request-status-changed.v1", 1, "EventResourceRequest", after.id(),
                after.siteCode(), actor.correlationId(), actor.actorId(), facts);
    }

    /**
     * Allocations before their venue: releasing an allocation first means cancelling the venue booking
     * afterwards finds nothing of S173's still hanging off it, so the S159 observer has nothing to do.
     */
    private static List<EventResourceRequest> venueLast(List<EventResourceRequest> requests) {
        List<EventResourceRequest> ordered = new ArrayList<>(requests);
        ordered.sort(Comparator.comparing(request -> request.resourceType() == EventResourceType.VENUE ? 1 : 0));
        return ordered;
    }

    static Map<String, Object> requestFacts(EventResourceRequest request) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("resourceRequestId", request.id().toString());
        facts.put("setupTaskId", request.setupTaskId().toString());
        facts.put("resourceType", request.resourceType().name());
        facts.put("owningSystem", request.owningSystem().name());
        facts.put("status", request.status().name());
        facts.put("externalReference", request.externalReference());
        facts.put("manualCoordination", request.status() == ResourceRequestStatus.MANUAL_COORDINATION);
        return facts;
    }

    private EventSetupTask requireTask(UUID id) {
        return repository.findTask(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Event set-up task", id));
    }

    private EventResourceRequest requireRequest(UUID id) {
        return repository.findRequest(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Event resource request", id));
    }
}
