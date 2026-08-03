package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web wiring for the fleet service: CORS, and nothing else.
 *
 * <p>This service used to package and serve the SFL Operations dashboard from {@code /ui}, and
 * redirect {@code /}, {@code /fleet}, {@code /fuel} and {@code /dispatch} into it. None of that is
 * here any more. The backend serves its API and its API documentation; the user interface is a
 * separate application that talks to it over HTTP, and any interface can take that place.
 *
 * <p>The practical consequence is that <strong>CORS is now load-bearing rather than a development
 * convenience</strong>. When the dashboard was served from this origin the browser never had to be
 * persuaded to allow the calls. A UI on its own origin does, so {@code sfl.cors.allowed-origins}
 * decides which front ends can reach this service and has to be set for each deployment. The
 * defaults below cover the usual local development ports.
 *
 * <p>{@code X-Correlation-ID} is exposed deliberately. Without it a browser client reads {@code null}
 * cross-origin, and every error message loses the one identifier that ties it to a line in the
 * service log — the failure still happens, it just becomes untraceable from the UI side.
 */
@Configuration(proxyBeanMethods = false)
class FleetWebConfiguration {

    @Bean
    WebMvcConfigurer fleetCorsConfigurer(
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
                // Nothing is served at the root any more, so send a browser that lands here to the
                // API documentation rather than a 404 that says nothing about what this process is.
                registry.addRedirectViewController("/", "/swagger-ui.html");
            }
        };
    }
}
