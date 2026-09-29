package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.DailyConsumption;
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

/**
 * JPA mapping for {@link DailyConsumption}. The {@code @Version} inherited from {@link VersionedRecord} is
 * what keeps two readings posting to the same meter-day concurrently from losing one of them: the second
 * writer fails and its message is retried (an AMI message is rolled back unclaimed, so the gateway's retry
 * with the same idempotency key is accepted).
 */
@Entity
@Table(name = "energy_daily_consumption", schema = "facilities")
public class EnergyDailyConsumptionRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "building_code", nullable = false, length = 40)
    private String buildingCode;
    @Column(name = "meter_id", nullable = false)
    private UUID meterId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Utility utility;
    @Column(name = "consumption_day", nullable = false)
    private LocalDate day;
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal consumption;
    @Column(name = "reading_count", nullable = false)
    private int readingCount;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EnergyDailyConsumptionRecord() {
    }

    static EnergyDailyConsumptionRecord empty() {
        return new EnergyDailyConsumptionRecord();
    }

    void apply(DailyConsumption daily) {
        id = daily.id();
        siteCode = daily.siteCode();
        buildingCode = daily.buildingCode();
        meterId = daily.meterId();
        utility = daily.utility();
        day = daily.day();
        consumption = daily.consumption();
        readingCount = daily.readingCount();
        metadata = RecordMetadataEmbeddable.from(daily.metadata());
    }

    DailyConsumption toDomain() {
        return new DailyConsumption(id, siteCode, buildingCode, meterId, utility, day, consumption, readingCount,
                metadata.toDomain(recordVersion()));
    }
}
