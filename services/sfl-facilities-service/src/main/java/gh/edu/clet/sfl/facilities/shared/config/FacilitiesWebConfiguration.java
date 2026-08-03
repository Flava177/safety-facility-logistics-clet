package gh.edu.clet.sfl.facilities.shared.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the facilities API.
 *
 * <p>{@code /} used to serve this service's own page, and later redirected into the SFL Operations
 * dashboard once S152 had screens there. Neither applies now: no service in this repository serves a
 * user interface, so there is nothing local to show and nothing central to point at. The root
 * redirects to the API documentation instead.
 *
 * <p>That makes CORS the whole contract with whatever front end is plugged in. The origins below are
 * the usual local development ports; {@code sfl.cors.allowed-origins} replaces them per deployment.
 */
@Configuration(proxyBeanMethods = false)
class FacilitiesWebConfiguration {

    @Bean
    WebMvcConfigurer facilitiesCorsConfigurer(
            @Value("${sfl.cors.allowed-origins:http://localhost:5005,http://localhost:5173,"
                    + "http://localhost:3000,http://localhost:4200,http://localhost:8080}")
            String allowedOrigins) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::strip)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);

        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        // `X-Correlation-ID` has to be exposed or the browser cannot read it: the
                        // client puts it on every error it raises, and cross-origin that read
                        // silently returns null. The correlation ID would then be present in the
                        // service log and absent from the message the operator is looking at —
                        // which is the one moment it exists to be useful.
                        .exposedHeaders("Location", "X-Correlation-ID");
                registry.addMapping("/actuator/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "OPTIONS")
                        .allowedHeaders("*");
            }

            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                registry.addRedirectViewController("/", "/swagger-ui.html");
            }
        };
    }
}
