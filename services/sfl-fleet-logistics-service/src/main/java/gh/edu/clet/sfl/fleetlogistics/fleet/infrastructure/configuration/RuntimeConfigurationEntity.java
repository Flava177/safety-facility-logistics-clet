package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.configuration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A versioned runtime configuration value (config-without-code, PLAT-05).
 *
 * <p>A row is superseded by setting {@code effectiveTo}; values are never edited in place, so an audit
 * of "which threshold was active when this SLA was evaluated" is always answerable.
 */
@Entity
@Table(name = "fleet_runtime_configuration", schema = "fleet_logistics")
public class RuntimeConfigurationEntity {

    @Id
    private UUID id;

    @Column(name = "config_key", nullable = false, length = 120)
    private String configKey;

    @Column(name = "site_code", length = 40)
    private String siteCode;

    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Column(name = "value_type", nullable = false, length = 30)
    private String valueType;

    @Column(length = 500)
    private String description;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_by", nullable = false, length = 160)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RuntimeConfigurationEntity() {
    }

    public RuntimeConfigurationEntity(UUID id, String configKey, String siteCode, String configValue,
            String valueType, String description, Instant effectiveFrom, String updatedBy, Instant updatedAt) {
        this.id = id;
        this.configKey = configKey;
        this.siteCode = siteCode;
        this.configValue = configValue;
        this.valueType = valueType;
        this.description = description;
        this.effectiveFrom = effectiveFrom;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String configKey() {
        return configKey;
    }

    public String siteCode() {
        return siteCode;
    }

    public String configValue() {
        return configValue;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void supersede(Instant now) {
        this.effectiveTo = now;
    }
}
