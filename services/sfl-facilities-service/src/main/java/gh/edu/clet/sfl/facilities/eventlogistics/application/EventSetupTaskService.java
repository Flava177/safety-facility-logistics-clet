package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.common.hse.RiskAssessmentCurrency;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.EventLogisticsRepository;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessEscalation;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.RiskAssessmentProjection;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.policy.EventReadinessPolicy;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.policy.EventRiskPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The set-up task's own lifecycle and the readiness views - SRS-SFL-S173-02 (roll-up), S173-03 (risk
 * assessment before confirmation) and S173-04 (completion rule).
 *
 * <h2>S173-03, and what it reuses</h2>
 *
 * <p>Whether an event is higher-risk is {@link EventRiskPolicy} over runtime configuration. Whether its
 * linked assessment is current is {@link RiskAssessmentCurrency#assess} - the shared rule S164-01 will
 * use - over S173's projection of S165's events. Nothing here re-derives currency. Because nothing
 * publishes S165's events yet, the projection is empty and every higher-risk confirmation is refused:
 * fail-closed, as it should be, until S165 ships.
 */
@Service
public class EventSetupTaskService {

    private final EventLogisticsRepository repository;
    private final EventLogisticsConfiguration configuration;
    private final EventLogisticsRefusalRecorder refusals;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public EventSetupTaskService(EventLogisticsRepository repository, EventLogisticsConfiguration configuration,
            EventLogisticsRefusalRecorder refusals, FacilitiesAuthorization authorization, AuditPort audit,
            ServiceOutbox outbox, Clock clock) {
        this.repository = repository;
        this.configuration = configuration;
        this.refusals = refusals;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** The risk position of one event, right now. */
    record RiskPosition(Set<EventRiskPolicy.Trigger> triggers, RiskAssessmentCurrency.Verdict verdict,
            String detail) {

        boolean higherRisk() {
            return !triggers.isEmpty();
        }
    }

    // =============================================================================================
    // Commands
    // =============================================================================================

    /**
     * Links an S165 assessment. S165-02: "A lapsed assessment cannot be newly linked" - so linking runs
     * the same currency rule and refuses what confirmation would refuse, rather than accepting a link
     * that is already useless.
     */
    @Transactional
    public EventSetupTask linkRiskAssessment(EventLogisticsCommands.LinkRiskAssessment command) {
        ActorContext actor = command.actor();
        EventSetupTask task = requireTask(command.setupTaskId());
        authorization.require(actor, SflPermission.FACILITIES_EVENT_COORDINATE, task.siteCode(), command.channel(),
                "EventSetupTask", task.id().toString());
        task.requireLive("link a risk assessment to");
        EstateCodes.require(command.assessmentId(), "assessmentId");
        String assessmentId = command.assessmentId().strip();

        Optional<RiskAssessmentProjection> found = command.version() == null
                ? repository.findRiskAssessmentVersions(assessmentId).stream()
                        .filter(version -> version.status() == RiskAssessmentCurrency.Status.PUBLISHED)
                        .max(Comparator.comparingInt(RiskAssessmentProjection::version))
                : repository.findRiskAssessment(assessmentId, command.version());
        if (found.isEmpty() || !found.get().siteCode().equals(task.siteCode())) {
            throw new FacilitiesException(FacilitiesErrorCode.EVENT_RISK_ASSESSMENT_NOT_CURRENT,
                    "S173 holds no S165 record of assessment " + assessmentId + " for site " + task.siteCode()
                            + ". S165 publishes none yet, so no assessment can be linked until it does.");
        }
        RiskAssessmentCurrency.Verdict verdict = RiskAssessmentCurrency.assess(found.get().toSnapshot(),
                clock.instant());
        if (!verdict.current()) {
            throw new FacilitiesException(FacilitiesErrorCode.EVENT_RISK_ASSESSMENT_NOT_CURRENT,
                    "Assessment " + assessmentId + " v" + found.get().version() + " cannot be linked: "
                            + verdict.reason() + ".");
        }
        EventSetupTask linked = repository.saveTask(task.linkRiskAssessment(assessmentId, found.get().version(),
                actor.actorId(), clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.EVENT_RISK_ASSESSMENT_LINKED, "EventSetupTask",
                linked.id().toString(), linked.siteCode(), task, linked);
        return linked;
    }

    /** S173-03: a higher-risk event needs a linked, current S165 assessment; a routine one does not. */
    @Transactional
    public EventSetupTask confirm(EventLogisticsCommands.ConfirmSetupTask command) {
        ActorContext actor = command.actor();
        EventSetupTask task = requireTask(command.setupTaskId());
        authorization.require(actor, SflPermission.FACILITIES_EVENT_COORDINATE, task.siteCode(), command.channel(),
                "EventSetupTask", task.id().toString());
        if (!task.status().canTransitionTo(gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus
                .CONFIRMED)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Set-up task " + task.taskReference() + " is " + task.status() + " and cannot be confirmed.");
        }
        RiskPosition risk = riskPosition(task, clock.instant());
        if (risk.higherRisk() && !risk.verdict().current()) {
            refusals.confirmationRefused(task, risk.verdict().reason().name(), risk.detail(), actor,
                    command.channel());
            throw new FacilitiesException(FacilitiesErrorCode.EVENT_RISK_ASSESSMENT_NOT_CURRENT, risk.detail());
        }
        EventSetupTask confirmed = repository.saveTask(task.confirm(actor.actorId(), clock.instant(),
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.EVENT_SETUP_TASK_CONFIRMED, "EventSetupTask",
                confirmed.id().toString(), confirmed.siteCode(), task, confirmed);
        Map<String, Object> facts = EventHandoffService.taskFacts(confirmed);
        facts.put("higherRisk", risk.higherRisk());
        facts.put("riskTriggers", risk.triggers().stream().map(Enum::name).sorted().toList());
        facts.put("riskAssessmentId", confirmed.riskAssessmentId());
        facts.put("riskAssessmentVersion", confirmed.riskAssessmentVersion());
        outbox.record("sfl.ifimp.event-setup-task-confirmed.v1", 1, "EventSetupTask", confirmed.id(),
                confirmed.siteCode(), actor.correlationId(), actor.actorId(), facts);
        return confirmed;
    }

    /**
     * S173-04 validation: "A set-up task cannot be marked complete with an unresolved requested-status
     * resource request and no escalation record." An unresolved request that <em>was</em> escalated does
     * not block: the coordinator was told and made a call, and the record says so.
     */
    @Transactional
    public EventSetupTask complete(EventLogisticsCommands.CompleteSetupTask command) {
        ActorContext actor = command.actor();
        EventSetupTask task = requireTask(command.setupTaskId());
        authorization.require(actor, SflPermission.FACILITIES_EVENT_COORDINATE, task.siteCode(), command.channel(),
                "EventSetupTask", task.id().toString());
        if (!task.status().canTransitionTo(
                gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus.COMPLETED)) {
            throw new FacilitiesException.InvalidStateTransitionException("Set-up task " + task.taskReference()
                    + " is " + task.status() + "; only a confirmed set-up task can be marked complete.");
        }
        Set<UUID> escalated = repository.findEscalationsForTask(task.id()).stream()
                .map(ReadinessEscalation::resourceRequestId).collect(Collectors.toSet());
        List<EventResourceRequest> blocking = EventReadinessPolicy.blockingCompletion(
                repository.findRequestsForTask(task.id()), escalated);
        if (!blocking.isEmpty()) {
            throw new FacilitiesException(FacilitiesErrorCode.EVENT_UNRESOLVED_RESOURCE_REQUEST,
                    FacilitiesErrorCode.EVENT_UNRESOLVED_RESOURCE_REQUEST.defaultMessage() + " Unresolved: "
                            + blocking.stream().map(request -> request.resourceType() + " (" + request.status() + ")")
                                    .collect(Collectors.joining(", ")) + ".");
        }
        EventSetupTask completed = repository.saveTask(task.complete(command.notes(), actor.actorId(),
                clock.instant(), command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.EVENT_SETUP_TASK_COMPLETED, "EventSetupTask",
                completed.id().toString(), completed.siteCode(), task, completed);
        return completed;
    }

    // =============================================================================================
    // Queries
    // =============================================================================================

    @Transactional(readOnly = true)
    public EventSetupTask findById(UUID id, ActorContext actor, SourceChannel channel) {
        EventSetupTask task = requireTask(id);
        authorization.require(actor, SflPermission.FACILITIES_EVENT_READ, task.siteCode(), channel, "EventSetupTask",
                id.toString());
        return task;
    }

    /** The consolidated view for one event (S173-02). */
    @Transactional(readOnly = true)
    public EventReadinessView readiness(UUID id, ActorContext actor, SourceChannel channel) {
        return view(findById(id, actor, channel));
    }

    /**
     * The dashboard's "upcoming events by readiness status, unresourced tasks, ... cross-unit resource
     * conflicts", worst first and then soonest.
     */
    @Transactional(readOnly = true)
    public List<EventReadinessView> upcoming(String siteCode, Instant from, Instant to, ActorContext actor,
            SourceChannel channel) {
        authorization.require(actor, SflPermission.FACILITIES_EVENT_READ, channel, "EventSetupTask", "list",
                siteCode);
        authorization.requireRequestedSite(actor, siteCode, channel, "EventSetupTask");
        String site = siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode);
        Instant start = from == null ? clock.instant() : from;
        Instant end = to == null ? start.plus(configuration.upcomingHorizon(site)) : to;
        if (!end.isAfter(start)) {
            throw new FacilitiesException.ValidationFailedException("'to' must be after 'from'.");
        }
        List<EventSetupTask> tasks = authorization.filterBySite(actor,
                repository.findTasksStartingBetween(site, start, end, 500), EventSetupTask::siteCode);
        return tasks.stream().map(this::view)
                .sorted(Comparator.comparing((EventReadinessView view) -> view.readiness().ordinal())
                        .thenComparing(view -> view.task().details().startsAt()))
                .toList();
    }

    /** The hand-off register for one event, accepted and rejected. */
    @Transactional(readOnly = true)
    public List<EventHandoff> handoffs(UUID id, ActorContext actor, SourceChannel channel) {
        return repository.findHandoffsForReference(findById(id, actor, channel).s078EventReference());
    }

    // =============================================================================================

    EventReadinessView view(EventSetupTask task) {
        List<EventResourceRequest> requests = repository.findRequestsForTask(task.id());
        RiskPosition risk = riskPosition(task, clock.instant());
        return new EventReadinessView(task, EventReadinessPolicy.rollUp(task.status(), requests),
                EventReadinessPolicy.summarise(requests), requests,
                requests.stream().filter(request -> request.status() == ResourceRequestStatus.CONFLICTED).toList(),
                requests.stream().filter(request -> request.status() == ResourceRequestStatus.MANUAL_COORDINATION)
                        .toList(),
                repository.findEscalationsForTask(task.id()), risk.triggers(),
                risk.higherRisk() ? risk.verdict().current() : null,
                risk.higherRisk() && !risk.verdict().current() ? risk.detail() : null);
    }

    RiskPosition riskPosition(EventSetupTask task, Instant at) {
        Set<EventRiskPolicy.Trigger> triggers = EventRiskPolicy.triggers(task.details(),
                configuration.riskCriteria(task.siteCode()));
        Optional<RiskAssessmentProjection> linked = task.hasLinkedRiskAssessment()
                ? repository.findRiskAssessment(task.riskAssessmentId(), task.riskAssessmentVersion())
                : Optional.empty();
        RiskAssessmentCurrency.Verdict verdict = RiskAssessmentCurrency.assess(
                linked.map(RiskAssessmentProjection::toSnapshot).orElse(null), at);
        String detail = verdict.current() ? null
                : EventRiskPolicy.explain(verdict.reason(), task.riskAssessmentId(),
                        task.hasLinkedRiskAssessment() && linked.isEmpty(), triggers);
        return new RiskPosition(triggers, verdict, detail);
    }

    private EventSetupTask requireTask(UUID id) {
        return repository.findTask(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException("Event set-up task", id));
    }
}
