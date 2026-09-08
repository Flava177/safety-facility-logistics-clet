package gh.edu.clet.sfl.fleetlogistics.fuel.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.port.FuelRepository;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelApplicationService;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Idempotent reconciliation and SLA sweep. Stable anomaly keys prevent duplicate cases. */
@Component
@ConditionalOnProperty(
        name = "sfl.fuel.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class FuelSweepScheduler {
    private static final Logger log = LoggerFactory.getLogger(FuelSweepScheduler.class);
    private final FuelRepository repo;
    private final FuelApplicationService service;
    private final Clock clock;

    public FuelSweepScheduler(FuelRepository r, FuelApplicationService s, Clock c) {
        repo = r;
        service = s;
        clock = c;
    }

    /**
     * One sweep pass reads a bounded window per site; the next pass picks up whatever it did not
     * reach.
     */
    private static final int SWEEP_WINDOW = 100;

    @Scheduled(
            fixedDelayString = "${sfl.fuel.scheduling.fixed-delay:PT5M}",
            initialDelayString = "${sfl.fuel.scheduling.initial-delay:PT45S}")
    public void sweep() {
        for (String site : repo.activeSites()) {
            ActorContext actor = system(site);
            var received =
                    new FuelRepository.TransactionQuery(
                            List.of(site),
                            site,
                            FuelTransaction.Status.RECEIVED,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            new FuelRepository.Paging(0, SWEEP_WINDOW, "occurredAt,asc"));
            for (FuelTransaction t : repo.findTransactions(received).content())
                try {
                    service.reconcile(t.id(), actor, SourceChannel.SYSTEM);
                } catch (RuntimeException e) {
                    log.warn("Fuel reconciliation sweep could not process {}", t.id(), e);
                }
            for (UUID id : repo.findLateReceiptTransactionIds(site, SWEEP_WINDOW))
                try {
                    service.reconcile(id, actor, SourceChannel.SYSTEM);
                } catch (RuntimeException e) {
                    log.warn("Fuel receipt sweep could not process {}", id, e);
                }
            for (FuelRepository.MissingLogbookTrip row :
                    repo.findMissingLogbookTrips(site, SWEEP_WINDOW))
                try {
                    service.raiseMissingLogbook(
                            site,
                            row.tripId(),
                            row.vehicleId(),
                            row.driverId(),
                            actor,
                            SourceChannel.SYSTEM);
                } catch (RuntimeException e) {
                    log.warn("Fuel logbook sweep could not process trip {}", row.tripId(), e);
                }
            // Overdue and still open. `openOnly` replaces the status check this loop used to make
            // in Java, so a case that is already closed or cancelled is never fetched to be
            // skipped.
            var overdue =
                    new FuelRepository.AnomalyQuery(
                            List.of(site),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            Boolean.TRUE,
                            clock.instant(),
                            null,
                            null,
                            null,
                            new FuelRepository.Paging(0, SWEEP_WINDOW, "slaDueAt,asc"));
            for (FuelAnomalyCase a : repo.findAnomalies(overdue).content())
                if (a.status() != FuelAnomalyCase.Status.ESCALATED)
                    try {
                        service.transitionAnomaly(
                                a.id(),
                                "escalate",
                                "SLA threshold breached",
                                null,
                                actor,
                                SourceChannel.SYSTEM);
                    } catch (RuntimeException e) {
                        log.warn("Fuel SLA sweep could not escalate {}", a.id(), e);
                    }
        }
    }

    private ActorContext system(String site) {
        return new ActorContext(
                new SiteScopedPrincipal(
                        "fuel-scheduler",
                        "Fuel Scheduler",
                        Set.of(SflRole.SFL_ADMIN),
                        Set.of(site),
                        true),
                "fuel-sweep-" + clock.instant().toEpochMilli());
    }
}
