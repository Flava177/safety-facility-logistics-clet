package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.AlertPriority;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.RuleCondition;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ThresholdRule;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link ThresholdRule}. Column names match V16 - one row per rule version. */
@Entity
@Table(name = "bms_threshold_rules", schema = "facilities")
public class ThresholdRuleRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;
    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MeasuredQuantity quantity;
    @Enumerated(EnumType.STRING)
    @Column(name = "system_type", length = 30)
    private BuildingSystemType systemType;
    @Column(name = "device_id")
    private UUID deviceId;
    @Column(name = "building_code", length = 80)
    private String buildingCode;
    @Column(name = "room_id")
    private UUID roomId;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_condition", nullable = false, length = 20)
    private RuleCondition condition;
    @Column(name = "lower_limit", precision = 20, scale = 6)
    private BigDecimal lowerLimit;
    @Column(name = "upper_limit", precision = 20, scale = 6)
    private BigDecimal upperLimit;
    @Column(length = 500)
    private String codes;
    @Column(name = "debounce_seconds", nullable = false)
    private long debounceSeconds;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertPriority priority;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "change_reason", length = 1000)
    private String changeReason;
    @Column(name = "override_reason", length = 1000)
    private String overrideReason;
    @Column(name = "accountable_owner", length = 160)
    private String accountableOwner;
    @Column(name = "superseded_at")
    private Instant supersededAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ThresholdRuleRecord() {
    }

    static ThresholdRuleRecord empty() {
        return new ThresholdRuleRecord();
    }

    void apply(ThresholdRule rule) {
        id = rule.id();
        ruleId = rule.ruleId();
        ruleVersion = rule.ruleVersion();
        siteCode = rule.siteCode();
        name = rule.name();
        quantity = rule.quantity();
        systemType = rule.systemType();
        deviceId = rule.deviceId();
        buildingCode = rule.buildingCode();
        roomId = rule.roomId();
        condition = rule.condition();
        lowerLimit = rule.lowerLimit();
        upperLimit = rule.upperLimit();
        codes = rule.codesText().isBlank() ? null : rule.codesText();
        debounceSeconds = rule.debounce().getSeconds();
        priority = rule.priority();
        enabled = rule.enabled();
        changeReason = rule.changeReason();
        overrideReason = rule.overrideReason();
        accountableOwner = rule.accountableOwner();
        supersededAt = rule.supersededAt();
        metadata = RecordMetadataEmbeddable.from(rule.metadata());
    }

    ThresholdRule toDomain() {
        return new ThresholdRule(id, ruleId, ruleVersion, siteCode, name, quantity, systemType, deviceId,
                buildingCode, roomId, condition, lowerLimit, upperLimit, IdLists.codes(codes),
                Duration.ofSeconds(debounceSeconds), priority, enabled, changeReason, overrideReason,
                accountableOwner, supersededAt, metadata.toDomain(recordVersion()));
    }
}
