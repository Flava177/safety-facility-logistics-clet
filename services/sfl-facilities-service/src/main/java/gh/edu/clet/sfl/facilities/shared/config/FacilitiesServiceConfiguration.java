package gh.edu.clet.sfl.facilities.shared.config;

import gh.edu.clet.sfl.facilities.shared.application.PlatformThreads;
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
        // Platform threads, so row-level security scopes the sweeps to '*' rather than to nothing.
        // See PlatformThreads for the defect this closes.
        scheduler.setThreadFactory(PlatformThreads.factory("sfl-facilities-scheduler-"));
        return scheduler;
    }

    /**
     * The broker listener's threads are platform threads too - {@code FacilitiesIntegrationListener}
     * is {@code @Transactional}, so the scope has to be on the thread before the transaction begins.
     *
     * <p>A post-processor rather than a replacement factory, so every setting Boot's configurer applied
     * from {@code spring.rabbitmq.listener.*} is kept and only the executor changes.
     */
    @Bean
    static org.springframework.beans.factory.config.BeanPostProcessor platformListenerThreads() {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory factory) {
                    factory.setTaskExecutor(new org.springframework.core.task.SimpleAsyncTaskExecutor(
                            PlatformThreads.factory("sfl-facilities-listener-")));
                }
                return bean;
            }
        };
    }
}