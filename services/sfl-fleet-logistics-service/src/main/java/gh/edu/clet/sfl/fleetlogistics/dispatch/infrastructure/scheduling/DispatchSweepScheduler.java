package gh.edu.clet.sfl.fleetlogistics.dispatch.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.port.DispatchRepository;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.CourierItemService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchDashboardService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchExceptionService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.application.service.DispatchReturnService;
import gh.edu.clet.sfl.fleetlogistics.dispatch.domain.model.DispatchExceptionCase;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Multi-execution-safe S171 sweeps: undelivered-inbound detection, outstanding-return escalation,
 * exception SLA escalation, dashboard snapshot refresh and stale-integration detection. Each action is
 * idempotent (stable occurrence keys, flag guards, snapshot upsert), so a re-run never duplicates
 * exception cases, notifications, audit entries or outbox messages. Every threshold is runtime-configurable.
 */
@Component
@ConditionalOnProperty(name = "sfl.dispatch.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class DispatchSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispatchSweepScheduler.class);
    private static final int BATCH = 100;

    private final DispatchRepository repository;
    private final CourierItemService items;
    private final DispatchReturnService returns;
    private final DispatchExceptionService exceptions;
    private final DispatchDashboardService dashboards;
    private final RuntimeConfigurationPort runtimeConfig;
    private final Clock clock;

    public DispatchSweepScheduler(DispatchRepository repository, CourierItemService items, DispatchReturnService returns,
            DispatchExceptionService exceptions, DispatchDashboardService dashboards,
            RuntimeConfigurationPort runtimeConfig, Clock clock) {
        this.repository = repository;
        this.items = items;
        this.returns = returns;
        this.exceptions = exceptions;
        this.dashboards = dashboards;
        this.runtimeConfig = runtimeConfig;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${sfl.dispatch.scheduling.fixed-delay:PT5M}",
            initialDelayString = "${sfl.dispatch.scheduling.initial-delay:PT50S}")
    public void sweep() {
        for (String site : repository.activeSites()) {
            ActorContext actor = system(site);
            if (enabled(site, "dispatch.scheduling.undelivered-enabled")) sweepUndelivered(site, actor);
            if (enabled(site, "dispatch.scheduling.outstanding-return-enabled")) sweepOutstandingReturns(site, actor);
            if (enabled(site, "dispatch.scheduling.sla-enabled")) sweepSla(site, actor);
            if (enabled(site, "dispatch.scheduling.dashboard-enabled")) refreshDashboard(site);
            if (enabled(site, "dispatch.scheduling.stale-integration-enabled")) detectStaleIntegration(actor);
        }
    }

    private void sweepUndelivered(String site, ActorContext actor) {
        var olderThan = clock.instant().minus(duration(site, "dispatch.undelivered.window", Duration.ofHours(48)));
        for (var id : repository.findUndeliveredInboundItemIds(site, olderThan, BATCH)) {
            try {
                items.flagUndelivered(id, "Undelivered/unclaimed beyond the configured window", actor,
                        SourceChannel.SCHEDULER);
            } catch (RuntimeException e) {
                log.warn("Undelivered sweep could not process item {}", id, e);
            }
        }
    }

    private void sweepOutstandingReturns(String site, ActorContext actor) {
        var olderThan = clock.instant().minus(duration(site, "dispatch.outstanding-return.window", Duration.ofDays(3)));
        for (var outstanding : repository.findOutstandingReturns(site, olderThan, BATCH)) {
            try {
                returns.escalateOutstanding(outstanding.dispatchId(), outstanding.manifestItemId(),
                        outstanding.courierItemId(), actor, SourceChannel.SCHEDULER);
            } catch (RuntimeException e) {
                log.warn("Outstanding-return sweep could not process manifest item {}", outstanding.manifestItemId(), e);
            }
        }
    }

    private void sweepSla(String site, ActorContext actor) {
        for (DispatchExceptionCase kase : repository.findExceptions(List.of(site), null, null, clock.instant(), BATCH)) {
            if (kase.status() == DispatchExceptionCase.Status.ESCALATED) continue;
            try {
                exceptions.escalateForSla(kase.id(), actor, SourceChannel.SCHEDULER);
            } catch (RuntimeException e) {
                log.warn("SLA sweep could not escalate exception {}", kase.id(), e);
            }
        }
    }

    private void refreshDashboard(String site) {
        try {
            dashboards.refreshSnapshot(site);
        } catch (RuntimeException e) {
            log.warn("Dashboard sweep could not refresh snapshot for {}", site, e);
        }
    }

    private void detectStaleIntegration(ActorContext actor) {
        try {
            var health = exceptions.integrationHealth(actor);
            if (health.deadLettered() > 0) {
                log.warn("Dispatch integration has {} dead-lettered outbound messages awaiting replay",
                        health.deadLettered());
            }
        } catch (RuntimeException e) {
            log.warn("Stale-integration sweep could not read outbox health", e);
        }
    }

    private boolean enabled(String site, String key) {
        return runtimeConfig.value(key, site).map(Boolean::parseBoolean).orElse(true);
    }

    private Duration duration(String site, String key, Duration fallback) {
        return runtimeConfig.value(key, site).map(Duration::parse).orElse(fallback);
    }

    private ActorContext system(String site) {
        return new ActorContext(new SiteScopedPrincipal("dispatch-scheduler", "Dispatch Scheduler",
                Set.of(SflRole.SFL_ADMIN), Set.of(site), true), "dispatch-sweep-" + clock.instant().toEpochMilli());
    }
}
