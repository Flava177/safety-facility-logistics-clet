package gh.edu.clet.sfl.safetysecurity.emergency.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the SFL front ends, and the retirement of S174's own page.
 *
 * <p>{@code /emergency} used to serve a page of its own. ADR 0006 retired it in favour of the
 * dashboard's emergency screens, which are served by the fleet service and call this one across
 * origins - so unlike the fleet redirects, the target here is on another host and has to be
 * configured. {@code sfl.dashboard.base-url} is that configuration; behind a gateway it becomes a
 * same-origin path prefix and nothing else changes.
 *
 * <p>The allowed-origin default lost {@code :8095} and gained {@code :8092} when S174 was folded into
 * this deployable. That is not cosmetic - a stale origin list is invisible to curl and fatal in a
 * browser, which is exactly how the facilities service shipped a CORS list that allowed neither
 * front end.
 */
@Configuration(proxyBeanMethods = false)
class EmergencyWebConfiguration {

    @Bean
    WebMvcConfigurer emergencyCorsConfigurer(
            // Where the SFL Operations dashboard is served. Only the fleet service packages the
            // bundle, so this is a cross-origin address in development rather than a local path.
            @Value("${sfl.dashboard.base-url:http://localhost:8093/ui}") String dashboardBaseUrl,
            // 5005 is the SFL Operations dashboards in development (npm run dev). The bundled build is
            // served by the fleet service on 8093, which is already allowed, so only the dev origin is
            // additional here.
            @Value("${sfl.cors.allowed-origins:http://localhost:8091,http://localhost:8092,http://localhost:8093,"
                    + "http://localhost:5005,http://localhost:5173,http://localhost:3000}") String allowedOrigins) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::strip)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PATCH", "PUT", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Location", "X-Correlation-ID");
                registry.addMapping("/actuator/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "OPTIONS")
                        .allowedHeaders("*");
            }

            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                // Retired by ADR 0006. `/emergency/index.html` stays as a notice page and refreshes
                // back to `/emergency`, so the redirect target is configured in exactly one place.
                String target = dashboardBaseUrl.replaceAll("/+$", "") + "/emergency";
                registry.addViewController("/emergency").setViewName("redirect:" + target);
                registry.addViewController("/emergency/").setViewName("redirect:" + target);
            }
        };
    }
}
