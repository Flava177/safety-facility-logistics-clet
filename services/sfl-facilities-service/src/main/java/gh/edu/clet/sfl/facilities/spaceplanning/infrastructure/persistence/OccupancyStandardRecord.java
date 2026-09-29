package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import gh.edu.clet.sfl.facilities.spaceplanning.domain.OccupancyStandard;
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

/** JPA mapping for {@link OccupancyStandard}. */
@Entity
@Table(name = "occupancy_standards", schema = "facilities")
public class OccupancyStandardRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false, length = 40)
    private SpaceType spaceType;
    @Column(name = "version_number", nullable = false)
    private int versionNumber;
    @Column(name = "max_capacity_percent")
    private Integer maxCapacityPercent;
    @Column(name = "min_area_per_person_sqm", precision = 8, scale = 2)
    private BigDecimal minAreaPerPersonSqm;
    @Column(length = 2000)
    private String note;
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;
    @Column(name = "superseded_at")
    private Instant supersededAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected OccupancyStandardRecord() {
    }

    void apply(OccupancyStandard s) {
        id = s.id();
        siteCode = s.siteCode();
        spaceType = s.spaceType();
        versionNumber = s.versionNumber();
        maxCapacityPercent = s.maxCapacityPercent();
        minAreaPerPersonSqm = s.minAreaPerPersonSqm();
        note = s.note();
        effectiveFrom = s.effectiveFrom();
        supersededAt = s.supersededAt();
        metadata = RecordMetadataEmbeddable.from(s.metadata());
    }

    OccupancyStandard toDomain() {
        return new OccupancyStandard(id, siteCode, spaceType, versionNumber, maxCapacityPercent, minAreaPerPersonSqm,
                note, effectiveFrom, supersededAt, metadata.toDomain(recordVersion()));
    }
}
