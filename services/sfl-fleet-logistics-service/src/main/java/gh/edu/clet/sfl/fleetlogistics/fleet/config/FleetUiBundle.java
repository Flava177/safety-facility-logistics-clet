package gh.edu.clet.sfl.fleetlogistics.fleet.config;

import org.springframework.core.io.ClassPathResource;

/**
 * Where the built SFL Operations dashboards lives on the classpath, and whether it is actually there.
 *
 * <p>Kept in one place so the web wiring and the startup banner cannot disagree about whether the
 * dashboard is being served.
 */
final class FleetUiBundle {

    static final String LOCATION = "classpath:/static/ui/";
    static final String PATH = "/ui/";

    private FleetUiBundle() {
    }

    /** {@code true} when {@code npm run build} output has been copied into the service resources. */
    static boolean isPresent() {
        return new ClassPathResource("static/ui/index.html").exists();
    }
}
