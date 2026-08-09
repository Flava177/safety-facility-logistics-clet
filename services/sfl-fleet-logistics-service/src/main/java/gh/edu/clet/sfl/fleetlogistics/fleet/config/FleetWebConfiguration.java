package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web wiring for the fleet service: CORS, and the retired per-service pages.
 *
 * <p>This service used to package and serve the SFL Operations dashboards at {@code /ui}. It no
 * longer does. There is still exactly one bundle (ADR 0006 stands) but {@code sfl-portal-service}
 * owns it now, because hosting it here meant facilities and safety-security users reached their own
 * screens through a URL with fleet's name in it - one platform appearing to own the other two. Fleet
 * is an API again.
 *
 * <p>{@code /fleet}, {@code /fuel} and {@code /dispatch} still redirect, now to the portal. They are
 * absolute redirects because the portal is a different origin, and they are kept because bookmarks
 * outlive refactors.
 */
@Configuration(proxyBeanMethods = false)
class FleetWebConfiguration {

    @Bean
    WebMvcConfigurer fleetCorsConfigurer(
            @Value("${sfl.cors.allowed-origins:http://localhost:8090,"
                    + "http://localhost:8091,http://localhost:8092,http://localhost:8093,"
                    + "http://localhost:5005,http://localhost:5173,http://localhost:3000}") String allowedOrigins,
            @Value("${sfl.dashboard.base-url:http://localhost:${server.port:8093}/home}") String dashboardBaseUrl) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::strip)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        String dashboard = dashboardBaseUrl.replaceAll("/+$", "");

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
                // Retired by ADR 0006, rehomed by the portal split. Both spellings of each, because
                // a bookmark may carry either.
                retire(registry, "/fleet", dashboard + "/fleet");
                retire(registry, "/fuel", dashboard + "/fuel");
                retire(registry, "/dispatch", dashboard + "/dispatch");
                // No mapping for "/" here: SflDashboardAutoConfiguration already redirects it to
                // this service's own /home/, and two view controllers on the same path is a startup
                // failure, not a last-one-wins.
            }
        };
    }

    /**
     * Points a retired route at the portal route that replaced it.
     *
     * <p>A redirect rather than a forward, so the address bar ends up on the origin that is really
     * serving the page and a refresh does not land back here.
     */
    private static void retire(ViewControllerRegistry registry, String from, String to) {
        registry.addViewController(from).setViewName("redirect:" + to);
        registry.addViewController(from + "/").setViewName("redirect:" + to);
    }
}
