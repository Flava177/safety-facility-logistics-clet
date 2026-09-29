package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyAlert;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JPA mapping for {@link EnergyAlert}. The period is stored as start and end; a one-day span is a DAY
 * period (an anomaly), anything else a month.
 */
@Entity
@Table(name = "energy_alerts", schema = "facilities")
public class EnergyAlertRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "alert_key", nullable = false, length = 200)
    private String alertKey;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 20)
    private EnergyAlert.EnergyAlertType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Utility utility;
    @Column(name = "building_code", length = 40)
    private String buildingCode;
    @Column(name = "meter_id")
    private UUID meterId;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(precision = 20, scale = 4)
    private BigDecimal observed;
    @Column(name = "reference_value", precision = 20, scale = 4)
    private BigDecimal referenceValue;
    @Column(name = "deviation_pct", precision = 12, scale = 4)
    private BigDecimal deviationPct;
    @Column(name = "threshold_pct", precision = 12, scale = 4)
    private BigDecimal thresholdPct;
    @Column(nullable = false, length = 1000)
    private String message;
    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EnergyAlertRecord() {
    }

    static EnergyAlertRecord of(EnergyAlert alert) {
        EnergyAlertRecord record = new EnergyAlertRecord();
        record.id = alert.id();
        record.alertKey = alert.alertKey();
        record.siteCode = alert.siteCode();
        record.type = alert.type();
        record.utility = alert.utility();
        record.buildingCode = alert.buildingCode();
        record.meterId = alert.meterId();
        record.periodStart = alert.period().start();
        record.periodEnd = alert.period().end();
        record.observed = alert.observed();
        record.referenceValue = alert.referenceValue();
        record.deviationPct = alert.deviationPct();
        record.thresholdPct = alert.thresholdPct();
        record.message = alert.message().length() > 1000 ? alert.message().substring(0, 1000) : alert.message();
        record.raisedAt = alert.raisedAt();
        record.metadata = RecordMetadataEmbeddable.from(alert.metadata());
        return record;
    }

    EnergyAlert toDomain() {
        EnergyPeriod.PeriodType type = ChronoUnit.DAYS.between(periodStart, periodEnd) == 1
                ? EnergyPeriod.PeriodType.DAY : EnergyPeriod.PeriodType.MONTH;
        return new EnergyAlert(id, alertKey, siteCode, this.type, utility, buildingCode, meterId,
                new EnergyPeriod(type, periodStart), observed, referenceValue, deviationPct, thresholdPct, message,
                raisedAt, metadata.toDomain(recordVersion()));
    }
}
