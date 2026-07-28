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
 * Web wiring for the fleet consoles: CORS, the legacy static consoles, and the React operations UI.
 *
 * <p>The SFL Operations UI is built with a {@code /ui/} base and copied into {@code static/ui} by the
 * service build, so a single {@code spring-boot:run} serves the API, Swagger and the console from one
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
 * console rather than leaving a silent 404.
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
                registry.addViewController("/fleet").setViewName("forward:/fleet/index.html");
                registry.addViewController("/fleet/").setViewName("forward:/fleet/index.html");
                registry.addViewController("/fuel").setViewName("forward:/fuel/index.html");
                registry.addViewController("/fuel/").setViewName("forward:/fuel/index.html");
                registry.addViewController("/dispatch").setViewName("forward:/dispatch/index.html");
                registry.addViewController("/dispatch/").setViewName("forward:/dispatch/index.html");

                if (uiBundled) {
                    // Landing on the service root opens the operations console.
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

    /** A request for a file (it has an extension in its last segment) rather than a client route. */
    private static boolean looksLikeAsset(String resourcePath) {
        int lastSlash = resourcePath.lastIndexOf('/');
        return resourcePath.indexOf('.', lastSlash + 1) >= 0;
    }
}
