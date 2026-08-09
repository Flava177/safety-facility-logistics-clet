package gh.edu.clet.sfl.common.ui;

import java.io.IOException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the SFL Operations dashboards at {@code /home} from whichever service is running.
 *
 * <h2>Why every service serves it</h2>
 *
 * <p>The dashboard used to be packaged by one service and every other service's screens were reached
 * through that service's port. That made one platform a dependency of the other two: start
 * facilities on its own and its own screens were unreachable, which is not what a service on its own
 * port should mean. There is still exactly one bundle - ADR 0006 stands, it is built once by the
 * aggregator - but each jar now carries a copy, so each port is complete on its own.
 *
 * <p>An auto-configuration rather than a {@code @Component}, because each service scans only its own
 * package. Registering it here means a service picks up the wiring by depending on this module, with
 * nothing to remember when the next service is added.
 *
 * <h2>Serving a single-page app</h2>
 *
 * <p>Two pieces. Both {@code /home} and {@code /home/} are forwarded to {@code index.html}
 * explicitly, because a request for the directory itself leaves an empty path inside the resource
 * handler and Spring rejects that before any resolver runs. Everything deeper is handled by the
 * resolver below, which serves a real asset when there is one and otherwise falls back to the shell
 * so a refresh on {@code /home/fleet/vehicles} resolves instead of 404ing.
 *
 * <p>When the bundle has not been built the routes are not registered at all, so the service still
 * starts and its API is unaffected.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(WebMvcConfigurer.class)
public class SflDashboardAutoConfiguration {

    @Bean
    public WebMvcConfigurer sflDashboardWebConfigurer() {
        boolean bundled = SflDashboardBundle.isPresent();

        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {
                if (!bundled) {
                    return;
                }
                // Landing on the service root opens its dashboard.
                registry.addRedirectViewController("/", SflDashboardBundle.PATH);
                // Both spellings, because "/home/" alone reaches the resource handler with an empty
                // path and Spring answers 404 before the fallback resolver is consulted.
                registry.addViewController("/home").setViewName("forward:/home/index.html");
                registry.addViewController("/home/").setViewName("forward:/home/index.html");
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                if (!bundled) {
                    return;
                }
                registry.addResourceHandler("/home/**")
                        .addResourceLocations(SflDashboardBundle.LOCATION)
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
