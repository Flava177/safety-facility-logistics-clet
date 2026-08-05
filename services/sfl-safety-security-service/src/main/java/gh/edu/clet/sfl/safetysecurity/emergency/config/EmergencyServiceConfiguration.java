package gh.edu.clet.sfl.safetysecurity.emergency.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class EmergencyServiceConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
