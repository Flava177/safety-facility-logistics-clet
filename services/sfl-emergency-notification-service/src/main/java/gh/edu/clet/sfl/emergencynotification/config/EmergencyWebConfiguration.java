package gh.edu.clet.sfl.emergencynotification.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the emergency dashboard and the SFL front ends, and its view routes. */
@Configuration(proxyBeanMethods = false)
class EmergencyWebConfiguration {

    @Bean
    WebMvcConfigurer emergencyCorsConfigurer(
            // 5005 is the SFL Operations dashboards in development (npm run dev). The bundled build is
            // served by the fleet service on 8093, which is already allowed, so only the dev origin is
            // additional here.
            @Value("${sfl.cors.allowed-origins:http://localhost:8091,http://localhost:8093,http://localhost:8095,"
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
                registry.addViewController("/emergency").setViewName("forward:/emergency/index.html");
                registry.addViewController("/emergency/").setViewName("forward:/emergency/index.html");
            }
        };
    }
}
