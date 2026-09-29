package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.EventLogisticsRepository;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventReconciliationLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventTemplateLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessEscalation;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.RiskAssessmentProjection;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * The one adapter behind {@link EventLogisticsRepository}.
 *
 * <p>The task save flushes, so the unique S078 reference in V20 - the database half of "one set-up task
 * per S078 event" - fails inside this method with a readable error rather than at commit as an opaque
 * one. Two hand-offs for a new event racing each other are the case it exists for: the loser is
 * refused and S078's retry then finds the task and updates it.
 */
@Repository
public class JpaEventLogisticsRepositoryAdapter implements EventLogisticsRepository {

    private final JpaEventSetupTaskRepository tasks;
    private final JpaEventResourceRequestRepository requests;
    private final JpaEventHandoffRepository handoffs;
    private final JpaEventEscalationRepository escalations;
    private final JpaEventReconciliationRepository reconciliation;
    private final JpaEventTemplateLineRepository templates;
    private final JpaEventRiskAssessmentRepository assessments;

    public JpaEventLogisticsRepositoryAdapter(JpaEventSetupTaskRepository tasks,
            JpaEventResourceRequestRepository requests, JpaEventHandoffRepository handoffs,
            JpaEventEscalationRepository escalations, JpaEventReconciliationRepository reconciliation,
            JpaEventTemplateLineRepository templates, JpaEventRiskAssessmentRepository assessments) {
        this.tasks = tasks;
        this.requests = requests;
        this.handoffs = handoffs;
        this.escalations = escalations;
        this.reconciliation = reconciliation;
        this.templates = templates;
        this.assessments = assessments;
    }

    // ---- tasks ----------------------------------------------------------------------------------

    @Override
    public EventSetupTask saveTask(EventSetupTask task) {
        Optional<EventSetupTaskRecord> existing = tasks.findById(task.id());
        existing.ifPresent(record -> record.requireNotStale(task.metadata().version()));
        EventSetupTaskRecord record = existing.orElseGet(EventSetupTaskRecord::empty);
        record.apply(task);
        try {
            return tasks.saveAndFlush(record).toDomain();
        } catch (DataIntegrityViolationException duplicate) {
            throw new FacilitiesException.DuplicateIdentifierException("event set-up task", task.s078EventReference(),
                    task.siteCode());
        }
    }

    @Override
    public Optional<EventSetupTask> findTask(UUID id) {
        return tasks.findById(id).map(EventSetupTaskRecord::toDomain);
    }

    @Override
    public Optional<EventSetupTask> findTaskByS078Reference(String s078EventReference) {
        return tasks.findByReference(s078EventReference).map(EventSetupTaskRecord::toDomain);
    }

    @Override
    public List<EventSetupTask> findTasksStartingBetween(String siteCode, Instant from, Instant to, int limit) {
        return tasks.findStartingBetween(siteCode, from, to, PageRequest.of(0, Math.max(1, limit))).stream()
                .map(EventSetupTaskRecord::toDomain).toList();
    }

    @Override
    public List<EventSetupTask> findLiveTasksForEscalation(Instant startsBefore, Instant endsAfter, int limit) {
        return tasks.findLiveForEscalation(startsBefore, endsAfter, PageRequest.of(0, Math.max(1, limit))).stream()
                .map(EventSetupTaskRecord::toDomain).toList();
    }

    @Override
    public String nextTaskReference(String siteCode) {
        return "EV-" + siteCode.strip().toUpperCase(Locale.ROOT) + "-"
                + String.format("%06d", tasks.nextTaskSequence());
    }

    // ---- requests -------------------------------------------------------------------------------

