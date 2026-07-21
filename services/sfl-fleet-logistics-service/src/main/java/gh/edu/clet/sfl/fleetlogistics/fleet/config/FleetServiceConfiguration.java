package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

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
}
