package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.scheduling;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow.SlaEvaluationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the SLA evaluation on a fixed delay (SRS-SFL-S166-02).
 *
 * <p>Split from the service so tests can call {@code evaluateOnce()} against a controlled clock, and so
 * the schedule can be turned off per environment without disabling the capability.
 */
@Component
@ConditionalOnProperty(name = "sfl.fleet.scheduling.sla.enabled", havingValue = "true", matchIfMissing = true)
class SlaEvaluationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaEvaluationScheduler.class);

    private final SlaEvaluationService slaEvaluation;

    SlaEvaluationScheduler(SlaEvaluationService slaEvaluation) {
        this.slaEvaluation = slaEvaluation;
    }

    @Scheduled(fixedDelayString = "${sfl.fleet.scheduling.sla.fixed-delay:PT1M}",
            initialDelayString = "${sfl.fleet.scheduling.sla.initial-delay:PT30S}")
    void evaluate() {
        try {
            int escalated = slaEvaluation.evaluateOnce().size();
            if (escalated > 0) {
                log.info("Escalated {} fleet workflow item(s) on SLA breach", escalated);
            }
        } catch (RuntimeException exception) {
            // The sweep must survive a bad pass; the next run picks the same items up again.
            log.error("Fleet SLA evaluation pass failed", exception);
        }
    }
}
