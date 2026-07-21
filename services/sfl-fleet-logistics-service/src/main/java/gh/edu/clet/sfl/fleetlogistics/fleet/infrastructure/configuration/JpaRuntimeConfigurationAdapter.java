package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.configuration;

import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed runtime configuration.
 *
 * <p>Values are read on every call rather than cached, because SRS-SFL-S166-02 requires escalation to be
 * evaluated with "the runtime configuration active at the time of evaluation". A site-specific row wins
 * over the platform default; when neither exists the compiled-in fallback applies and is logged, so an
 * operator can see a threshold was never configured.
 */
@Component
public class JpaRuntimeConfigurationAdapter implements RuntimeConfigurationPort {

    private static final Logger log = LoggerFactory.getLogger(JpaRuntimeConfigurationAdapter.class);

    static final String KEY_COMPLIANCE_WARNING = "fleet.compliance.expiry-warning-window";
    static final String KEY_INSPECTION_VALIDITY = "fleet.inspection.validity-window";
    static final String KEY_SERVICE_DUE_WARNING = "fleet.service.due-warning-window";
    static final String KEY_ODOMETER_STALENESS = "fleet.odometer.staleness-threshold";
    static final String KEY_TELEMATICS_STALENESS = "fleet.telematics.staleness-threshold";
    static final String KEY_DASHBOARD_FRESHNESS = "fleet.dashboard.freshness-threshold";
    static final String KEY_SIGNATURE_WINDOW = "fleet.integration.signature-window";
    static final String KEY_MAX_ATTEMPTS = "fleet.outbound.max-attempts";
    static final String KEY_RETRY_BASE_SECONDS = "fleet.outbound.retry-base-seconds";
    static final String KEY_RETRY_MAX_SECONDS = "fleet.outbound.retry-max-seconds";

    private static final Map<String, String> FALLBACKS = Map.of(
            KEY_COMPLIANCE_WARNING, "P30D",
            KEY_INSPECTION_VALIDITY, "P1D",
            KEY_SERVICE_DUE_WARNING, "P14D",
            KEY_ODOMETER_STALENESS, "P30D",
            KEY_TELEMATICS_STALENESS, "PT6H",
            KEY_DASHBOARD_FRESHNESS, "PT15M",
            KEY_SIGNATURE_WINDOW, "PT5M",
            KEY_MAX_ATTEMPTS, "8",
            KEY_RETRY_BASE_SECONDS, "10",
            KEY_RETRY_MAX_SECONDS, "3600");

    private final RuntimeConfigurationRepository repository;

    JpaRuntimeConfigurationAdapter(RuntimeConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Duration complianceExpiryWarningWindow(String siteCode) {
        return duration(KEY_COMPLIANCE_WARNING, siteCode);
    }

    @Override
    public Duration inspectionValidityWindow(String siteCode) {
        return duration(KEY_INSPECTION_VALIDITY, siteCode);
    }

    @Override
    public Duration serviceDueWarningWindow(String siteCode) {
        return duration(KEY_SERVICE_DUE_WARNING, siteCode);
    }

    @Override
    public Duration odometerStalenessThreshold(String siteCode) {
        return duration(KEY_ODOMETER_STALENESS, siteCode);
    }

    @Override
    public Duration telematicsStalenessThreshold(String siteCode) {
        return duration(KEY_TELEMATICS_STALENESS, siteCode);
    }

    @Override
    public Duration dashboardFreshnessThreshold(String siteCode) {
        return duration(KEY_DASHBOARD_FRESHNESS, siteCode);
    }

    @Override
    public Duration integrationSignatureWindow() {
        return duration(KEY_SIGNATURE_WINDOW, null);
    }

    @Override
    public Duration outboundRetryBackoff(int attempt) {
        long base = number(KEY_RETRY_BASE_SECONDS, null);
        long max = number(KEY_RETRY_MAX_SECONDS, null);
        int exponent = Math.max(0, Math.min(attempt - 1, 20));
        long seconds = base * (1L << exponent);
        return Duration.ofSeconds(Math.min(seconds, max));
    }

    @Override
    public int outboundMaxAttempts() {
        return (int) number(KEY_MAX_ATTEMPTS, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> value(String key, String siteCode) {
        return Optional.ofNullable(resolve(key, siteCode));
    }

    @Override
    @Transactional(readOnly = true)
    public Instant activeConfigurationChangedAt() {
        return repository.findAllEffective().stream()
                .map(RuntimeConfigurationEntity::updatedAt)
                .max(Instant::compareTo)
                .orElse(Instant.EPOCH);
    }

    private Duration duration(String key, String siteCode) {
        String raw = resolveOrFallback(key, siteCode);
        try {
            return Duration.parse(raw);
        } catch (java.time.format.DateTimeParseException exception) {
            log.error("Runtime configuration {} for site {} is not an ISO-8601 duration: '{}'. Using the "
                    + "compiled-in fallback {}.", key, siteCode, raw, FALLBACKS.get(key));
            return Duration.parse(FALLBACKS.get(key));
        }
    }

    private long number(String key, String siteCode) {
        String raw = resolveOrFallback(key, siteCode);
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException exception) {
            log.error("Runtime configuration {} for site {} is not a number: '{}'. Using the compiled-in "
                    + "fallback {}.", key, siteCode, raw, FALLBACKS.get(key));
            return Long.parseLong(FALLBACKS.get(key));
        }
    }

    private String resolveOrFallback(String key, String siteCode) {
        String resolved = resolve(key, siteCode);
        if (resolved != null) {
            return resolved;
        }
        log.warn("Runtime configuration {} is not present for site {}; using the compiled-in fallback {}.",
                key, siteCode, FALLBACKS.get(key));
        return FALLBACKS.get(key);
    }

    @Transactional(readOnly = true)
    String resolve(String key, String siteCode) {
        String normalisedSite = siteCode == null ? null : siteCode.strip().toUpperCase(Locale.ROOT);
        List<RuntimeConfigurationEntity> effective = repository.findAllEffective();
        Map<String, String> defaults = new HashMap<>();
        String siteSpecific = null;
        for (RuntimeConfigurationEntity entity : effective) {
            if (!entity.configKey().equals(key)) {
                continue;
            }
            if (entity.siteCode() == null) {
                defaults.put(key, entity.configValue());
            } else if (entity.siteCode().equalsIgnoreCase(normalisedSite)) {
                siteSpecific = entity.configValue();
            }
        }
        return siteSpecific != null ? siteSpecific : defaults.get(key);
    }
}