    @Override
    public EventResourceRequest saveRequest(EventResourceRequest request) {
        Optional<EventResourceRequestRecord> existing = requests.findById(request.id());
        existing.ifPresent(record -> record.requireNotStale(request.metadata().version()));
        EventResourceRequestRecord record = existing.orElseGet(EventResourceRequestRecord::empty);
        record.apply(request);
        return requests.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<EventResourceRequest> findRequest(UUID id) {
        return requests.findById(id).map(EventResourceRequestRecord::toDomain);
    }

    @Override
    public List<EventResourceRequest> findRequestsForTask(UUID setupTaskId) {
        return requests.findForTask(setupTaskId).stream().map(EventResourceRequestRecord::toDomain).toList();
    }

    @Override
    public List<EventResourceRequest> findRequestsForBooking(String bookingId) {
        return requests.findForBooking(bookingId).stream().map(EventResourceRequestRecord::toDomain).toList();
    }

    @Override
    public List<EventResourceRequest> findRoutedLiveRequests(int limit) {
        return requests.findRoutedLive(PageRequest.of(0, Math.max(1, limit))).stream()
                .map(EventResourceRequestRecord::toDomain).toList();
    }

    // ---- hand-offs ------------------------------------------------------------------------------

    @Override
    public EventHandoff saveHandoff(EventHandoff handoff) {
        return handoffs.saveAndFlush(EventHandoffRecord.from(handoff)).toDomain();
    }

    @Override
    public Optional<EventHandoff> findAcceptedHandoff(String sourceSystem, String idempotencyKey) {
        return handoffs.findAccepted(sourceSystem, idempotencyKey).map(EventHandoffRecord::toDomain);
    }

    @Override
    public List<EventHandoff> findHandoffsForReference(String s078EventReference) {
        return handoffs.findForReference(s078EventReference).stream().map(EventHandoffRecord::toDomain).toList();
    }

    // ---- escalations, reconciliation, templates ------------------------------------------------

    @Override
    public ReadinessEscalation saveEscalation(ReadinessEscalation escalation) {
        return escalations.saveAndFlush(EventReadinessEscalationRecord.from(escalation)).toDomain();
    }

    @Override
    public List<ReadinessEscalation> findEscalationsForTask(UUID setupTaskId) {
        return escalations.findForTask(setupTaskId).stream().map(EventReadinessEscalationRecord::toDomain).toList();
    }

    @Override
    public EventReconciliationLine saveReconciliation(EventReconciliationLine line) {
        return reconciliation.saveAndFlush(EventReconciliationLineRecord.from(line)).toDomain();
    }

    @Override
    public List<EventReconciliationLine> findReconciliationForTask(UUID setupTaskId) {
        return reconciliation.findForTask(setupTaskId).stream().map(EventReconciliationLineRecord::toDomain).toList();
    }

    @Override
    public EventTemplateLine saveTemplateLine(EventTemplateLine line) {
        Optional<EventTemplateLineRecord> existing = templates.findById(line.id());
        existing.ifPresent(record -> record.requireNotStale(line.metadata().version()));
        EventTemplateLineRecord record = existing.orElseGet(EventTemplateLineRecord::empty);
        record.apply(line);
        return templates.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<EventTemplateLine> findTemplateLine(String siteCode, String eventCategory, EventResourceType type) {
        return templates.findLine(siteCode, eventCategory, type).map(EventTemplateLineRecord::toDomain);
    }

    @Override
    public List<EventTemplateLine> findTemplateLines(String siteCode, String eventCategory) {
        return templates.findLines(siteCode, eventCategory).stream().map(EventTemplateLineRecord::toDomain).toList();
    }

    // ---- S165 projection ------------------------------------------------------------------------

    @Override
    public RiskAssessmentProjection saveRiskAssessment(RiskAssessmentProjection projection) {
        EventRiskAssessmentRecord record = assessments.findById(
                new EventRiskAssessmentRecord.Key(projection.assessmentId(), projection.version()))
                .orElseGet(EventRiskAssessmentRecord::empty);
        record.apply(projection);
        return assessments.saveAndFlush(record).toDomain();
    }

    @Override
    public Optional<RiskAssessmentProjection> findRiskAssessment(String assessmentId, int version) {
        return assessments.findById(new EventRiskAssessmentRecord.Key(assessmentId, version))
                .map(EventRiskAssessmentRecord::toDomain);
    }

    @Override
    public List<RiskAssessmentProjection> findRiskAssessmentVersions(String assessmentId) {
        return assessments.findVersions(assessmentId).stream().map(EventRiskAssessmentRecord::toDomain).toList();
    }
}
