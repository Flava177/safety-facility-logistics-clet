package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.CleaningRepository;
import gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType;
import gh.edu.clet.sfl.facilities.cleaning.domain.ChecklistTemplate;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskChecklistItem;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * What every S169 service shares: raising a task with its checklist, the per-record narrowing, and the
 * event payloads.
 *
 * <h2>Who sees which task</h2>
 *
 * Site scope is necessary and not sufficient, for the reason {@code WorkOrderApplicationService} gives
 * for S153. Three readings, applied to every read and every write:
 *
 * <ul>
 *   <li>An actor holding only {@link SflRole#VENDOR_TECHNICIAN} sees and acts on the tasks
 *       <strong>assigned to them</strong> and nothing else - a contractor cleaner has no business
 *       knowing which rooms are cleaned when.</li>
 *   <li>An actor without {@code FACILITIES_CLEANING_READ} who may raise requests or give feedback - the
 *       occupant, {@link SflRole#IFIMP_REQUESTER} - sees only the requests <strong>they raised</strong>.</li>
 *   <li>Everybody else holding {@code FACILITIES_CLEANING_READ} reads the site's whole rota, because
 *       that is what stops two supervisors planning around the same crew.</li>
 * </ul>
 */
@Component
public class CleaningSupport {

    /** The per-record narrowing for one actor. {@code actorId} is null when there is none. */
    public record Narrowing(Kind kind, String actorId) {

        public enum Kind {
            NONE,
            ASSIGNED_TO_ME,
            REQUESTED_BY_ME
        }

        boolean narrowed() {
            return kind != Kind.NONE;
        }
    }

    static final DateTimeFormatter HOURS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CleaningRepository repository;
    private final FacilitiesRepository facilities;
    private final CleaningConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final CleaningRefusalRecorder refusals;
    private final Clock clock;

    public CleaningSupport(CleaningRepository repository, FacilitiesRepository facilities,
            CleaningConfiguration configuration, FacilitiesAuthorization authorization, AuditPort audit,
            ServiceOutbox outbox, CleaningRefusalRecorder refusals, Clock clock) {
        this.repository = repository;
        this.facilities = facilities;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.refusals = refusals;
        this.clock = clock;
    }

    // ---- collaborators, for the services in this package --------------------------------------

    CleaningRepository repository() {
        return repository;
    }

    FacilitiesRepository facilities() {
        return facilities;
    }

    CleaningConfiguration configuration() {
        return configuration;
    }

    FacilitiesAuthorization authorization() {
        return authorization;
    }

    AuditPort audit() {
        return audit;
    }

    CleaningRefusalRecorder refusals() {
        return refusals;
    }

    Instant now() {
        return clock.instant();
    }

    // ---- raising tasks ------------------------------------------------------------------------

    /**
     * Raises a task and copies onto it the checklist for its space type - SRS-SFL-S169-02 "each cleaning
     * task carries a checklist appropriate to the space type".
     *
     * <p>A space type with no active template gives a task with no checklist. That is not refused: the
     * booking observer calls this inside the booking's transaction, and refusing would refuse the
     * booking because nobody wrote a checklist for its room type. The task records that it has none
     * ({@code checklistTemplateId} null), the dashboard counts such tasks, and the gap is in the report.
     */
    CleaningTask raise(FacilityRoom room, TaskOrigin origin, String title, String description, UUID scheduleId,
            Instant occurrenceStart, UUID bookingId, String bookingReference, UUID reservationId,
            String eventReference, Instant windowStart, Instant dueBy, String requestedBy, ActorContext actor,
            SourceChannel channel) {
        Instant at = now();
        Optional<ChecklistTemplate> template = repository.findActiveTemplate(room.siteCode(), room.spaceType());
        CleaningTask task = repository.saveTask(CleaningTask.raise(new CleaningTask.Raise(UUID.randomUUID(),
                repository.nextTaskNumber(room.siteCode()), room.siteCode(), room.id(), room.roomCode(),
                room.spaceType(), origin, title, description, scheduleId, occurrenceStart, bookingId,
                bookingReference, reservationId, eventReference, windowStart, dueBy, requestedBy,
                template.map(ChecklistTemplate::id).orElse(null),
                template.map(ChecklistTemplate::version).orElse(null)), actor.actorId(), at, channel,
                actor.correlationId()));
        template.ifPresent(found -> found.items().forEach(item -> repository.saveChecklistItem(
                TaskChecklistItem.fromTemplate(UUID.randomUUID(), task, item, actor.actorId(), at, channel,
                        actor.correlationId()))));
        audit.record(actor, channel, AuditAction.CLEANING_TASK_CREATED, "CleaningTask", task.id().toString(),
                task.siteCode(), null, task);
        publishTask("sfl.ifimp.cleaning-task-created.v1", task, actor);
        return task;
    }

    CleaningTask requireTask(UUID id) {
        return repository.findTask(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Cleaning task", id));
    }

    FacilityRoom requireRoom(UUID roomId) {
        if (roomId == null) {
            throw new FacilitiesException.ValidationFailedException("A room is required.");
        }
        return facilities.findRoom(roomId)
                .orElseThrow(() -> new FacilitiesException.InvalidParentReferenceException("Space", roomId));
    }

    // ---- narrowing ----------------------------------------------------------------------------

    /**
     * The reading of the rota this actor gets, refusing (audited) an actor with no route to it at all.
     *
     * @param siteCode the site the read is about, for the audit record of a refusal
     */
    Narrowing readNarrowing(ActorContext actor, String siteCode, SourceChannel channel, String resourceId) {
        if (authorization.has(actor, SflPermission.FACILITIES_CLEANING_READ)) {
            return onlyVendor(actor) ? new Narrowing(Narrowing.Kind.ASSIGNED_TO_ME, actor.actorId())
                    : new Narrowing(Narrowing.Kind.NONE, null);
        }
        if (authorization.has(actor, SflPermission.FACILITIES_CLEANING_REQUEST)
                || authorization.has(actor, SflPermission.FACILITIES_CLEANING_FEEDBACK_SUBMIT)) {
            return new Narrowing(Narrowing.Kind.REQUESTED_BY_ME, actor.actorId());
        }
        authorization.require(actor, SflPermission.FACILITIES_CLEANING_READ, channel, "CleaningTask", resourceId,
                siteCode);
        throw new IllegalStateException("unreachable: require refuses");
    }

    /**
     * For aggregate reads - dashboard, capacity, scorecards. They summarise every task at the site, so
     * an actor who may only see some tasks may see none of these.
     */
    void requireUnnarrowedRead(ActorContext actor, String siteCode, SourceChannel channel, String resource) {
        authorization.require(actor, SflPermission.FACILITIES_CLEANING_READ, channel, resource, "read", siteCode);
        if (onlyVendor(actor)) {
            audit.recordDenial(actor, channel, resource, "read", siteCode == null ? "*" : siteCode,
                    "A vendor technician may read only the cleaning tasks assigned to them");
            throw new FacilitiesException.UnauthorizedScopeException(
                    "You may only view cleaning tasks assigned to you.");
        }
    }

    /** Applied to reads and writes alike: a narrowing only one of the two obeys is decorative. */
    void assertVisible(ActorContext actor, CleaningTask task, SourceChannel channel) {
        authorization.requireSite(actor, task.siteCode(), channel, "CleaningTask", task.id().toString());
        Narrowing narrowing = readNarrowing(actor, task.siteCode(), channel, task.id().toString());
        switch (narrowing.kind()) {
            case NONE -> {
                return;
            }
            case ASSIGNED_TO_ME -> {
                if (task.isAssignedTo(actor.actorId())) {
                    return;
                }
                audit.recordDenial(actor, channel, "CleaningTask", task.id().toString(), task.siteCode(),
                        "A vendor technician may act only on cleaning tasks assigned to them");
                throw new FacilitiesException.UnauthorizedScopeException(
                        "You may only view cleaning tasks assigned to you.");
            }
            case REQUESTED_BY_ME -> {
                if (actor.actorId().equals(task.requestedBy())) {
                    return;
                }
                audit.recordDenial(actor, channel, "CleaningTask", task.id().toString(), task.siteCode(),
                        "An occupant may read only the cleaning requests they raised");
                throw new FacilitiesException.UnauthorizedScopeException(
                        "You may only view cleaning requests you raised.");
            }
            default -> throw new IllegalStateException("Unhandled narrowing " + narrowing.kind());
        }
    }

    /**
     * The rule for working a task: you are its assignee and may execute, or you supervise.
     *
     * <p>Stricter than S153 for in-house staff, deliberately: the checklist is signed evidence of who
     * cleaned, and a colleague ticking somebody else's items would make that evidence untrue.
     */
    void requireMayExecute(ActorContext actor, CleaningTask task, SourceChannel channel) {
        authorization.requireSite(actor, task.siteCode(), channel, "CleaningTask", task.id().toString());
        if (authorization.has(actor, SflPermission.FACILITIES_CLEANING_TASK_SUPERVISE)) {
            return;
        }
        authorization.require(actor, SflPermission.FACILITIES_CLEANING_TASK_EXECUTE, task.siteCode(), channel,
                "CleaningTask", task.id().toString());
        if (!task.isAssignedTo(actor.actorId())) {
            audit.recordDenial(actor, channel, "CleaningTask", task.id().toString(), task.siteCode(),
                    "Only the assignee or a supervisor may work a cleaning task");
            throw new FacilitiesException.UnauthorizedScopeException(
                    "You may only work cleaning tasks assigned to you.");
        }
    }

    static boolean onlyVendor(ActorContext actor) {
        Set<SflRole> roles = actor.principal().roles();
        return roles.contains(SflRole.VENDOR_TECHNICIAN)
                && roles.stream().allMatch(role -> role == SflRole.VENDOR_TECHNICIAN);
    }

    // ---- descriptions and events --------------------------------------------------------------

    /**
     * A commitment in words S173 can show a coordinator verbatim - "CT-MAIN-000123, routine clean of
     * HALL-A 2026-09-28 09:00-10:00 (Africa/Accra), assigned to vendor Spotless Ltd (tech.kofi)". The
     * SRS-SFL-S169-04 validation rule is that a conflict "must name the competing commitment"; this is
     * the naming.
     */
    String describe(CleaningTask task) {
        ZoneId zone = configuration.timeZone(task.siteCode());
        String when = HOURS.format(task.windowStart().atZone(zone)) + "-"
                + DateTimeFormatter.ofPattern("HH:mm").format(task.dueBy().atZone(zone)) + " (" + zone.getId() + ")";
        String what = switch (task.origin()) {
            case ROUTINE -> "routine clean";
            case BOOKING_SETUP -> "setup clean for booking " + task.bookingReference();
            case BOOKING_TEARDOWN -> "teardown clean for booking " + task.bookingReference();
            case REACTIVE -> "reactive request";
            case EVENT -> "event clean for " + task.eventReference();
            case ADHOC -> "ad-hoc clean";
        };
        String who;
        if (task.assigneeType() == AssigneeType.VENDOR) {
            String vendor = repository.findVendor(task.vendorId()).map(CleaningVendor::name)
                    .orElse(String.valueOf(task.vendorId()));
            who = "assigned to vendor " + vendor + " (" + task.assignedTo() + ")";
        } else if (task.assigneeType() == AssigneeType.STAFF) {
            who = "assigned to " + task.assignedTo();
        } else {
            who = "not yet assigned";
        }
        return task.taskNumber() + ", " + what + " of " + task.roomCode() + " " + when + ", " + who;
    }

    /** References and classifications only - never the description, comment or a person's name. */
    Map<String, Object> taskPayload(CleaningTask task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.id().toString());
        payload.put("taskNumber", task.taskNumber());
        payload.put("siteCode", task.siteCode());
        payload.put("roomId", task.roomId().toString());
        payload.put("roomCode", task.roomCode());
        payload.put("spaceType", task.spaceType().name());
        payload.put("origin", task.origin().name());
        payload.put("status", task.status().name());
        payload.put("windowStart", task.windowStart().toString());
        payload.put("dueBy", task.dueBy().toString());
        putIfPresent(payload, "bookingId", task.bookingId());
        putIfPresent(payload, "bookingReference", task.bookingReference());
        putIfPresent(payload, "reservationId", task.reservationId());
        putIfPresent(payload, "eventReference", task.eventReference());
        putIfPresent(payload, "scheduleId", task.scheduleId());
        putIfPresent(payload, "assigneeType", task.assigneeType());
        putIfPresent(payload, "vendorId", task.vendorId());
        putIfPresent(payload, "completedAt", task.completedAt());
        putIfPresent(payload, "cancelledAt", task.cancelledAt());
        return payload;
    }

    void publishTask(String eventType, CleaningTask task, ActorContext actor) {
        outbox.record(eventType, 1, "CleaningTask", task.id(), task.siteCode(), actor.correlationId(),
                actor.actorId(), taskPayload(task));
    }

    void publish(String eventType, String aggregateType, UUID aggregateId, String siteCode,
            Map<String, Object> payload, ActorContext actor) {
        outbox.record(eventType, 1, aggregateType, aggregateId, siteCode, actor.correlationId(), actor.actorId(),
                payload);
    }

    static void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value.toString());
        }
    }
}
