package gh.edu.clet.sfl.facilities.shared.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the facilities API, and the retirement of this service's own page.
 *
 * <p>{@code /} used to serve the SFL Facilities Dashboard - the only user interface SFL.IFIMP had.
 * ADR 0006 decision 3 kept it explicitly, and only until IFIMP had dashboard screens; S152 is those
 * screens, so the condition it was kept under is now met and it redirects like the other four.
 *
 * <p>The target is configured rather than local, for the same reason the emergency service's is: only
 * {@code sfl-fleet-logistics-service} packages the dashboard bundle, so in development this is a
 * cross-origin address. Behind a gateway {@code sfl.dashboard.base-url} becomes a same-origin path
 * prefix and nothing else changes.
 */
@Configuration(proxyBeanMethods = false)
class FacilitiesWebConfiguration {

    /**
     * CORS origins for the facilities API.
     *
     * <p>The two that matter for the operations dashboard are <strong>8093</strong> and
     * <strong>5005</strong>, and neither was here before S152 had a UI. The dashboard bundle is served
     * by {@code sfl-fleet-logistics-service} on 8093 and calls this service across origins, exactly as
     * it does the SSEMP service on 8092; {@code npm run dev} serves it from 5005. Without both, a
     * screen fails in a browser while every equivalent curl succeeds - which is a genuinely confusing
     * way to lose an afternoon.
     *
     * <p>8091 is kept for the service's own static page and 8092 for SSEMP; 5173 and 3000 for a
     * default Vite or CRA port. 8094 and 8095 were dropped when the asset-visibility and emergency
     * deployables were folded into FTLMP and SSEMP - nothing listens on them now, and an origin list
     * that names a dead port is a list nobody trusts to be current.
     */
    @Bean
    WebMvcConfigurer facilitiesCorsConfigurer(
            // Where the SFL Operations dashboard is served. This service does not package the bundle.
            @Value("${sfl.dashboard.base-url:http://localhost:${server.port:8091}/home}") String dashboardBaseUrl,
            @Value("${sfl.cors.allowed-origins:"
                    + "http://localhost:8090,http://localhost:8091,http://localhost:8092,http://localhost:8093,"
                    + "http://localhost:5005,http://localhost:5173,http://localhost:3000}")
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
                        // service log and absent from the message the operator is looking at -
                        // which is the one moment it exists to be useful.
                        .exposedHeaders("Location", "X-Correlation-ID");
                registry.addMapping("/actuator/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "OPTIONS")
                        .allowedHeaders("*");
            }

            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                // Retired by ADR 0006 once S152 shipped. `/index.html` stays as a notice page and
                // refreshes to the same place, so the target is configured in exactly one spot.
                //
                // "/" is deliberately not mapped here. SflDashboardAutoConfiguration redirects it to
                // this service's own /home/, and two view controllers on one path is a startup
                // failure rather than last-one-wins. /home/ is the dashboard's own entry: it reads
                // the signed-in account and lands on the screens that account is entitled to, which
                // is a better root than a hard redirect into the facilities module.
                String target = dashboardBaseUrl.replaceAll("/+$", "") + "/facilities";
                registry.addViewController("/facilities").setViewName("redirect:" + target);
                registry.addViewController("/facilities/").setViewName("redirect:" + target);
            }
        };
    }
}
