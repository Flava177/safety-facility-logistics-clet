package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and supersedes runtime configuration (NFR 23.8, SRS-SFL-S152-02).
 *
 * <p>Nothing is cached. A value changed at 09:00 applies to the 09:01 evaluation, which is precisely
 * what "the runtime configuration active at the time of evaluation" asks for; the read is a primary-key
 * lookup on a small table, so the cost of correctness here is negligible.
 *
 * <p>An unparseable value falls back to the caller's default and logs, rather than throwing. A
 * mistyped threshold should degrade an escalation window, not take down every command that reads it.
 */
@Component
class JpaRuntimeConfigurationAdapter implements RuntimeConfigurationPort {

    private static final Logger log = LoggerFactory.getLogger(JpaRuntimeConfigurationAdapter.class);

    private final RuntimeConfigurationRepository configuration;
    private final Clock clock;

    JpaRuntimeConfigurationAdapter(RuntimeConfigurationRepository configuration, Clock clock) {
        this.configuration = configuration;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> find(String key, String siteCode) {
        return resolve(key, siteCode).map(RuntimeConfigurationEntity::configValue);
    }

    @Override
    @Transactional(readOnly = true)
    public Duration duration(String key, String siteCode, Duration fallback) {
        return find(key, siteCode)
                .map(value -> {
                    try {
                        return Duration.parse(value);
                    } catch (DateTimeParseException exception) {
                        log.warn("Configuration {} for site {} is not an ISO-8601 duration ('{}'); using {}",
                                key, siteCode, value, fallback);
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    @Override
    @Transactional(readOnly = true)
    public int integer(String key, String siteCode, int fallback) {
        return find(key, siteCode)
                .map(value -> {
                    try {
                        return Integer.parseInt(value.strip());
                    } catch (NumberFormatException exception) {
                        log.warn("Configuration {} for site {} is not an integer ('{}'); using {}",
                                key, siteCode, value, fallback);
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigurationValue> activeValues(String siteCode) {
        return configuration.findAllActive(blankToNull(siteCode)).stream()
                .map(RuntimeConfigurationEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public ConfigurationValue put(String key, String siteCode, String value, String valueType, String description,
            String actorId) {
        Instant now = clock.instant();
        String scope = blankToNull(siteCode);
        Optional<RuntimeConfigurationEntity> current = scope == null
                ? configuration.findActiveDefault(key)
                : configuration.findActiveForSite(key, scope);

        long nextVersion = current.map(existing -> {
            existing.supersede(now);
            configuration.save(existing);
            // Flush before the insert below. A partial unique index enforces "one active value per key
            // per scope", and Hibernate is free to order the INSERT ahead of this UPDATE within the
            // same flush — which trips the constraint even though the end state is valid. Forcing the
            // close to land first is what makes supersede-then-insert legal.
            configuration.flush();
            return existing.version() + 1;
        }).orElse(0L);

        String resolvedDescription = description == null || description.isBlank()
                ? current.map(RuntimeConfigurationEntity::description).orElse(null)
                : description;

        RuntimeConfigurationEntity saved = configuration.save(new RuntimeConfigurationEntity(UUID.randomUUID(),
                key, scope, value, valueType == null || valueType.isBlank() ? "STRING" : valueType,
                resolvedDescription, now, nextVersion, actorId, now));
        return saved.toDomain();
    }

    /** Site override first, platform default second. */
    private Optional<RuntimeConfigurationEntity> resolve(String key, String siteCode) {
        String scope = blankToNull(siteCode);
        if (scope != null) {
            Optional<RuntimeConfigurationEntity> scoped = configuration.findActiveForSite(key, scope);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        return configuration.findActiveDefault(key);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
