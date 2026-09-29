package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.ReadingStatus;
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
import java.util.UUID;

/** JPA mapping for {@link ConsumptionReading}. Column names match V17 exactly. */
@Entity
@Table(name = "energy_readings", schema = "facilities")
public class EnergyReadingRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "meter_id", nullable = false)
    private UUID meterId;
    @Column(name = "building_code", nullable = false, length = 40)
    private String buildingCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Utility utility;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeterSource source;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReadingStatus status;
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
    @Column(name = "interval_start")
    private Instant intervalStart;
    @Column(name = "register_value", precision = 20, scale = 4)
    private BigDecimal registerValue;
    @Column(precision = 20, scale = 4)
    private BigDecimal consumption;
    @Column(name = "vendor_value", precision = 20, scale = 6)
    private BigDecimal vendorValue;
    @Column(name = "vendor_unit", length = 20)
    private String vendorUnit;
    @Column(name = "source_reference", length = 200)
    private String sourceReference;
    @Column(name = "plausibility_checked", nullable = false)
    private boolean plausibilityChecked;
    @Column(name = "trailing_daily_average", precision = 20, scale = 4)
    private BigDecimal trailingDailyAverage;
    @Column(name = "band_low", precision = 20, scale = 4)
    private BigDecimal bandLow;
    @Column(name = "band_high", precision = 20, scale = 4)
    private BigDecimal bandHigh;
    @Column(name = "hold_reason", length = 1000)
    private String holdReason;
    @Column(name = "entered_by", length = 160)
    private String enteredBy;
    @Column(name = "entered_at")
    private Instant enteredAt;
    @Column(name = "verified_by", length = 160)
    private String verifiedBy;
    @Column(name = "verified_at")
    private Instant verifiedAt;
    @Column(name = "verification_note", length = 1000)
    private String verificationNote;
    @Column(length = 1000)
    private String note;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EnergyReadingRecord() {
    }

    static EnergyReadingRecord empty() {
        return new EnergyReadingRecord();
    }

    void apply(ConsumptionReading reading) {
        id = reading.id();
        siteCode = reading.siteCode();
        meterId = reading.meterId();
        buildingCode = reading.buildingCode();
        utility = reading.utility();
        source = reading.source();
        status = reading.status();
        observedAt = reading.observedAt();
        intervalStart = reading.intervalStart();
        registerValue = reading.registerValue();
        consumption = reading.consumption();
        vendorValue = reading.vendorValue();
        vendorUnit = reading.vendorUnit();
        sourceReference = reading.sourceReference();
        plausibilityChecked = reading.plausibilityChecked();
        trailingDailyAverage = reading.trailingDailyAverage();
        bandLow = reading.bandLow();
        bandHigh = reading.bandHigh();
        holdReason = reading.holdReason();
        enteredBy = reading.enteredBy();
        enteredAt = reading.enteredAt();
        verifiedBy = reading.verifiedBy();
        verifiedAt = reading.verifiedAt();
        verificationNote = reading.verificationNote();
        note = reading.note();
        metadata = RecordMetadataEmbeddable.from(reading.metadata());
    }

    ConsumptionReading toDomain() {
        return new ConsumptionReading(id, siteCode, meterId, buildingCode, utility, source, status, observedAt,
                intervalStart, registerValue, consumption, vendorValue, vendorUnit, sourceReference,
                plausibilityChecked, trailingDailyAverage, bandLow, bandHigh, holdReason, enteredBy, enteredAt,
                verifiedBy, verifiedAt, verificationNote, note, metadata.toDomain(recordVersion()));
    }
}
