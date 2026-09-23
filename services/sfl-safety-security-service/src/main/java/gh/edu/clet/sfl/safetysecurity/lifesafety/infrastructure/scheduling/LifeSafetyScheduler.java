package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.LifeSafetyRepository;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.service.DetectorCoverageService;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.service.InspectionComplianceService;
import java.time.Clock;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SRS-SFL-S162a-03/05: the per-site overdue-inspection and coverage-gap sweeps, mirroring {@code
 * EmergencySweepScheduler}'s shape - fan out over every site with a schedule/coverage record, under a
 * system actor, catching and logging per-site failures rather than letting one bad site stop the rest.
 */
@Component
@ConditionalOnProperty(name = "sfl.life-safety.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class LifeSafetyScheduler {

    private static final Logger log = LoggerFactory.getLogger(LifeSafetyScheduler.class);

    private final LifeSafetyRepository repository;
    private final InspectionComplianceService inspections;
    private final DetectorCoverageService coverage;
    private final Clock clock;

    public LifeSafetyScheduler(LifeSafetyRepository repository, InspectionComplianceService inspections,
            DetectorCoverageService coverage, Clock clock) {
        this.repository = repository;
        this.inspections = inspections;
        this.coverage = coverage;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${sfl.life-safety.scheduling.fixed-delay:PT1H}",
            initialDelayString = "${sfl.life-safety.scheduling.initial-delay:PT1M}")
    public void sweep() {
        for (String site : repository.activeSites()) {
            ActorContext actor = system(site);
            try {
                inspections.sweepOverdueInspections(site, actor);
            } catch (RuntimeException e) {
                log.warn("Inspection sweep could not process site {}", site, e);
            }
            try {
                coverage.sweepCoverageGaps(site, actor);
            } catch (RuntimeException e) {
                log.warn("Coverage sweep could not process site {}", site, e);
            }
        }
    }

    private ActorContext system(String site) {
        return new ActorContext(new SiteScopedPrincipal("lifesafety-scheduler", "Life-Safety Scheduler",
                Set.of(SflRole.SFL_ADMIN), Set.of(site), true), "lifesafety-sweep-" + clock.instant().toEpochMilli());
    }
}
