package gh.edu.clet.sfl.facilities.eventlogistics.application.ports;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventHandoff;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventReconciliationLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceType;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventTemplateLine;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessEscalation;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.RiskAssessmentProjection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * S173's persistence, one port for the module's seven tables (V20).
 *
 * <p>Site filtering is done in SQL on every list query (the {@code siteCode} parameters) and again by
 * the services through {@code FacilitiesAuthorization.filterBySite}, and under {@code sfl_app} by RLS.
 * Three layers for one rule is the lesson ADR 0007 recorded, not an accident.
 */
public interface EventLogisticsRepository {

    // ---- set-up tasks ---------------------------------------------------------------------------

    EventSetupTask saveTask(EventSetupTask task);

    Optional<EventSetupTask> findTask(UUID id);

    Optional<EventSetupTask> findTaskByS078Reference(String s078EventReference);

    /**
     * Tasks whose event starts in {@code [from, to)}, soonest first.
     *
     * @param siteCode {@code null} for every site the caller may see - narrowed again by the service
     */
    List<EventSetupTask> findTasksStartingBetween(String siteCode, Instant from, Instant to, int limit);

    /** Live tasks whose event starts before {@code startsBefore} and has not ended by {@code endsAfter}. */
    List<EventSetupTask> findLiveTasksForEscalation(Instant startsBefore, Instant endsAfter, int limit);

    /** {@code EV-MAIN-000123}. */
    String nextTaskReference(String siteCode);

    // ---- resource requests ----------------------------------------------------------------------

    EventResourceRequest saveRequest(EventResourceRequest request);

    Optional<EventResourceRequest> findRequest(UUID id);

    List<EventResourceRequest> findRequestsForTask(UUID setupTaskId);

    /** Requests whose own or parent external reference is this S159 booking. */
    List<EventResourceRequest> findRequestsForBooking(String bookingId);

    /** Live requests holding an owning-system commitment, for the status synchronisation sweep. */
    List<EventResourceRequest> findRoutedLiveRequests(int limit);

    // ---- hand-off register ----------------------------------------------------------------------

    EventHandoff saveHandoff(EventHandoff handoff);

    Optional<EventHandoff> findAcceptedHandoff(String sourceSystem, String idempotencyKey);

    List<EventHandoff> findHandoffsForReference(String s078EventReference);

    // ---- escalations, reconciliation, templates ------------------------------------------------

    ReadinessEscalation saveEscalation(ReadinessEscalation escalation);

    List<ReadinessEscalation> findEscalationsForTask(UUID setupTaskId);

    EventReconciliationLine saveReconciliation(EventReconciliationLine line);

    List<EventReconciliationLine> findReconciliationForTask(UUID setupTaskId);

    EventTemplateLine saveTemplateLine(EventTemplateLine line);

    Optional<EventTemplateLine> findTemplateLine(String siteCode, String eventCategory, EventResourceType type);

    /** @param eventCategory {@code null} for every category at the site */
    List<EventTemplateLine> findTemplateLines(String siteCode, String eventCategory);

    // ---- S165 projection ------------------------------------------------------------------------

    RiskAssessmentProjection saveRiskAssessment(RiskAssessmentProjection projection);

    Optional<RiskAssessmentProjection> findRiskAssessment(String assessmentId, int version);

    List<RiskAssessmentProjection> findRiskAssessmentVersions(String assessmentId);
}
