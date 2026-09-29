package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.MeterStatus;
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
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for {@link EnergyMeter}. Column names match V17 exactly. */
@Entity
@Table(name = "energy_meters", schema = "facilities")
public class EnergyMeterRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "building_code", nullable = false, length = 40)
    private String buildingCode;
    @Column(name = "room_id")
    private UUID roomId;
    @Column(name = "meter_code", nullable = false, length = 80)
    private String meterCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Utility utility;
    @Column(name = "canonical_unit", nullable = false, length = 10)
    private String canonicalUnit;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeterSource source;
    @Column(name = "avamp_asset_id", length = 120)
    private String avampAssetId;
    @Column(name = "vendor_meter_ref", length = 120)
    private String vendorMeterRef;
    @Column(name = "bms_device_id")
    private UUID bmsDeviceId;
    @Column(name = "expected_interval_minutes", nullable = false)
    private int expectedIntervalMinutes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeterStatus status;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "retired_reason", length = 1000)
    private String retiredReason;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EnergyMeterRecord() {
    }

    static EnergyMeterRecord empty() {
        return new EnergyMeterRecord();
    }

    void apply(EnergyMeter meter) {
        id = meter.id();
        siteCode = meter.siteCode();
        buildingCode = meter.buildingCode();
        roomId = meter.roomId();
        meterCode = meter.meterCode();
        name = meter.name();
        utility = meter.utility();
        canonicalUnit = meter.utility().unitCode();
        source = meter.source();
        avampAssetId = meter.avampAssetId();
        vendorMeterRef = meter.vendorMeterRef();
        bmsDeviceId = meter.bmsDeviceId();
        expectedIntervalMinutes = meter.expectedIntervalMinutes();
        status = meter.status();
        retiredAt = meter.retiredAt();
        retiredReason = meter.retiredReason();
        metadata = RecordMetadataEmbeddable.from(meter.metadata());
    }

    EnergyMeter toDomain() {
        return new EnergyMeter(id, siteCode, buildingCode, roomId, meterCode, name, utility, source, avampAssetId,
                vendorMeterRef, bmsDeviceId, expectedIntervalMinutes, status, retiredAt, retiredReason,
                metadata.toDomain(recordVersion()));
    }
}
