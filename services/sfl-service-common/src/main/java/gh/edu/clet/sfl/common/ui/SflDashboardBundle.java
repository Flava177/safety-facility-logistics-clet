package gh.edu.clet.sfl.common.ui;

import org.springframework.core.io.ClassPathResource;

/**
 * Where the built SFL Operations dashboards lives on the classpath, and whether it is actually there.
 *
 * <p>Kept in one place so the web wiring and every service's startup banner cannot disagree about
 * whether the dashboard is being served.
 *
 * <p>{@code PATH} must stay in step with {@code VITE_BASENAME} in {@code vite.config.ts}. The bundle
 * hard-codes its own mount point into every asset URL at build time, so a mismatch serves a shell
 * whose scripts all 404 - which looks like a blank page, not like a configuration error.
 */
public final class SflDashboardBundle {

    public static final String LOCATION = "classpath:/static/home/";
    public static final String PATH = "/home/";

    private SflDashboardBundle() {
    }

    /** {@code true} when {@code npm run build} output has been copied into this service's resources. */
    public static boolean isPresent() {
        return new ClassPathResource("static/home/index.html").exists();
    }
}
