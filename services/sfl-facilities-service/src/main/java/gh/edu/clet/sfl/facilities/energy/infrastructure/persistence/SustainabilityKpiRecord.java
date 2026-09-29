package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.CompletenessFlag;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.SustainabilityKpi;
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

/** JPA mapping for {@link SustainabilityKpi} - the S225 read model. */
@Entity
@Table(name = "sustainability_kpis", schema = "facilities")
public class SustainabilityKpiRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "kpi_key", nullable = false, length = 200)
    private String kpiKey;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_level", nullable = false, length = 20)
    private SustainabilityKpi.KpiScope scope;
    @Column(name = "building_code", length = 40)
    private String buildingCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Utility utility;
    @Column(name = "canonical_unit", nullable = false, length = 10)
    private String canonicalUnit;
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private EnergyPeriod.PeriodType periodType;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(name = "period_closed", nullable = false)
    private boolean periodClosed;
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal consumption;
    @Column(name = "previous_consumption", precision = 20, scale = 4)
    private BigDecimal previousConsumption;
    @Column(name = "trend_pct", precision = 12, scale = 4)
    private BigDecimal trendPct;
    @Column(name = "carbon_kg_co2e", precision = 20, scale = 4)
    private BigDecimal carbonKgCo2e;
    @Enumerated(EnumType.STRING)
    @Column(name = "emission_factor_status", nullable = false, length = 20)
    private SustainabilityKpi.EmissionFactorStatus emissionFactorStatus;
    @Column(name = "expected_readings", nullable = false)
    private int expectedReadings;
    @Column(name = "received_readings", nullable = false)
    private int receivedReadings;
    @Column(name = "completeness_pct", nullable = false, precision = 7, scale = 2)
    private BigDecimal completenessPct;
    @Enumerated(EnumType.STRING)
    @Column(name = "completeness_flag", nullable = false, length = 10)
    private CompletenessFlag completenessFlag;
    @Column(name = "minimum_completeness_pct", nullable = false, precision = 7, scale = 2)
    private BigDecimal minimumCompletenessPct;
    @Column(nullable = false)
    private int revision;
    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected SustainabilityKpiRecord() {
    }

    static SustainabilityKpiRecord empty() {
        return new SustainabilityKpiRecord();
    }

    void apply(SustainabilityKpi kpi) {
        id = kpi.id();
        kpiKey = kpi.kpiKey();
        siteCode = kpi.siteCode();
        scope = kpi.scope();
        buildingCode = kpi.buildingCode();
        utility = kpi.utility();
        canonicalUnit = kpi.utility().unitCode();
        periodType = kpi.period().type();
        periodStart = kpi.period().start();
        periodEnd = kpi.period().end();
        periodClosed = kpi.periodClosed();
        consumption = kpi.consumption();
        previousConsumption = kpi.previousConsumption();
        trendPct = kpi.trendPct();
        carbonKgCo2e = kpi.carbonKgCo2e();
        emissionFactorStatus = kpi.emissionFactorStatus();
        expectedReadings = kpi.expectedReadings();
        receivedReadings = kpi.receivedReadings();
        completenessPct = kpi.completenessPct();
        completenessFlag = kpi.completenessFlag();
        minimumCompletenessPct = kpi.minimumCompletenessPct();
        revision = kpi.revision();
        computedAt = kpi.computedAt();
        metadata = RecordMetadataEmbeddable.from(kpi.metadata());
    }

    SustainabilityKpi toDomain() {
        return new SustainabilityKpi(id, kpiKey, siteCode, scope, buildingCode, utility,
                new EnergyPeriod(periodType, periodStart), periodClosed, consumption, previousConsumption, trendPct,
                carbonKgCo2e, emissionFactorStatus, expectedReadings, receivedReadings, completenessPct,
                completenessFlag, minimumCompletenessPct, revision, computedAt, metadata.toDomain(recordVersion()));
    }
}
