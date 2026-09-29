package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.BuildingSystemType;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.ChannelState;
import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
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
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link ChannelState}. Column names match V16. */
@Entity
@Table(name = "bms_channel_states", schema = "facilities")
public class ChannelStateRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;
    @Column(nullable = false, length = 160)
    private String channel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MeasuredQuantity quantity;
    @Enumerated(EnumType.STRING)
    @Column(name = "system_type", length = 30)
    private BuildingSystemType systemType;
    @Column(name = "building_code", length = 80)
    private String buildingCode;
    @Column(name = "last_observed_at")
    private Instant lastObservedAt;
    @Column(name = "last_received_at")
    private Instant lastReceivedAt;
    @Column(name = "last_value", precision = 20, scale = 6)
    private BigDecimal lastValue;
    @Column(name = "last_reading_id")
    private UUID lastReadingId;
    @Column(name = "breach_rule_id")
    private UUID breachRuleId;
    @Column(name = "breach_started_at")
    private Instant breachStartedAt;
    @Column(name = "breach_reading_ids", columnDefinition = "text")
    private String breachReadingIds;
    @Column(name = "alert_id")
    private UUID alertId;
    @Column(name = "critical_alert_id")
    private UUID criticalAlertId;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ChannelStateRecord() {
    }

    static ChannelStateRecord empty() {
        return new ChannelStateRecord();
    }

    void apply(ChannelState state) {
        id = state.id();
        siteCode = state.siteCode();
        deviceId = state.deviceId();
        channel = state.channel();
        quantity = state.quantity();
        systemType = state.systemType();
        buildingCode = state.buildingCode();
        lastObservedAt = state.lastObservedAt();
        lastReceivedAt = state.lastReceivedAt();
        lastValue = state.lastValue();
        lastReadingId = state.lastReadingId();
        breachRuleId = state.breachRuleId();
        breachStartedAt = state.breachStartedAt();
        breachReadingIds = IdLists.join(state.breachReadingIds());
        alertId = state.alertId();
        criticalAlertId = state.criticalAlertId();
        metadata = RecordMetadataEmbeddable.from(state.metadata());
    }

    ChannelState toDomain() {
        return new ChannelState(id, siteCode, deviceId, channel, quantity, systemType, buildingCode, lastObservedAt,
                lastReceivedAt, lastValue, lastReadingId, breachRuleId, breachStartedAt, IdLists.ids(breachReadingIds),
                alertId, criticalAlertId, metadata.toDomain(recordVersion()));
    }
}
