package gh.edu.clet.sfl.emergencynotification.infrastructure.scheduling;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.emergencynotification.application.port.EmergencyRepository;
import gh.edu.clet.sfl.emergencynotification.application.port.OutboxAdminPort;
import gh.edu.clet.sfl.emergencynotification.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.emergencynotification.application.service.ActivationService;
import gh.edu.clet.sfl.emergencynotification.application.service.EmergencyDashboardService;
import gh.edu.clet.sfl.emergencynotification.domain.model.SourceChannel;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Multi-execution-safe S174 sweeps: unacknowledged-recipient SLA escalation, dashboard snapshot refresh
 * and stale-integration detection. Each action is idempotent (escalate is a no-op once ESCALATED, snapshot
 * upserts), so a re-run never duplicates escalations, audit or outbox rows. Thresholds are runtime-config.
 */
@Component
@ConditionalOnProperty(name = "sfl.emergency.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class EmergencySweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmergencySweepScheduler.class);
    private static final int BATCH = 100;

    private final EmergencyRepository repository;
    private final ActivationService activations;
    private final EmergencyDashboardService dashboards;
    private final OutboxAdminPort outboxAdmin;
    private final RuntimeConfigurationPort runtimeConfig;
    private final Clock clock;

    public EmergencySweepScheduler(EmergencyRepository repository, ActivationService activations,
            EmergencyDashboardService dashboards, OutboxAdminPort outboxAdmin, RuntimeConfigurationPort runtimeConfig,
            Clock clock) {
        this.repository = repository;
        this.activations = activations;
        this.dashboards = dashboards;
        this.outboxAdmin = outboxAdmin;
        this.runtimeConfig = runtimeConfig;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${sfl.emergency.scheduling.fixed-delay:PT1M}",
            initialDelayString = "${sfl.emergency.scheduling.initial-delay:PT30S}")
    public void sweep() {
        for (String site : repository.activeSites()) {
            if (runtimeConfig.flag("emergency.scheduling.escalation-enabled", site, true)) {
                escalateUnacknowledged(site);
            }
            if (runtimeConfig.flag("emergency.scheduling.dashboard-enabled", site, true)) {
                refreshDashboard(site);
            }
        }
        if (staleIntegrationEnabled()) {
            detectStaleIntegration();
        }
    }

    private void escalateUnacknowledged(String site) {
        Duration window = runtimeConfig.duration("emergency.ack.sla", site, Duration.ofMinutes(30));
        var olderThan = clock.instant().minus(window);
        ActorContext actor = system(site);
        for (UUID id : repository.findActivationsForAckEscalation(site, olderThan, BATCH)) {
            try {
                activations.escalateForSla(id, actor, SourceChannel.SCHEDULER);
            } catch (RuntimeException e) {
                log.warn("Ack-escalation sweep could not process activation {}", id, e);
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

    private void detectStaleIntegration() {
        try {
            OutboxAdminPort.OutboxHealth health = outboxAdmin.health();
            if (health.deadLettered() > 0) {
                log.warn("Emergency integration has {} dead-lettered outbound messages awaiting replay",
                        health.deadLettered());
            }
        } catch (RuntimeException e) {
            log.warn("Stale-integration sweep could not read outbox health", e);
        }
    }

    private boolean staleIntegrationEnabled() {
        return runtimeConfig.flag("emergency.scheduling.stale-integration-enabled", null, true);
    }

    private ActorContext system(String site) {
        return new ActorContext(new SiteScopedPrincipal("emergency-scheduler", "Emergency Scheduler",
                Set.of(SflRole.SFL_ADMIN), Set.of(site), true), "emergency-sweep-" + clock.instant().toEpochMilli());
    }
}
