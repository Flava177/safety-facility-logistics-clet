package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Config-without-code: runtime-configurable fleet thresholds, read at the moment of evaluation.
 *
 * <p>SRS-SFL-S166-02 requires that "escalation rules must be evaluated using the runtime configuration
 * active at the time of evaluation", so callers must not cache these values across evaluations.
 */
public interface RuntimeConfigurationPort {

    /** How long a compliance document may have left before it is flagged as expiring. */
    Duration complianceExpiryWarningWindow(String siteCode);

    /** How long a passed inspection remains valid before a new one is required. */
    Duration inspectionValidityWindow(String siteCode);

    /** How far ahead of the due date a service is reported as due. */
    Duration serviceDueWarningWindow(String siteCode);

    /** Age at which the last odometer reading is considered stale provenance. */
    Duration odometerStalenessThreshold(String siteCode);

    /** Age at which telematics data is considered stale (SRS-SFL-S166-05 stale-data indicator). */
    Duration telematicsStalenessThreshold(String siteCode);

    /** Age at which a dashboard snapshot must display a stale-data warning. */
    Duration dashboardFreshnessThreshold(String siteCode);

    /** Maximum inbound-message age accepted by the HMAC replay guard. */
    Duration integrationSignatureWindow();

    /** Backoff applied to the {@code attempt}-th outbound delivery attempt. */
    Duration outboundRetryBackoff(int attempt);

    /** Attempts after which an outbound message is dead-lettered. */
    int outboundMaxAttempts();

    /** A raw configuration value, for values that do not have a typed accessor yet. */
    Optional<String> value(String key, String siteCode);

    /** The instant the active configuration set was last changed, for audit and dashboards. */
    Instant activeConfigurationChangedAt();
}
