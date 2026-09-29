package gh.edu.clet.sfl.facilities.eventlogistics.application;

import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventResourceRequest;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.EventSetupTask;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessEscalation;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ReadinessStatus;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.policy.EventReadinessPolicy;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.policy.EventRiskPolicy;
import java.util.List;
import java.util.Set;

/**
 * One event's consolidated readiness - SRS-SFL-S173-02: "Each resource request's status ... rolls up to
 * one consolidated event-readiness view", "so that one coordinator sees the whole picture instead of
 * chasing four separate systems".
 *
 * @param riskTriggers why the event is higher-risk; empty for a routine event
 * @param riskAssessmentCurrent whether the linked assessment passes the shared currency rule now;
 *        {@code null} for a routine event, where the question does not arise
 * @param riskAssessmentDetail the refusal sentence a confirmation would receive, when not current
 */
public record EventReadinessView(
        EventSetupTask task,
        ReadinessStatus readiness,
        EventReadinessPolicy.Summary summary,
        List<EventResourceRequest> requests,
        List<EventResourceRequest> conflicts,
        List<EventResourceRequest> manualCoordination,
        List<ReadinessEscalation> escalations,
        Set<EventRiskPolicy.Trigger> riskTriggers,
        Boolean riskAssessmentCurrent,
        String riskAssessmentDetail) {
}
