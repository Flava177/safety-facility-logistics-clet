package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.EventLogisticsRepository;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventReconciliationLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventTemplateLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessEscalation;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.policy.EventReadinessPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hand-off completeness and post-event reconciliation - SRS-SFL-S173-04.
 *
 * <h2>The escalation sweep</h2>
 *
 * <p>"Before the event's start time, all resource requests must show a status of confirmed or an
 * explicitly accepted manual-coordination status; any request still merely requested escalates to the
 * coordinator." The sweep looks at live tasks whose escalation window
 * ({@code event-logistics.escalation.window}, default 48 hours before the start) has opened, and
 * escalates each unresolved request once: an escalation row (the evidence the completion rule checks
 * for, and the notification intent naming the coordinator) and one
 * {@code sfl.ifimp.event-readiness-escalated.v1} per task per sweep. The acceptance criterion - notified
 * "before the event, not after" - is met by the window opening before the start and the sweep running
 * every five minutes; the escalation row records whether it did fall before the start.
 *
 * <h2>Reconciliation feeds the templates</h2>
 *
 * <p>After the event ends each request is reconciled - delivered, partial, not delivered, with notes.
 * A gap creates or strengthens the event category's template line for that resource type; a line that
 * has gapped often enough (the persistence threshold) pre-populates the next decomposition of that
 * category. That loop is S173-04's "feeding lessons back into future event templates".
 */
@Service
public class EventReadinessService {

