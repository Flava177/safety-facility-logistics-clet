package gh.edu.clet.sfl.facilities.support;

import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.EventLogisticsRepository;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventReconciliationLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTaskStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventTemplateLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessEscalation;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.RiskAssessmentProjection;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An in-memory {@link EventLogisticsRepository} for the S173 application tests.
 *
 * <p>Reproduces the module's own uniqueness rules where the SRS puts weight on them - one set-up task
 * per S078 reference, one accepted hand-off per source/idempotency key - by throwing the same exception
 * the JPA adapter's flush would, so a test written against this double is exercising the rule the real
 * adapter enforces, not a rule this double invented. It does not reproduce the {@code GIST}-style
 * concurrency this module does not have: nothing here races.
 */
public class InMemoryEventLogisticsRepository implements EventLogisticsRepository {

    private final Map<UUID, EventSetupTask> tasks = new LinkedHashMap<>();
    private final Map<UUID, EventResourceRequest> requests = new LinkedHashMap<>();
    private final Map<UUID, EventHandoff> handoffs = new LinkedHashMap<>();
    private final Map<UUID, ReadinessEscalation> escalations = new LinkedHashMap<>();
    private final Map<UUID, EventReconciliationLine> reconciliations = new LinkedHashMap<>();
    private final Map<UUID, EventTemplateLine> templates = new LinkedHashMap<>();
    private final Map<String, RiskAssessmentProjection> riskAssessments = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    // ---- tasks ----------------------------------------------------------------------------------

