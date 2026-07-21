package gh.edu.clet.sfl.facilities.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FacilitiesServiceConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}