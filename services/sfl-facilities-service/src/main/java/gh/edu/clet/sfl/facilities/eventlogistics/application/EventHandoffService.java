package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.CcpEventsDirectoryPort;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.EventLogisticsRepository;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventDetails;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.S078EventStatus;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.FacilityRoom;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.PlatformThreads;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VerifiedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hand-off intake from CCP Events (S078) - SRS-SFL-S173-01.
 *
 * <h2>The order of the checks</h2>
 * <ol>
 *   <li><strong>Permission.</strong> {@code FACILITIES_EVENT_HANDOFF_INGEST}, held only by integration
 *       principals: a person who could post a hand-off could conjure a set-up task.</li>
 *   <li><strong>Authentication</strong> through the shared {@link VendorMessageVerifier} on
 *       {@link VendorChannel#CCP_EVENTS} (NFR-SEC2). A forged hand-off is rejected, logged, audited and
 *       forwarded there, and nothing is created.</li>
 *   <li><strong>Duplicate.</strong> The same idempotency key again answers as the first time did.</li>
 *   <li><strong>Payload.</strong> A confirmation must carry the event's date, location, attendance and
 *       category; a malformed value is a {@code verifier.reject}, recorded the same way.</li>
 *   <li><strong>Resolution.</strong> The reference must be one S078 recognises, through
 *       {@link CcpEventsDirectoryPort}. An unrecognised or unconfirmed event is refused with
 *       {@code EVENT_REFERENCE_UNRESOLVABLE}, recorded as {@code EVENT_HANDOFF_REJECTED} in a
 *       transaction of its own, and nothing is created (S173-01 validation).</li>
 *   <li><strong>Location.</strong> The room must be in the S152 register at the site. A hand-off naming
 *       a room S152 does not hold is refused rather than creating a task nobody can book.</li>
 * </ol>
 *
 * <h2>One task per S078 event, whatever S078 sends</h2>
 *
 * <p>The reference is unique on the task table. A later confirmed hand-off for the same event updates
 * the task; a cancelled one cancels it and its live requests; a repeat with nothing changed is recorded
 * as {@code UNCHANGED}. So the same S078 fact delivered twice under two keys is still one task - the
 * idempotency that matters is on the event, not only on the message.
 */
@Service
public class EventHandoffService {

    static final String MODULE = "S173";

    /** Fields every hand-off needs before anything else can be decided. */
    static final List<String> REQUIRED_FIELDS = List.of("s078EventReference", "status");

    /** Fields a confirmation needs - the S173-01 "date, location, expected attendance". */
    static final List<String> CONFIRMATION_FIELDS = List.of("title", "startsAt", "endsAt", "roomCode",
            "expectedAttendance", "eventCategory");

    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}");

    private final EventLogisticsRepository repository;
    private final FacilitiesRepository estate;
    private final CcpEventsDirectoryPort directory;
    private final VendorMessageVerifier verifier;
    private final EventLogisticsRefusalRecorder refusals;
    private final EventResourceRequestService requests;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public EventHandoffService(EventLogisticsRepository repository, FacilitiesRepository estate,
            CcpEventsDirectoryPort directory, VendorMessageVerifier verifier, EventLogisticsRefusalRecorder refusals,
            EventResourceRequestService requests, FacilitiesAuthorization authorization, AuditPort audit,
            ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.estate = estate;
        this.directory = directory;
        this.verifier = verifier;
        this.refusals = refusals;
        this.requests = requests;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * What a hand-off did.
     *
     * @param task the set-up task it created or changed; {@code null} only for a duplicate whose original
     *        record cannot be found
     * @param duplicate the same message was already accepted and nothing was re-actioned
     */
    public record HandoffResult(EventHandoff handoff, EventSetupTask task, boolean duplicate) {
    }

    @Transactional
    public HandoffResult accept(SignedVendorMessage message, ActorContext actor) {
        authorization.require(actor, SflPermission.FACILITIES_EVENT_HANDOFF_INGEST, SourceChannel.INTEGRATION,
                "EventHandoff", "new", message.siteCode());
        VerifiedVendorMessage verified = verifier.accept(message, VendorChannel.CCP_EVENTS, REQUIRED_FIELDS, MODULE,
                actor);
        if (verified.duplicate()) {
            Optional<EventHandoff> original = repository.findAcceptedHandoff(verified.sourceId(),
                    verified.idempotencyKey());
            return new HandoffResult(original.orElse(null),
                    original.map(EventHandoff::setupTaskId).flatMap(repository::findTask).orElse(null), true);
        }

        String reference = verified.text("s078EventReference").strip();
        if (!REFERENCE.matcher(reference).matches()) {
            throw verifier.reject(verified, "s078EventReference is not a well-formed S078 reference.", MODULE,
                    actor);
        }
        Optional<S078EventStatus> stated = S078EventStatus.parse(verified.text("status"));
        if (stated.isEmpty()) {
            throw verifier.reject(verified, "status '" + verified.text("status") + "' is not an S078 event status.",
                    MODULE, actor);
        }

        CcpEventsDirectoryPort.Resolution resolution = directory.resolve(reference, stated.get());
        if (!resolution.resolvable()) {
            throw unresolvable(verified, reference, stated.get(), resolution.explanation(), actor);
        }

        Optional<EventSetupTask> existing = repository.findTaskByS078Reference(reference);
        if (existing.isPresent() && !existing.get().siteCode().equals(verified.siteCode())) {
            throw verifier.reject(verified, "S078 event " + reference + " is held for site "
                    + existing.get().siteCode() + ", not " + verified.siteCode() + ".", MODULE, actor);
        }
        Instant at = clock.instant();
        if (stated.get() == S078EventStatus.CANCELLED) {
            // Resolvable here means known: the directory does not resolve a cancellation it has never seen.
            return cancelled(verified, existing.orElseThrow(), actor, at);
        }
        EventDetails details = details(verified, actor);
        return existing.isPresent()
                ? updated(verified, existing.get(), details, actor, at)
                : created(verified, reference, details, actor, at);
    }

    // =============================================================================================

    private HandoffResult created(VerifiedVendorMessage verified, String reference, EventDetails details,
            ActorContext actor, Instant at) {
        EventSetupTask task = repository.saveTask(EventSetupTask.fromHandoff(UUID.randomUUID(), verified.siteCode(),
                repository.nextTaskReference(verified.siteCode()), reference, details, actor.actorId(), at,
                SourceChannel.INTEGRATION, actor.correlationId()));
        EventHandoff handoff = accepted(verified, reference, EventHandoff.Action.CREATED, task, actor, at);
        audit.record(actor, SourceChannel.INTEGRATION, AuditAction.EVENT_SETUP_TASK_CREATED, "EventSetupTask",
                task.id().toString(), task.siteCode(), null, task);
        outbox.record("sfl.ifimp.event-setup-task-created.v1", 1, "EventSetupTask", task.id(), task.siteCode(),
                actor.correlationId(), actor.actorId(), taskFacts(task));
        return new HandoffResult(handoff, task, false);
    }

    private HandoffResult updated(VerifiedVendorMessage verified, EventSetupTask task, EventDetails details,
            ActorContext actor, Instant at) {
        if (!task.status().isLive()) {
            return new HandoffResult(accepted(verified, task.s078EventReference(),
                    EventHandoff.Action.IGNORED_TASK_CLOSED, task, actor, at), task, false);
        }
        if (details.equals(task.details())) {
            return new HandoffResult(accepted(verified, task.s078EventReference(), EventHandoff.Action.UNCHANGED,
                    task, actor, at), task, false);
        }
        boolean moved = details.movesFrom(task.details());
        EventSetupTask changed = repository.saveTask(task.withHandoffDetails(details,
                details.materiallyDiffersFrom(task.details()), actor.actorId(), at, SourceChannel.INTEGRATION,
                actor.correlationId()));
        if (moved) {
            // A booking for the old hall at the old time is not a booking for this event. Released and
            // re-queued rather than silently kept: the coordinator re-routes against the new details.
            requests.requeueAfterMove(changed, actor, SourceChannel.INTEGRATION);
        }
        EventHandoff handoff = accepted(verified, task.s078EventReference(), EventHandoff.Action.UPDATED, changed,
                actor, at);
        return new HandoffResult(handoff, changed, false);
    }

    private HandoffResult cancelled(VerifiedVendorMessage verified, EventSetupTask task, ActorContext actor,
            Instant at) {
        if (!task.status().isLive()) {
            return new HandoffResult(accepted(verified, task.s078EventReference(),
                    task.status() == gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus.CANCELLED
                            ? EventHandoff.Action.UNCHANGED
                            : EventHandoff.Action.IGNORED_TASK_CLOSED, task, actor, at), task, false);
        }
        String reason = Optional.ofNullable(verified.text("cancellationReason")).filter(value -> !value.isBlank())
                .map(String::strip).orElse("Cancelled in CCP Events (S078).");
        // Requests first, so the S159 observer that fires as a booking is cancelled finds them already
        // cancelled and leaves them alone.
        requests.cancelAllForTask(task, reason, actor, SourceChannel.INTEGRATION);
        EventSetupTask cancelledTask = repository.saveTask(task.cancel(reason, actor.actorId(), at,
                SourceChannel.INTEGRATION, actor.correlationId()));
        EventHandoff handoff = accepted(verified, task.s078EventReference(), EventHandoff.Action.CANCELLED,
                cancelledTask, actor, at);
        return new HandoffResult(handoff, cancelledTask, false);
    }

    private EventHandoff accepted(VerifiedVendorMessage verified, String reference, EventHandoff.Action action,
            EventSetupTask task, ActorContext actor, Instant at) {
        EventHandoff handoff = repository.saveHandoff(new EventHandoff(UUID.randomUUID(), verified.siteCode(),
                reference, verified.text("status").strip().toUpperCase(Locale.ROOT), EventHandoff.Outcome.ACCEPTED,
                action, task.id(), verified.inboxId(), verified.sourceId(), verified.idempotencyKey(), null, at,
                actor.actorId(), actor.correlationId()));
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("handoffId", handoff.id().toString());
        facts.put("s078EventReference", reference);
        facts.put("s078Status", handoff.s078Status());
        facts.put("action", action.name());
        facts.put("setupTaskId", task.id().toString());
        facts.put("taskReference", task.taskReference());
        facts.put("taskStatus", task.status().name());
        audit.record(actor, SourceChannel.INTEGRATION, AuditAction.EVENT_HANDOFF_ACCEPTED, "EventHandoff",
                handoff.id().toString(), handoff.siteCode(), null, facts);
        outbox.record("sfl.ifimp.event-handoff-accepted.v1", 1, "EventHandoff", handoff.id(), handoff.siteCode(),
                actor.correlationId(), actor.actorId(), facts);
        return handoff;
    }

    /**
     * S173-01 error state "Unresolvable Event Reference". Recorded twice, deliberately: once in S173's own
     * register and audit ({@code EVENT_HANDOFF_REJECTED}, which is what an event coordinator reads), and
     * once through the verifier ({@code VENDOR_MESSAGE_REJECTED}, the inbox, the SIEM), because an
     * authenticated source sending references S078 does not know is either a broken integration or a
     * compromised key, and either is a security-relevant fact.
     */
    private FacilitiesException unresolvable(VerifiedVendorMessage verified, String reference,
            S078EventStatus stated, String explanation, ActorContext actor) {
        EventHandoff rejected = new EventHandoff(UUID.randomUUID(), verified.siteCode(), reference, stated.name(),
                EventHandoff.Outcome.REJECTED, null, null, verified.inboxId(), verified.sourceId(),
                verified.idempotencyKey(), explanation, clock.instant(), actor.actorId(), actor.correlationId());
        PlatformThreads.callAsPlatform(() -> refusals.handoffRejected(rejected, actor));
        verifier.reject(verified, "Unresolvable S078 event reference: " + explanation, MODULE, actor);
        return new FacilitiesException(FacilitiesErrorCode.EVENT_REFERENCE_UNRESOLVABLE,
                FacilitiesErrorCode.EVENT_REFERENCE_UNRESOLVABLE.defaultMessage() + " " + explanation);
    }

    private EventDetails details(VerifiedVendorMessage verified, ActorContext actor) {
        for (String field : CONFIRMATION_FIELDS) {
            String value = verified.text(field);
            if (value == null || value.isBlank()) {
                throw verifier.reject(verified, "A confirmed hand-off requires '" + field + "'.", MODULE, actor);
            }
        }
        Instant startsAt;
        Instant endsAt;
        int attendance;
        try {
            startsAt = Instant.parse(verified.text("startsAt").strip());
            endsAt = Instant.parse(verified.text("endsAt").strip());
            attendance = Integer.parseInt(verified.text("expectedAttendance").strip());
        } catch (DateTimeParseException | NumberFormatException malformed) {
            throw verifier.reject(verified, "startsAt/endsAt must be ISO instants and expectedAttendance a whole"
                    + " number.", MODULE, actor);
        }
        String roomCode = verified.text("roomCode").strip().toUpperCase(Locale.ROOT);
        Optional<FacilityRoom> room = estate.findRoomByCode(verified.siteCode(), roomCode);
        if (room.isEmpty()) {
            throw verifier.reject(verified, "Room " + roomCode + " is not in the S152 register for site "
                    + verified.siteCode() + ".", MODULE, actor);
        }
        try {
            return new EventDetails(verified.text("title"), verified.text("eventCategory"), startsAt, endsAt,
                    room.get().id(), roomCode, attendance, verified.text("statedRequirements"),
                    bool(verified.text("externalContractors")), bool(verified.text("temporaryStructures")));
        } catch (IllegalArgumentException invalid) {
            throw verifier.reject(verified, invalid.getMessage(), MODULE, actor);
        }
    }

    private static boolean bool(String value) {
        return value != null && Boolean.parseBoolean(value.strip());
    }

    static Map<String, Object> taskFacts(EventSetupTask task) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("setupTaskId", task.id().toString());
        facts.put("taskReference", task.taskReference());
        facts.put("s078EventReference", task.s078EventReference());
        facts.put("eventCategory", task.details().eventCategory());
        facts.put("startsAt", task.details().startsAt().toString());
        facts.put("endsAt", task.details().endsAt().toString());
        facts.put("roomCode", task.details().roomCode());
        facts.put("expectedAttendance", task.details().expectedAttendance());
        facts.put("status", task.status().name());
        return facts;
    }
}
