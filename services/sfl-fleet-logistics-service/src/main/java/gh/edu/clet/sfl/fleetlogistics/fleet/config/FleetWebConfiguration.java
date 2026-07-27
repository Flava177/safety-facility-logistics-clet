package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the fleet console and the other SFL front ends. */
@Configuration(proxyBeanMethods = false)
class FleetWebConfiguration {

    @Bean
    WebMvcConfigurer fleetCorsConfigurer(
            @Value("${sfl.cors.allowed-origins:http://localhost:8091,http://localhost:8093,http://localhost:8094,"
                    + "http://localhost:5173,http://localhost:3000}") String allowedOrigins) {
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
                registry.addViewController("/fleet").setViewName("forward:/fleet/index.html");
                registry.addViewController("/fleet/").setViewName("forward:/fleet/index.html");
                registry.addViewController("/fuel").setViewName("forward:/fuel/index.html");
                registry.addViewController("/fuel/").setViewName("forward:/fuel/index.html");
                registry.addViewController("/dispatch").setViewName("forward:/dispatch/index.html");
                registry.addViewController("/dispatch/").setViewName("forward:/dispatch/index.html");
            }
        };
    }
}
