package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.scheduling;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.workflow.ComplianceServiceSweepService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drives compliance expiry and service due sweeps (SRS-SFL-S166-01/-02/-05). */
@Component
@ConditionalOnProperty(name = "sfl.fleet.scheduling.compliance.enabled", havingValue = "true",
        matchIfMissing = true)
class ComplianceServiceSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(ComplianceServiceSweepScheduler.class);

    private final ComplianceServiceSweepService sweepService;

    ComplianceServiceSweepScheduler(ComplianceServiceSweepService sweepService) {
        this.sweepService = sweepService;
    }

    @Scheduled(cron = "${sfl.fleet.scheduling.compliance.cron:0 5 1 * * *}")
    void evaluate() {
        try {
            ComplianceServiceSweepService.SweepResult result = sweepService.evaluateOnce();
            if (result.complianceStatusesUpdated() > 0 || result.serviceStatusesUpdated() > 0
                    || result.totalWorkflowsRaised() > 0) {
                log.info("Fleet compliance/service sweep completed: {}", result);
            }
        } catch (RuntimeException exception) {
            log.error("Fleet compliance/service sweep failed", exception);
        }
    }
}
