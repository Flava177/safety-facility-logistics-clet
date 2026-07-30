package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import java.io.IOException;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Web wiring for the fleet dashboards: CORS, the retired per-service pages, and the operations UI.
 *
 * <p>{@code /fleet}, {@code /fuel} and {@code /dispatch} used to serve pages of their own. ADR 0006
 * retired them: two interfaces over one service drift, and these had, so each now redirects to the
 * dashboard route that replaced it. The redirect is registered only when the bundle is present —
 * sending somebody to a route that is not being served would replace a working page with a bare 404 —
 * and without it the request falls through to a notice page that says where the screens went and how
 * to build them.
 *
 * <p>The SFL Operations dashboards is built with a {@code /ui/} base and copied into {@code static/ui} by the
 * service build, so a single {@code spring-boot:run} serves the API, Swagger and the dashboard from one
 * origin.
 *
 * <p>Serving a single-page app takes two pieces. Both {@code /ui} and {@code /ui/} are forwarded to
 * {@code index.html} explicitly, because a request for the directory itself leaves an empty path
 * inside the resource handler and Spring rejects that before any resolver runs. Everything deeper is
 * handled by the resolver below, which serves a real asset when there is one and otherwise falls back
 * to the shell so a refresh on {@code /ui/fleet/vehicles} resolves instead of 404ing.
 *
 * <p>When the bundle has not been built the {@code /ui} routes are not registered at all, so the
 * service still starts and the API is unaffected. {@link FleetUiStartupReporter} says so on the
 * dashboard rather than leaving a silent 404.
 */
@Configuration(proxyBeanMethods = false)
class FleetWebConfiguration {

    @Bean
    WebMvcConfigurer fleetCorsConfigurer(
            @Value("${sfl.cors.allowed-origins:http://localhost:8091,http://localhost:8093,http://localhost:8094,"
                    + "http://localhost:5005,http://localhost:5173,http://localhost:3000}") String allowedOrigins) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::strip)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
        boolean uiBundled = FleetUiBundle.isPresent();

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
                // Retired by ADR 0006. Both spellings of each, because a bookmark may carry either.
                retire(registry, "/fleet", "/ui/fleet", uiBundled);
                retire(registry, "/fuel", "/ui/fuel", uiBundled);
                retire(registry, "/dispatch", "/ui/dispatch", uiBundled);

                if (uiBundled) {
                    // Landing on the service root opens the operations dashboards.
                    registry.addRedirectViewController("/", "/ui/");
                    // Both spellings, because "/ui/" alone reaches the resource handler with an
                    // empty path and Spring answers 404 before the fallback resolver is consulted.
                    registry.addViewController("/ui").setViewName("forward:/ui/index.html");
                    registry.addViewController("/ui/").setViewName("forward:/ui/index.html");
                }
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                if (!uiBundled) {
                    return;
                }
                registry.addResourceHandler("/ui/**")
                        .addResourceLocations(FleetUiBundle.LOCATION)
                        .resourceChain(true)
                        .addResolver(new PathResourceResolver() {
                            @Override
                            protected Resource getResource(String resourcePath, Resource location)
                                    throws IOException {
                                Resource requested = location.createRelative(resourcePath);
                                if (requested.exists() && requested.isReadable()) {
                                    return requested;
                                }
                                if (looksLikeAsset(resourcePath)) {
                                    // A missing file stays a 404. Returning the shell here would hand
                                    // the browser HTML where it asked for JavaScript, and the real
                                    // failure would surface as a syntax error instead.
                                    return null;
                                }
                                Resource index = location.createRelative("index.html");
                                return index.exists() && index.isReadable() ? index : null;
                            }
                        });
            }
        };
    }

    /**
     * Points a retired route at the dashboard route that replaced it.
     *
     * <p>A redirect rather than a forward, so the address bar ends up on the route that is really
     * being served and a refresh does not land back here. When the bundle is absent there is nothing
     * to redirect to, so the request falls through to the directory's notice page instead — which
     * explains the move and says how to build the dashboard.
     */
    private static void retire(ViewControllerRegistry registry, String from, String to, boolean uiBundled) {
        String view = uiBundled ? "redirect:" + to : "forward:" + from + "/index.html";
        registry.addViewController(from).setViewName(view);
        registry.addViewController(from + "/").setViewName(view);
    }

    /** A request for a file (it has an extension in its last segment) rather than a client route. */
    private static boolean looksLikeAsset(String resourcePath) {
        int lastSlash = resourcePath.lastIndexOf('/');
        return resourcePath.indexOf('.', lastSlash + 1) >= 0;
    }
}
