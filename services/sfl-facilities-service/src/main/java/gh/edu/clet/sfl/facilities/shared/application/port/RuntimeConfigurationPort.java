package gh.edu.clet.sfl.facilities.shared.application.port;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Config-without-code (NFR 23.8; SRS-SFL-S152-02 "using the runtime configuration active at the time
 * of evaluation").
 *
 * <p>Values are read at evaluation time, never cached across a run — a threshold changed at 09:00
 * must apply to the 09:01 evaluation without a redeploy or a restart. Resolution is site-first: a
 * value scoped to the site wins, otherwise the platform default applies.
 */
public interface RuntimeConfigurationPort {

    /** A configured value with its scope and provenance. */
    record ConfigurationValue(
            String key,
            String siteCode,
            String value,
            String valueType,
            String description,
            long version,
            String updatedBy,
            java.time.Instant updatedAt) {
    }

    Optional<String> find(String key, String siteCode);

    /** Resolves a duration, falling back to {@code fallback} when unset or unparseable. */
    Duration duration(String key, String siteCode, Duration fallback);

    /** Resolves an integer, falling back to {@code fallback} when unset or unparseable. */
    int integer(String key, String siteCode, int fallback);

    /** Every active value, for the configuration screen and for operational review. */
    List<ConfigurationValue> activeValues(String siteCode);

    /**
     * Supersedes the active value for a key.
     *
     * <p>Versioned rather than overwritten: NFR 23.8 requires configuration to be "runtime-configurable
     * and versioned", and a threshold that changed silently is one nobody can reconcile an old
     * escalation against.
     */
    ConfigurationValue put(String key, String siteCode, String value, String valueType, String description,
            String actorId);
}
