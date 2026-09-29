package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyTariff;
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

/** JPA mapping for {@link EnergyTariff}. Insert-only. */
@Entity
@Table(name = "energy_tariffs", schema = "facilities")
public class EnergyTariffRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Utility utility;
    @Column(nullable = false)
    private int version;
    @Column(name = "unit_rate", nullable = false, precision = 20, scale = 6)
    private BigDecimal unitRate;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_to")
    private LocalDate validTo;
    @Column(length = 1000)
    private String reason;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EnergyTariffRecord() {
    }

    static EnergyTariffRecord of(EnergyTariff tariff) {
        EnergyTariffRecord record = new EnergyTariffRecord();
        record.id = tariff.id();
        record.siteCode = tariff.siteCode();
        record.utility = tariff.utility();
        record.version = tariff.version();
        record.unitRate = tariff.unitRate();
        record.currency = tariff.currency();
        record.validFrom = tariff.validFrom();
        record.validTo = tariff.validTo();
        record.reason = tariff.reason();
        record.metadata = RecordMetadataEmbeddable.from(tariff.metadata());
        return record;
    }

    EnergyTariff toDomain() {
        return new EnergyTariff(id, siteCode, utility, version, unitRate, currency, validFrom, validTo, reason,
                metadata.toDomain(recordVersion()));
    }
}
