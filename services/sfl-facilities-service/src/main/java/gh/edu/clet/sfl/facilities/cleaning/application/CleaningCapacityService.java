package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.cleaning.application.contract.EventCleaningCapacity;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.CapacityReservation;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.ReservationStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.policy.CleaningCapacityPolicy;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S169's cleaning capacity, provided to S173 event logistics - SRS-SFL-S169-04.
 *
 * <p>The provider side of {@link EventCleaningCapacity}. S173 asks for a slot; this answers at request
 * time, inside S173's transaction, with either a reservation (and the cleaning task it raised, linked to
 * S173's reference) or a conflict naming the competing commitment - task number, what, where, when and
 * who - never a bare "unavailable". "A resourcing conflict ... is surfaced to S173 at request time, not
 * discovered at execution" is why the check runs here and not in a later sweep.
 *
 * <h2>Correct under concurrency</h2>
 *
 * The site's capacity lock ({@link CleaningRepository#lockCapacity}) is taken before commitments are
 * read, so two S173 requests for the last crew queue; the second reads the first's task and is given
 * the conflict naming it.
 *
 * <h2>What routine and booking cleans are not refused for</h2>
 *
 * Only this path is refused for lack of capacity. A routine occurrence or a booking's teardown clean is
 * an obligation the estate already has, and refusing it would hide work rather than prevent it; those
 * overlaps show in the capacity view instead. The gap report records the choice.
 *
 * <h2>The actor</h2>
 *
 * S173 authorises its own caller before it calls in, and the contract carries the requester by value.
 * Audit records are written as the {@code system.event-logistics} service account, with the requester
 * in the record, so an audit reader can tell a capacity decision from a person acting in S169.
 */
@Service
public class CleaningCapacityService implements EventCleaningCapacity {

