package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyBudget;
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
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link EnergyBudget}. Insert-only: a budget version is never updated. */
@Entity
@Table(name = "energy_budgets", schema = "facilities")
public class EnergyBudgetRecord extends VersionedRecord {

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
    @Column(nullable = false)
    private int version;
    @Column(name = "consumption_budget", nullable = false, precision = 20, scale = 4)
    private BigDecimal consumptionBudget;
    @Column(name = "cost_budget", precision = 20, scale = 2)
    private BigDecimal costBudget;
    @Column(length = 3)
    private String currency;
    @Column(length = 1000)
    private String reason;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EnergyBudgetRecord() {
    }

    static EnergyBudgetRecord of(EnergyBudget budget) {
        EnergyBudgetRecord record = new EnergyBudgetRecord();
        record.id = budget.id();
        record.siteCode = budget.siteCode();
        record.utility = budget.utility();
        record.periodType = budget.period().type();
        record.periodStart = budget.period().start();
        record.version = budget.version();
        record.consumptionBudget = budget.consumptionBudget();
        record.costBudget = budget.costBudget();
        record.currency = budget.currency();
        record.reason = budget.reason();
        record.metadata = RecordMetadataEmbeddable.from(budget.metadata());
        return record;
    }

    EnergyBudget toDomain() {
        return new EnergyBudget(id, siteCode, utility, new EnergyPeriod(periodType, periodStart), version,
                consumptionBudget, costBudget, currency, reason, metadata.toDomain(recordVersion()));
    }
}
