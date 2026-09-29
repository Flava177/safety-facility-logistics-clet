package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of the occupancy standard for a space type at a site - SRS-SFL-S158-02.
 *
 * <p>"Standards are configurable per space type (office, hall, lab, residence)." The space types are
 * S152's {@link SpaceType} - office is {@code OFFICE}, hall {@code LECTURE_HALL} / {@code EXAMINATION_HALL}
 * / {@code AUDITORIUM}, lab {@code LABORATORY}, residence {@code ACCOMMODATION} - so a standard can never
 * name a type the register does not have.
 *
 * <h2>The two criteria</h2>
 *
 * <ul>
 *   <li>{@link #maxCapacityPercent} - the allocated headcount may be at most this share of the room's
 *       recorded capacity. 100 means "no more people than the room holds"; 80 leaves slack.</li>
 *   <li>{@link #minAreaPerPersonSqm} - each person gets at least this much floor area. Only
 *       evaluable where S152 records the room's {@code areaSqm}.</li>
 * </ul>
 * At least one is required; a standard with neither would check nothing and report compliant, which is
 * the "compliant by default" the SRS forbids by another route.
 *
 * <h2>Versioned, not edited</h2>
 *
 * Defining a standard supersedes the active version rather than overwriting it, so a committed scenario's
 * {@code standardVersion} still says what it was checked against.
 */
public record OccupancyStandard(
        UUID id,
        String siteCode,
        SpaceType spaceType,
        int versionNumber,
        Integer maxCapacityPercent,
        BigDecimal minAreaPerPersonSqm,
        String note,
        Instant effectiveFrom,
        Instant supersededAt,
        RecordMetadata metadata) {

    public OccupancyStandard {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(spaceType, "spaceType is required");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        Objects.requireNonNull(metadata, "metadata is required");
        siteCode = AllocationScenario.requireText(siteCode, "siteCode", 40);
        note = AllocationScenario.optional(note, "note", 2000);
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber starts at 1");
        }
        if (maxCapacityPercent == null && minAreaPerPersonSqm == null) {
            throw new IllegalArgumentException(
                    "An occupancy standard must set a maximum share of capacity, a minimum area per person, or both.");
        }
        if (maxCapacityPercent != null && (maxCapacityPercent < 1 || maxCapacityPercent > 200)) {
            throw new IllegalArgumentException("maxCapacityPercent must be between 1 and 200");
        }
        if (minAreaPerPersonSqm != null && minAreaPerPersonSqm.signum() <= 0) {
            throw new IllegalArgumentException("minAreaPerPersonSqm must be positive");
        }
    }

    public static OccupancyStandard define(UUID id, String siteCode, SpaceType spaceType, int versionNumber,
            Integer maxCapacityPercent, BigDecimal minAreaPerPersonSqm, String note, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new OccupancyStandard(id, siteCode, spaceType, versionNumber, maxCapacityPercent, minAreaPerPersonSqm,
                note, at, null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public OccupancyStandard supersede(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new OccupancyStandard(id, siteCode, spaceType, versionNumber, maxCapacityPercent, minAreaPerPersonSqm,
                note, effectiveFrom, at, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public boolean isActive() {
        return supersededAt == null;
    }
}
