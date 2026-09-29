package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.EmissionFactor;
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

/** JPA mapping for {@link EmissionFactor}. Insert-only. */
@Entity
@Table(name = "energy_emission_factors", schema = "facilities")
public class EnergyEmissionFactorRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Utility utility;
    @Column(nullable = false)
    private int version;
    @Column(name = "kg_co2e_per_unit", nullable = false, precision = 20, scale = 6)
    private BigDecimal kgCo2ePerUnit;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "source_reference", length = 500)
    private String sourceReference;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected EnergyEmissionFactorRecord() {
    }

    static EnergyEmissionFactorRecord of(EmissionFactor factor) {
        EnergyEmissionFactorRecord record = new EnergyEmissionFactorRecord();
        record.id = factor.id();
        record.siteCode = factor.siteCode();
        record.utility = factor.utility();
        record.version = factor.version();
        record.kgCo2ePerUnit = factor.kgCo2ePerUnit();
        record.validFrom = factor.validFrom();
        record.sourceReference = factor.sourceReference();
        record.metadata = RecordMetadataEmbeddable.from(factor.metadata());
        return record;
    }

    EmissionFactor toDomain() {
        return new EmissionFactor(id, siteCode, utility, version, kgCo2ePerUnit, validFrom, sourceReference,
                metadata.toDomain(recordVersion()));
    }
}