    static final SiteScopedPrincipal SERVICE = new SiteScopedPrincipal("system.event-logistics",
            "S173 event logistics (in-process)", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    /** The capacity view for one site and window. */
    public record CapacityView(String siteCode, Instant from, Instant to, int crews, int peakConcurrent,
            int crewsFreeAtPeak, List<Commitment> commitments, List<CapacityReservation> reservations) {
    }

    /** One live commitment, described the way a conflict would describe it. */
    public record Commitment(UUID taskId, String taskNumber, UUID roomId, String roomCode, TaskOrigin origin,
            Instant from, Instant to, String assignedTo, UUID vendorId, String description) {
    }

    private final CleaningSupport support;
    private final CleaningRepository repository;
    private final CleaningTaskService tasks;

    public CleaningCapacityService(CleaningSupport support, CleaningTaskService tasks) {
        this.support = support;
        this.repository = support.repository();
        this.tasks = tasks;
    }

    @Override
    @Transactional
    public Reservation reserve(ReservationRequest request) {
        if (request == null || request.siteCode() == null || request.siteCode().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A site is required to reserve cleaning.");
        }
        if (request.from() == null || request.to() == null || !request.to().isAfter(request.from())) {
            throw new FacilitiesException.ValidationFailedException("A cleaning slot must end after it starts.");
        }
        if (request.eventReference() == null || request.eventReference().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("The S173 event reference is required.");
        }
        if (request.requestedBy() == null || request.requestedBy().isBlank()) {
            throw new FacilitiesException.ValidationFailedException("The requesting coordinator is required.");
        }
        String site = EstateCodes.normalize(request.siteCode());
        FacilityRoom room = resolveRoom(site, request.roomId(), request.locationCode());
        ActorContext actor = actorFor(request.requestedBy());

        // S173 retrying the same request - a timeout on its side - gets the reservation it already has.
        Optional<CapacityReservation> existing = repository.findLiveReservation(site, request.eventReference().strip());
        if (existing.isPresent()) {
            return toContract(existing.get());
        }

        repository.lockCapacity(site);
        List<CleaningTask> live = repository.findLiveTasksOverlapping(site, request.from(), request.to());
        List<CleaningCapacityPolicy.Commitment> commitments = live.stream()
                .map(task -> new CleaningCapacityPolicy.Commitment(task.id(), task.roomId(), task.windowStart(),
                        task.dueBy(), support.describe(task)))
                .toList();
        CleaningCapacityPolicy.Result result = CleaningCapacityPolicy.evaluate(room.id(), request.from(),
                request.to(), support.configuration().crews(site), commitments);
        Instant at = support.now();

        if (!result.available()) {
            CleaningCapacityPolicy.Commitment competing = result.competing();
            String named = result.reason() + " Competing commitment: " + competing.description();
            CapacityReservation conflict = repository.saveReservation(CapacityReservation.conflict(UUID.randomUUID(),
                    site, room.id(), request.locationCode(), request.from(), request.to(), request.scope(),
                    request.eventReference(), request.requestedBy(), competing.taskId(), truncate(named, 600),
                    competing.from(), competing.to(), actor.actorId(), at, SourceChannel.INTEGRATION,
                    actor.correlationId()));
            support.audit().record(actor, SourceChannel.INTEGRATION, AuditAction.CLEANING_CAPACITY_CONFLICT_RETURNED,
                    "CleaningCapacityReservation", conflict.id().toString(), site, null, conflict);
            Map<String, Object> payload = reservationPayload(conflict);
            payload.put("competingTaskId", competing.taskId().toString());
            payload.put("competingFrom", competing.from().toString());
            payload.put("competingTo", competing.to().toString());
            support.publish("sfl.ifimp.cleaning-capacity-conflict.v1", "CleaningCapacityReservation", conflict.id(),
                    site, payload, actor);
            return toContract(conflict);
        }

        UUID reservationId = UUID.randomUUID();
        String title = "Event clean of " + room.roomCode() + " for " + request.eventReference().strip();
        CleaningTask task = support.raise(room, TaskOrigin.EVENT, title, request.scope(), null, null, null, null,
                reservationId, request.eventReference().strip(), request.from(), request.to(), request.requestedBy(),
                actor, SourceChannel.INTEGRATION);
        CapacityReservation reserved = repository.saveReservation(CapacityReservation.reserved(reservationId, site,
                room.id(), request.locationCode(), request.from(), request.to(), request.scope(),
                request.eventReference(), request.requestedBy(), task.id(), actor.actorId(), at,
                SourceChannel.INTEGRATION, actor.correlationId()));
        support.audit().record(actor, SourceChannel.INTEGRATION, AuditAction.CLEANING_CAPACITY_RESERVED,
                "CleaningCapacityReservation", reserved.id().toString(), site, null, reserved);
        Map<String, Object> payload = reservationPayload(reserved);
        payload.put("taskId", task.id().toString());
        payload.put("taskNumber", task.taskNumber());
        support.publish("sfl.ifimp.cleaning-capacity-reserved.v1", "CleaningCapacityReservation", reserved.id(), site,
                payload, actor);
        return toContract(reserved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> find(UUID reservationId) {
        return reservationId == null ? Optional.empty() : repository.findReservation(reservationId).map(this::toContract);
    }

    /** Idempotent: an unknown, conflicting or already released reservation is left as it is. */
    @Override
    @Transactional
    public void release(UUID reservationId, String reason) {
        if (reservationId == null) {
            return;
        }
        Optional<CapacityReservation> found = repository.findReservation(reservationId);
        if (found.isEmpty() || found.get().status() != ReservationStatus.RESERVED) {
            return;
        }
        CapacityReservation reservation = found.get();
        ActorContext actor = actorFor(reservation.requestedBy());
        String recorded = reason == null || reason.isBlank() ? "Released by S173 event logistics." : reason.strip();
        repository.findTask(reservation.taskId()).filter(CleaningTask::isLive)
                .ifPresent(task -> tasks.cancelTask(task, "Capacity reservation released by S173: " + recorded, actor,
                        SourceChannel.INTEGRATION));
        CapacityReservation released = repository.saveReservation(reservation.release(recorded, actor.actorId(),
                support.now(), SourceChannel.INTEGRATION, actor.correlationId()));
        support.audit().record(actor, SourceChannel.INTEGRATION, AuditAction.CLEANING_CAPACITY_RELEASED,
                "CleaningCapacityReservation", released.id().toString(), released.siteCode(), reservation, released);
    }

    /**
     * The schedule and capacity at a site over a window - SRS-SFL-S169-04 "S169 exposes its schedule and
     * available capacity to S173".
     */
    @Transactional(readOnly = true)
    public CapacityView view(String siteCode, Instant from, Instant to, ActorContext actor, SourceChannel channel) {
        String site = EstateCodes.normalize(siteCode);
        support.requireUnnarrowedRead(actor, site, channel, "CleaningCapacity");
        support.authorization().requireSite(actor, site, channel, "CleaningCapacity", "view");
        Instant start = from == null ? support.now() : from;
        Instant end = to == null ? start.plus(Duration.ofDays(1)) : to;
        if (!end.isAfter(start)) {
            throw new FacilitiesException.ValidationFailedException("The capacity window must end after it starts.");
        }
        List<CleaningTask> live = repository.findLiveTasksOverlapping(site, start, end);
        List<Commitment> commitments = live.stream()
                .map(task -> new Commitment(task.id(), task.taskNumber(), task.roomId(), task.roomCode(), task.origin(),
                        task.windowStart(), task.dueBy(), task.assignedTo(), task.vendorId(), support.describe(task)))
                .toList();
        int crews = support.configuration().crews(site);
        CleaningCapacityPolicy.Result whole = CleaningCapacityPolicy.evaluate(null, start, end, Integer.MAX_VALUE,
                live.stream().map(task -> new CleaningCapacityPolicy.Commitment(task.id(), task.roomId(),
                        task.windowStart(), task.dueBy(), task.taskNumber())).toList());
        return new CapacityView(site, start, end, crews, whole.peakConcurrent(),
                Math.max(0, crews - whole.peakConcurrent()), commitments, repository.findReservations(site, start, end));
    }

    // ---- internals ----------------------------------------------------------------------------

    private FacilityRoom resolveRoom(String site, UUID roomId, String locationCode) {
        Optional<FacilityRoom> room = roomId != null ? support.facilities().findRoom(roomId)
                : locationCode == null || locationCode.isBlank() ? Optional.empty()
                        : support.facilities().findRoomByCode(site, locationCode.strip());
        FacilityRoom found = room.orElseThrow(() -> new FacilitiesException.ValidationFailedException(
                "The event location does not resolve to a room in the S152 estate at " + site + "."));
        if (!found.siteCode().equals(site)) {
            throw new FacilitiesException.ValidationFailedException(
                    found.roomCode() + " is at " + found.siteCode() + ", not " + site + ".");
        }
        return found;
    }

    /**
     * Maps what is stored onto the contract. FULFILLED and a release by task cancellation are derived
     * from the task, so the answer cannot disagree with the task it is about.
     */
    private Reservation toContract(CapacityReservation reservation) {
        Status status = switch (reservation.status()) {
            case CONFLICT -> Status.CONFLICT;
            case RELEASED -> Status.RELEASED;
            case RESERVED -> repository.findTask(reservation.taskId())
                    .map(task -> task.status() == TaskStatus.COMPLETED ? Status.FULFILLED
                            : task.status() == TaskStatus.CANCELLED ? Status.RELEASED : Status.RESERVED)
                    .orElse(Status.RESERVED);
        };
        return new Reservation(reservation.id(), status, reservation.taskId(), reservation.competingTaskId(),
                reservation.competingCommitment(), reservation.competingFrom(), reservation.competingTo());
    }

    private static Map<String, Object> reservationPayload(CapacityReservation reservation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reservationId", reservation.id().toString());
        payload.put("siteCode", reservation.siteCode());
        CleaningSupport.putIfPresent(payload, "roomId", reservation.roomId());
        payload.put("windowFrom", reservation.windowFrom().toString());
        payload.put("windowTo", reservation.windowTo().toString());
        payload.put("eventReference", reservation.eventReference());
        payload.put("status", reservation.status().name());
        return payload;
    }

    /** The requester is kept on the reservation row; the audit actor is the service account. */
    private static ActorContext actorFor(String requestedBy) {
        return new ActorContext(SERVICE, "s173-" + UUID.randomUUID());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
