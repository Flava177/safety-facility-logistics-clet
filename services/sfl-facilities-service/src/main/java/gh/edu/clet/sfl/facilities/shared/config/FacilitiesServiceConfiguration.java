package gh.edu.clet.sfl.facilities.shared.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
class FacilitiesServiceConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Without this, {@code @EnableScheduling} falls back to a single-thread pool shared by every
     * {@code @Scheduled} job in the service - S153 escalation, S153 preventive generation, S159
     * readiness reconciliation, S159 no-shows, and the IFIMP outbox drainer - so a slow one serializes
     * behind whichever job happened to start first. Mirrors {@code FleetServiceConfiguration}'s
     * identical reasoning and sizing convention: headroom above the current job count so a new job
     * does not silently reintroduce the same contention.
     */
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("sfl-facilities-scheduler-");
        return scheduler;
    }
}