    @Override
    public EventSetupTask saveTask(EventSetupTask task) {
        boolean clashes = tasks.values().stream()
                .anyMatch(existing -> !existing.id().equals(task.id())
                        && existing.s078EventReference().equals(task.s078EventReference()));
        if (clashes) {
            throw new FacilitiesException.DuplicateIdentifierException("event set-up task",
                    task.s078EventReference(), task.siteCode());
        }
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<EventSetupTask> findTask(UUID id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public Optional<EventSetupTask> findTaskByS078Reference(String s078EventReference) {
        return tasks.values().stream().filter(task -> task.s078EventReference().equals(s078EventReference))
                .findFirst();
    }

    @Override
    public List<EventSetupTask> findTasksStartingBetween(String siteCode, Instant from, Instant to, int limit) {
        return tasks.values().stream()
                .filter(task -> siteCode == null || task.siteCode().equals(siteCode))
                .filter(task -> !task.details().startsAt().isBefore(from) && task.details().startsAt().isBefore(to))
                .sorted(Comparator.comparing(task -> task.details().startsAt()))
                .limit(limit)
                .toList();
    }

    @Override
    public List<EventSetupTask> findLiveTasksForEscalation(Instant startsBefore, Instant endsAfter, int limit) {
        return tasks.values().stream()
                .filter(task -> task.status() == EventSetupTaskStatus.OPEN
                        || task.status() == EventSetupTaskStatus.CONFIRMED)
                .filter(task -> task.details().startsAt().isBefore(startsBefore)
                        && task.details().endsAt().isAfter(endsAfter))
                .sorted(Comparator.comparing(task -> task.details().startsAt()))
                .limit(limit)
                .toList();
    }

    @Override
    public String nextTaskReference(String siteCode) {
        return "EV-" + siteCode.toUpperCase(Locale.ROOT) + "-" + String.format("%06d", sequence.incrementAndGet());
    }

    // ---- requests -------------------------------------------------------------------------------

    @Override
    public EventResourceRequest saveRequest(EventResourceRequest request) {
        requests.put(request.id(), request);
        return request;
    }

    @Override
    public Optional<EventResourceRequest> findRequest(UUID id) {
        return Optional.ofNullable(requests.get(id));
    }

    @Override
    public List<EventResourceRequest> findRequestsForTask(UUID setupTaskId) {
        return requests.values().stream().filter(request -> request.setupTaskId().equals(setupTaskId)).toList();
    }

    @Override
    public List<EventResourceRequest> findRequestsForBooking(String bookingId) {
        return requests.values().stream()
                .filter(request -> bookingId.equals(request.externalReference())
                        || bookingId.equals(request.externalParentReference()))
                .toList();
    }

    @Override
    public List<EventResourceRequest> findRoutedLiveRequests(int limit) {
        return requests.values().stream()
                .filter(request -> request.externalReference() != null
                        && (request.status() == ResourceRequestStatus.REQUESTED
                                || request.status() == ResourceRequestStatus.CONFIRMED))
                .limit(limit)
                .toList();
    }

    // ---- hand-off register ----------------------------------------------------------------------

    @Override
    public EventHandoff saveHandoff(EventHandoff handoff) {
        if (handoff.outcome() == EventHandoff.Outcome.ACCEPTED) {
            boolean duplicate = handoffs.values().stream()
                    .anyMatch(existing -> !existing.id().equals(handoff.id())
                            && existing.outcome() == EventHandoff.Outcome.ACCEPTED
                            && existing.sourceSystem().equals(handoff.sourceSystem())
                            && existing.idempotencyKey().equals(handoff.idempotencyKey()));
            if (duplicate) {
                throw new FacilitiesException.DuplicateIdentifierException("event hand-off",
                        handoff.sourceSystem() + "/" + handoff.idempotencyKey(), handoff.siteCode());
            }
        }
        handoffs.put(handoff.id(), handoff);
        return handoff;
    }

    @Override
    public Optional<EventHandoff> findAcceptedHandoff(String sourceSystem, String idempotencyKey) {
        return handoffs.values().stream()
                .filter(handoff -> handoff.outcome() == EventHandoff.Outcome.ACCEPTED
                        && handoff.sourceSystem().equals(sourceSystem)
                        && handoff.idempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    @Override
    public List<EventHandoff> findHandoffsForReference(String s078EventReference) {
        return handoffs.values().stream().filter(handoff -> handoff.s078EventReference().equals(s078EventReference))
                .sorted(Comparator.comparing(EventHandoff::receivedAt))
                .toList();
    }

    // ---- escalations, reconciliation, templates ------------------------------------------------

    @Override
    public ReadinessEscalation saveEscalation(ReadinessEscalation escalation) {
        escalations.put(escalation.id(), escalation);
        return escalation;
    }

    @Override
    public List<ReadinessEscalation> findEscalationsForTask(UUID setupTaskId) {
        return escalations.values().stream().filter(escalation -> escalation.setupTaskId().equals(setupTaskId))
                .toList();
    }

    @Override
    public EventReconciliationLine saveReconciliation(EventReconciliationLine line) {
        reconciliations.put(line.id(), line);
        return line;
    }

    @Override
    public List<EventReconciliationLine> findReconciliationForTask(UUID setupTaskId) {
        return reconciliations.values().stream().filter(line -> line.setupTaskId().equals(setupTaskId)).toList();
    }

    @Override
    public EventTemplateLine saveTemplateLine(EventTemplateLine line) {
        templates.put(line.id(), line);
        return line;
    }

    @Override
    public Optional<EventTemplateLine> findTemplateLine(String siteCode, String eventCategory,
            EventResourceType type) {
        return templates.values().stream()
                .filter(line -> line.siteCode().equals(siteCode) && line.eventCategory().equals(eventCategory)
                        && line.resourceType() == type)
                .findFirst();
    }

    @Override
    public List<EventTemplateLine> findTemplateLines(String siteCode, String eventCategory) {
        return templates.values().stream()
                .filter(line -> line.siteCode().equals(siteCode))
                .filter(line -> eventCategory == null || line.eventCategory().equals(eventCategory))
                .toList();
    }

    // ---- S165 projection ------------------------------------------------------------------------

    @Override
    public RiskAssessmentProjection saveRiskAssessment(RiskAssessmentProjection projection) {
        riskAssessments.put(key(projection.assessmentId(), projection.version()), projection);
        return projection;
    }

    @Override
    public Optional<RiskAssessmentProjection> findRiskAssessment(String assessmentId, int version) {
        return Optional.ofNullable(riskAssessments.get(key(assessmentId, version)));
    }

    @Override
    public List<RiskAssessmentProjection> findRiskAssessmentVersions(String assessmentId) {
        List<RiskAssessmentProjection> found = new ArrayList<>();
        riskAssessments.values().forEach(projection -> {
            if (projection.assessmentId().equals(assessmentId)) {
                found.add(projection);
            }
        });
        return found;
    }

    private static String key(String assessmentId, int version) {
        return assessmentId + "@" + version;
    }
}
