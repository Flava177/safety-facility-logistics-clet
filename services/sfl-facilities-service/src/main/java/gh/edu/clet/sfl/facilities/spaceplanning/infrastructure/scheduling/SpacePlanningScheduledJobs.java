package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.spaceplanning.application.SpacePlanningCommands;
import gh.edu.clet.sfl.facilities.spaceplanning.application.UtilisationReconciliationService;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The S158-03 utilisation pull, on a timer: "S158 pulls utilisation snapshots from S159 ... on a
 * configured schedule."
 *
 * <p>Thin, like {@code BookingScheduledJobs}: a system actor, one call per site, log what moved. Each site
 * runs in its own transaction (the service's), so one site's failure - S159 unreadable, a bad row - does
 * not stop the others, and is retried on the next run.
 *
 * <h2>Why hourly for a weekly report</h2>
 *
 * The run always evaluates the latest <em>complete</em> period and is idempotent on it (one snapshot per
 * room per period, signals raised once). Running hourly means a period is picked up within the hour of
 * ending, and that bookings completed or swept as no-shows in the hours after it ended are folded in on
 * the next run - rather than frozen at whatever they were at one fixed moment.
 */
@Component
@ConditionalOnProperty(name = "sfl.space-planning.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SpacePlanningScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(SpacePlanningScheduledJobs.class);

    /** A service account with {@code *} scope, so an audit reader can tell the sweep from a person. */
    private static final SiteScopedPrincipal SYSTEM = new SiteScopedPrincipal("system.space-planning",
            "Space planning scheduler", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final UtilisationReconciliationService reconciliation;
    private final FacilitiesRepository facilities;

    public SpacePlanningScheduledJobs(UtilisationReconciliationService reconciliation, FacilitiesRepository facilities) {
        this.reconciliation = reconciliation;
        this.facilities = facilities;
    }

    @Scheduled(fixedDelayString = "${sfl.space-planning.utilisation.interval-ms:3600000}",
            initialDelayString = "${sfl.space-planning.utilisation.initial-delay-ms:600000}")
    public void pullUtilisation() {
        for (Site site : facilities.findSites()) {
            if (!site.lifecycleStatus().isOperational()) {
                continue;
            }
            try {
                UtilisationReconciliationService.RunSummary run = reconciliation.reconcile(
                        new SpacePlanningCommands.RunReconciliation(site.siteCode(), null,
                                new ActorContext(SYSTEM, UUID.randomUUID().toString()), SourceChannel.SCHEDULER));
                if (run.signalsRaised() > 0 || run.signalsCleared() > 0) {
                    log.info("Space utilisation {} {}..{}: {} room(s), {} signal(s) raised, {} cleared, {} active",
                            run.siteCode(), run.periodStart(), run.periodEnd(), run.roomsSnapshotted(),
                            run.signalsRaised(), run.signalsCleared(), run.activeSignals());
                }
            } catch (RuntimeException failure) {
                // Swallowed on purpose: an uncaught exception cancels a fixedDelay schedule for the life of
                // the process, and one unreadable site would silently stop every other site's reconciliation.
                log.error("Space utilisation reconciliation failed for {}; retried on the next run",
                        site.siteCode(), failure);
            }
        }
    }
}
