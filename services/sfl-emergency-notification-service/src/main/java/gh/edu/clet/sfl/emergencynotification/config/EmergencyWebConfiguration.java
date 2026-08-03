package gh.edu.clet.sfl.emergencynotification.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for whatever front end is plugged into the emergency API.
 *
 * <p>{@code /emergency} used to serve a page of its own, and then redirected into the SFL Operations
 * dashboard's emergency screens. Neither exists here now — no service in this repository serves a
 * user interface — so the root redirects to the API documentation and the screens are somebody
 * else's application.
 *
 * <p>S174 is the one service where this matters most. It is a separate deployable precisely so it
 * keeps working when other things do not, and an emergency console that cannot reach it because of
 * an unlisted origin is a failure at the worst possible moment. Set
 * {@code sfl.cors.allowed-origins} explicitly for every deployment rather than relying on the local
 * development defaults below.
 */
@Configuration(proxyBeanMethods = false)
class EmergencyWebConfiguration {

    @Bean
    WebMvcConfigurer emergencyCorsConfigurer(
            @Value("${sfl.cors.allowed-origins:http://localhost:5005,http://localhost:5173,"
                    + "http://localhost:3000,http://localhost:4200,http://localhost:8080}") String allowedOrigins) {
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
