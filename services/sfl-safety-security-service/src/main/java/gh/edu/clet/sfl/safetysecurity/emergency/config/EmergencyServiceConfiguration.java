package gh.edu.clet.sfl.safetysecurity.emergency.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
class EmergencyServiceConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Without this, {@code @EnableScheduling} falls back to a single-thread pool shared by every
     * {@code @Scheduled} job in the service - the emergency sweep and the outbox drainer - so a slow
     * one serializes behind the other. Mirrors {@code FleetServiceConfiguration}'s reasoning and sizing
     * convention: headroom above the current (small) job count.
     */
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("sfl-safety-security-scheduler-");
        return scheduler;
    }
}
