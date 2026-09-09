package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Core fleet beans.
 *
 * <p>The clock is injected everywhere rather than read statically, so SLA evaluation, expiry sweeps and
 * every timestamp in the audit trail can be driven deterministically from tests.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class FleetServiceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Without this, {@code @EnableScheduling} falls back to a single-thread pool shared by every
     * {@code @Scheduled} job in the module - outbox drain, SLA escalation, compliance sweep, dashboard
     * refresh, the fuel sweep and the dispatch sweep - so a slow one serializes behind whichever job
     * happened to start first. SLA escalation is safety/compliance-relevant; it should never be stuck
     * waiting on a dashboard refresh. Sized with headroom above the module's current scheduled-job
     * count so a new job does not silently reintroduce the same contention.
     */
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("sfl-fleet-scheduler-");
        return scheduler;
    }
}