    private final EventLogisticsRepository repository;
    private final EventLogisticsConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public EventReadinessService(EventLogisticsRepository repository, EventLogisticsConfiguration configuration,
            FacilitiesAuthorization authorization, AuditPort audit, ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    public record EscalationSweep(int tasksExamined, int escalated, Instant evaluatedAt) {
    }

    /**
     * Escalates every unresolved request whose event's window has opened.
     *
     * <p>Candidates are read generously - live events starting within four times the platform-default
     * window - and each is then judged against its own site's window, so a site that lengthened its window
     * up to that bound is still honoured. A site window longer than four times the default is a
     * configuration to raise with the default, and the runbook says so.
     */
    @Transactional
    public EscalationSweep sweepEscalations(ActorContext actor) {
        Instant now = clock.instant();
        Duration widest = configuration.escalationWindow(null);
        int examined = 0;
        int escalated = 0;
        for (EventSetupTask task : repository.findLiveTasksForEscalation(now.plus(widest.multipliedBy(4)), now,
                configuration.sweepBatchSize())) {
            examined++;
            escalated += escalate(task, now, actor);
        }
        return new EscalationSweep(examined, escalated, now);
    }

    private int escalate(EventSetupTask task, Instant now, ActorContext actor) {
        Duration window = configuration.escalationWindow(task.siteCode());
        Set<UUID> already = repository.findEscalationsForTask(task.id()).stream()
                .map(ReadinessEscalation::resourceRequestId).collect(Collectors.toSet());
        List<EventResourceRequest> due = EventReadinessPolicy.dueForEscalation(task,
                repository.findRequestsForTask(task.id()), already, now, window);
        if (due.isEmpty()) {
            return 0;
        }
        String notifiedTo = task.coordinatorId() == null ? ReadinessEscalation.COORDINATOR_DESK : task.coordinatorId();
        boolean beforeStart = now.isBefore(task.details().startsAt());
        List<String> escalatedIds = new ArrayList<>();
        for (EventResourceRequest request : due) {
            ReadinessEscalation escalation = repository.saveEscalation(new ReadinessEscalation(UUID.randomUUID(),
                    task.siteCode(), task.id(), request.id(), request.status(), task.details().startsAt(), now,
                    window.toMinutes(), notifiedTo, ReadinessEscalation.NOTIFICATION_RECORDED, beforeStart,
                    actor.actorId(), actor.correlationId()));
            audit.record(actor, SourceChannel.SCHEDULER, AuditAction.EVENT_READINESS_ESCALATED, "EventResourceRequest",
                    request.id().toString(), task.siteCode(), null, escalation);
            escalatedIds.add(request.id().toString());
        }
        Map<String, Object> facts = EventHandoffService.taskFacts(task);
        facts.put("resourceRequestIds", escalatedIds);
        facts.put("resourceTypes", due.stream().map(request -> request.resourceType().name()).toList());
        facts.put("notifiedTo", notifiedTo);
        facts.put("beforeEventStart", beforeStart);
        facts.put("windowMinutes", window.toMinutes());
        outbox.record("sfl.ifimp.event-readiness-escalated.v1", 1, "EventSetupTask", task.id(), task.siteCode(),
                actor.correlationId(), actor.actorId(), facts);
        return due.size();
    }

    /** S173-04 post-event reconciliation, and the template feedback it drives. */
    @Transactional
    public List<EventReconciliationLine> reconcile(EventLogisticsCommands.RecordReconciliation command) {
        ActorContext actor = command.actor();
        EventSetupTask task = repository.findTask(command.setupTaskId()).orElseThrow(
                () -> new FacilitiesException.RecordNotFoundException("Event set-up task", command.setupTaskId()));
        authorization.require(actor, SflPermission.FACILITIES_EVENT_COORDINATE, task.siteCode(), command.channel(),
                "EventSetupTask", task.id().toString());
        Instant now = clock.instant();
        if (task.status() == EventSetupTaskStatus.CANCELLED || task.status() == EventSetupTaskStatus.OPEN) {
            throw new FacilitiesException.InvalidStateTransitionException("Set-up task " + task.taskReference()
                    + " is " + task.status() + "; only a confirmed or completed event is reconciled.");
        }
        if (now.isBefore(task.details().endsAt())) {
            throw new FacilitiesException.ValidationFailedException(
                    "Reconciliation records what was delivered after the event; this event ends at "
                            + task.details().endsAt() + ".");
        }
        if (command.lines().isEmpty()) {
            throw new FacilitiesException.ValidationFailedException("Reconcile at least one resource request.");
        }
        Map<UUID, EventResourceRequest> requests = new LinkedHashMap<>();
        repository.findRequestsForTask(task.id()).forEach(request -> requests.put(request.id(), request));
        Set<UUID> done = new HashSet<>();
        repository.findReconciliationForTask(task.id()).forEach(line -> done.add(line.resourceRequestId()));

        List<EventReconciliationLine> saved = new ArrayList<>();
        int threshold = configuration.templateGapThreshold(task.siteCode());
        for (EventLogisticsCommands.ReconciliationEntry entry : command.lines()) {
            EventResourceRequest request = requests.get(entry.resourceRequestId());
            if (request == null) {
                throw new FacilitiesException.ValidationFailedException(
                        "Resource request " + entry.resourceRequestId() + " is not part of " + task.taskReference() + ".");
            }
            if (entry.outcome() == null) {
                throw new FacilitiesException.ValidationFailedException("Every reconciliation line needs an outcome.");
            }
            if (!request.status().isLive()) {
                throw new FacilitiesException.ValidationFailedException(
                        "A cancelled request has nothing to reconcile.");
            }
            if (!done.add(request.id())) {
                throw new FacilitiesException.ValidationFailedException(
                        request.resourceType() + " request " + request.id() + " is already reconciled.");
            }
            EventReconciliationLine line = repository.saveReconciliation(new EventReconciliationLine(
                    UUID.randomUUID(), task.siteCode(), task.id(), request.id(), request.resourceType(),
                    request.status(), request.quantity(), entry.deliveredQuantity(), entry.outcome(),
                    EstateCodes.blankToNull(entry.notes()), actor.actorId(), now, actor.correlationId()));
            saved.add(line);
            if (entry.outcome().isGap()) {
                feedTemplate(task, request, line, threshold, actor, command.channel(), now);
            }
        }
        audit.record(actor, command.channel(), AuditAction.EVENT_RECONCILIATION_RECORDED, "EventSetupTask",
                task.id().toString(), task.siteCode(), null, saved);
        Map<String, Object> facts = EventHandoffService.taskFacts(task);
        facts.put("linesRecorded", saved.size());
        facts.put("gaps", saved.stream().filter(line -> line.outcome().isGap())
                .map(line -> line.resourceType().name() + ":" + line.outcome().name()).toList());
        outbox.record("sfl.ifimp.event-reconciliation-recorded.v1", 1, "EventSetupTask", task.id(), task.siteCode(),
                actor.correlationId(), actor.actorId(), facts);
        return saved;
    }

    private void feedTemplate(EventSetupTask task, EventResourceRequest request, EventReconciliationLine line,
            int threshold, ActorContext actor, SourceChannel channel, Instant now) {
        String category = task.details().eventCategory();
        EventTemplateLine before = repository.findTemplateLine(task.siteCode(), category, request.resourceType())
                .orElse(null);
        EventTemplateLine after = repository.saveTemplateLine(before == null
                ? EventTemplateLine.firstGap(UUID.randomUUID(), task.siteCode(), category, request, line.notes(),
                        task.id(), actor.actorId(), now, channel, actor.correlationId())
                : before.anotherGap(request, line.notes(), task.id(), actor.actorId(), now, channel,
                        actor.correlationId()));
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("templateLine", after);
        facts.put("persistent", after.isPersistent(threshold));
        facts.put("fromReconciliationLine", line.id().toString());
        audit.record(actor, channel, AuditAction.EVENT_TEMPLATE_GAP_RECORDED, "EventTemplateLine",
                after.id().toString(), after.siteCode(), before, facts);
    }

    @Transactional(readOnly = true)
    public List<EventReconciliationLine> reconciliation(UUID setupTaskId, ActorContext actor, SourceChannel channel) {
        EventSetupTask task = repository.findTask(setupTaskId).orElseThrow(
                () -> new FacilitiesException.RecordNotFoundException("Event set-up task", setupTaskId));
        authorization.require(actor, SflPermission.FACILITIES_EVENT_READ, task.siteCode(), channel, "EventSetupTask",
                task.id().toString());
        return repository.findReconciliationForTask(task.id());
    }

    /** The lessons in force for a site, optionally for one category. */
    @Transactional(readOnly = true)
    public List<EventTemplateLine> templates(String siteCode, String eventCategory, ActorContext actor,
            SourceChannel channel) {
        EstateCodes.require(siteCode, "siteCode");
        String site = EstateCodes.normalize(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_EVENT_READ, site, channel, "EventTemplateLine", "list");
        return repository.findTemplateLines(site, eventCategory == null || eventCategory.isBlank() ? null
                : eventCategory.strip().toUpperCase(java.util.Locale.ROOT));
    }

    /** The persistence threshold in force, for the template view. */
    public int templateGapThreshold(String siteCode) {
        return configuration.templateGapThreshold(siteCode);
    }
}
