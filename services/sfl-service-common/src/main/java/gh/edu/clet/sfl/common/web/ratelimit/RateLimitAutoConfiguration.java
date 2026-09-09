package gh.edu.clet.sfl.common.web.ratelimit;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link RateLimitFilter} when {@code sfl.rate-limit.enabled=true}.
 *
 * <p><strong>Off by default.</strong> Every service in the estate shares one Spring context per test
 * run, and the estate's test suites drive the same permitAll paths (system info, reports) far more
 * than {@code sfl.rate-limit}'s default budget would allow within a single test run's wall-clock
 * window if this were on unconditionally. A production profile turns it on explicitly; the platform
 * carries no separate profile split today; see the security audit that raised this finding for the
 * follow-up that should close that gap.
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "sfl.rate-limit.enabled", havingValue = "true")
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties properties) {
        LoggerFactory.getLogger(getClass()).info(
                "sfl.rate-limit.enabled=true: throttling {} req/{}s per remote address on {} (excluding {})",
                properties.getRequestsPerWindow(), properties.getWindowSeconds(), properties.getPathPatterns(),
                properties.getExcludePathPatterns());
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(properties));
        registration.setOrder(-100);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
