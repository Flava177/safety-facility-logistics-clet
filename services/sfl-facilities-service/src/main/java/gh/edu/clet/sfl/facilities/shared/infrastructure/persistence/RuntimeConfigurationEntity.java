package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.shared.application.port.RuntimeConfigurationPort.ConfigurationValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One effective-dated configuration value.
 *
 * <p>Superseding writes {@code effective_to} on the old row and inserts a new one, so the history of
 * a threshold is recoverable — NFR 23.8 asks for versioned configuration, and an escalation raised
 * last month can only be reconciled against the value that was active last month.
 */
@Entity
@Table(name = "facility_runtime_configuration", schema = "facilities")
class RuntimeConfigurationEntity {

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
    @Column(nullable = false)
    private long version;
    @Column(name = "updated_by", nullable = false, length = 160)
    private String updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RuntimeConfigurationEntity() {
    }

    RuntimeConfigurationEntity(UUID id, String configKey, String siteCode, String configValue, String valueType,
            String description, Instant effectiveFrom, long version, String updatedBy, Instant updatedAt) {
        this.id = id;
        this.configKey = configKey;
        this.siteCode = siteCode;
        this.configValue = configValue;
        this.valueType = valueType;
        this.description = description;
        this.effectiveFrom = effectiveFrom;
        this.version = version;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    String configValue() {
        return configValue;
    }

    String description() {
        return description;
    }

    long version() {
        return version;
    }

    void supersede(Instant at) {
        this.effectiveTo = at;
    }

    ConfigurationValue toDomain() {
        return new ConfigurationValue(configKey, siteCode, configValue, valueType, description, version,
                updatedBy, updatedAt);
    }
}
