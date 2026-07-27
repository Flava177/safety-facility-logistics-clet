package gh.edu.clet.sfl.emergencynotification.application.port;

import java.time.Duration;
import java.util.Optional;

/** Runtime-configurable thresholds, read at the moment of evaluation (never cached across evaluations). */
public interface RuntimeConfigurationPort {

    Optional<String> value(String key, String siteCode);

    Duration duration(String key, String siteCode, Duration fallback);

    boolean flag(String key, String siteCode, boolean fallback);
}
