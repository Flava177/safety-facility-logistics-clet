package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.PeriodVarianceResult;
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
import java.util.UUID;

/** JPA mapping for {@link PeriodVarianceResult}. Insert-only; the adapter never updates one. */
@Entity
@Table(name = "energy_period_results", schema = "facilities")
public class EnergyPeriodResultRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Utility utility;
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private EnergyPeriod.PeriodType periodType;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal consumption;
    @Column(name = "reading_count", nullable = false)
    private int readingCount;
    @Column(name = "budget_id")
    private UUID budgetId;
    @Column(name = "budget_version")
    private Integer budgetVersion;
    @Column(name = "consumption_budget", precision = 20, scale = 4)
    private BigDecimal consumptionBudget;
    @Column(name = "variance_pct", precision = 12, scale = 4)
    private BigDecimal variancePct;
    @Column(name = "threshold_pct", nullable = false, precision = 12, scale = 4)
    private BigDecimal thresholdPct;
    @Column(name = "variance_alert", nullable = false)
    private boolean varianceAlert;
    @Column(name = "tariff_id")
    private UUID tariffId;
    @Column(name = "tariff_version")
    private Integer tariffVersion;
    @Column(precision = 20, scale = 2)
    private BigDecimal cost;
    @Column(name = "cost_budget", precision = 20, scale = 2)
    private BigDecimal costBudget;
    @Column(name = "cost_variance_pct", precision = 12, scale = 4)
    private BigDecimal costVariancePct;
    @Column(length = 3)
    private String currency;
    @Column(name = "tariff_missing", nullable = false)
    private boolean tariffMissing;
    @Column(name = "closed_by", nullable = false, length = 160)
    private String closedBy;
    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EnergyPeriodResultRecord() {
    }

    static EnergyPeriodResultRecord of(PeriodVarianceResult result) {
        EnergyPeriodResultRecord record = new EnergyPeriodResultRecord();
        record.id = result.id();
        record.siteCode = result.siteCode();
        record.utility = result.utility();
        record.periodType = result.period().type();
        record.periodStart = result.period().start();
        record.periodEnd = result.period().end();
        record.consumption = result.consumption();
        record.readingCount = result.readingCount();
        record.budgetId = result.budgetId();
        record.budgetVersion = result.budgetVersion();
        record.consumptionBudget = result.consumptionBudget();
        record.variancePct = result.variancePct();
        record.thresholdPct = result.thresholdPct();
        record.varianceAlert = result.varianceAlert();
        record.tariffId = result.tariffId();
        record.tariffVersion = result.tariffVersion();
        record.cost = result.cost();
        record.costBudget = result.costBudget();
        record.costVariancePct = result.costVariancePct();
        record.currency = result.currency();
        record.tariffMissing = result.tariffMissing();
        record.closedBy = result.closedBy();
        record.closedAt = result.closedAt();
        record.metadata = RecordMetadataEmbeddable.from(result.metadata());
        return record;
    }

    PeriodVarianceResult toDomain() {
        return new PeriodVarianceResult(id, siteCode, utility, new EnergyPeriod(periodType, periodStart), consumption,
                readingCount, budgetId, budgetVersion, consumptionBudget, variancePct, thresholdPct, varianceAlert,
                tariffId, tariffVersion, cost, costBudget, costVariancePct, currency, tariffMissing, closedBy, closedAt,
                metadata.toDomain(recordVersion()));
    }
}
