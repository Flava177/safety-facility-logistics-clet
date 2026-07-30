package gh.edu.clet.sfl.facilities.masterdata.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An operational space — room, hall, moot courtroom, plant room (SRS-SFL-S152-01).
 *
 * <p>The single most-referenced record in IFIMP. S153 raises faults against it, S159 will book it,
 * S162a zones contain it, S173 stages events in it and the S152-05 dashboard reports on it. Its
 * attributes are therefore set by what those modules need to ask, not by what a room "has":
 *
 * <ul>
 *   <li>{@code spaceType} replaces the free-text room type, so "which spaces can host an examination"
 *       is answerable.</li>
 *   <li>{@code bookable} and {@code examinationCapable} default from the type and are overridable per
 *       space — a lecture hall under refurbishment is not bookable, whatever its type says.</li>
 *   <li>{@code readinessStatus} is <em>derived</em> from assessments and blockers, and the readiness
 *       module owns it. {@link #applyReadiness} is deliberately the only way in.</li>
 *   <li>{@code readinessLocked} is the examination lock (NFR 23.3): while set, attribute and readiness
 *       changes are refused without the override permission.</li>
 * </ul>
 *
 * <p>The class keeps its name rather than becoming {@code Space}. Six JPA entities, the maintenance
 * module, four tests and a 1 400-line dashboard page all say "room"; renaming the type without
 * renaming the concept everywhere would buy nothing and cost a large diff.
 */
public record FacilityRoom(
        UUID id,
        UUID floorId,
        String siteCode,
        String roomCode,
        String name,
        SpaceType spaceType,
        String roomType,
        Integer capacity,
        BigDecimal areaSqm,
        String costCentre,
        boolean bookable,
        boolean examinationCapable,
        LocationReadinessStatus readinessStatus,
        String readinessNotes,
        Instant readinessUpdatedAt,
        boolean readinessLocked,
        String readinessLockedBy,
        Instant readinessLockedAt,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public FacilityRoom {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(floorId, "floorId is required");
        Site.requireText(siteCode, "siteCode");
        Site.requireText(roomCode, "roomCode");
        Site.requireText(name, "name");
        Objects.requireNonNull(spaceType, "spaceType is required");
        Objects.requireNonNull(readinessStatus, "readinessStatus is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (capacity != null && capacity < 0) {
            throw new IllegalArgumentException("capacity cannot be negative");
        }
        if (areaSqm != null && areaSqm.signum() < 0) {
            throw new IllegalArgumentException("areaSqm cannot be negative");
        }
    }

    /** Registers a space. Readiness starts {@code UNKNOWN} — nothing has assessed it yet. */
    public static FacilityRoom create(UUID id, UUID floorId, String siteCode, String roomCode, String name,
            SpaceType spaceType, Integer capacity, BigDecimal areaSqm, String costCentre, Boolean bookable,
            Boolean examinationCapable, String actorId, Instant at, SourceChannel channel, String correlationId) {
        SpaceType type = spaceType == null ? SpaceType.OTHER : spaceType;
        return new FacilityRoom(id, floorId, Site.normalizeCode(siteCode), Site.normalizeCode(roomCode),
                name.strip(), type, type.name(), capacity, areaSqm, Site.blankToNull(costCentre),
                bookable == null ? type.isBookableByDefault() : bookable,
                examinationCapable == null ? type.isExaminationCapableByDefault() : examinationCapable,
                LocationReadinessStatus.UNKNOWN, null, null, false, null, null,
                RecordLifecycleStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Updates the space's own attributes.
     *
     * <p>Refuses while the readiness lock is engaged. Changing a locked hall's capacity mid-examination
     * is exactly the change NFR 23.3 exists to prevent, and the caller must release the lock — an
     * audited act — rather than edit around it.
     */
    public FacilityRoom update(String name, SpaceType spaceType, Integer capacity, BigDecimal areaSqm,
            String costCentre, Boolean bookable, Boolean examinationCapable, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        requireUnlocked();
        SpaceType type = spaceType == null ? this.spaceType : spaceType;
        return new FacilityRoom(id, floorId, siteCode, roomCode,
                name == null || name.isBlank() ? this.name : name.strip(),
                type, type.name(),
                capacity == null ? this.capacity : capacity,
                areaSqm == null ? this.areaSqm : areaSqm,
                costCentre == null ? this.costCentre : Site.blankToNull(costCentre),
                bookable == null ? this.bookable : bookable,
                examinationCapable == null ? this.examinationCapable : examinationCapable,
                readinessStatus, readinessNotes, readinessUpdatedAt,
                readinessLocked, readinessLockedBy, readinessLockedAt,
                lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * Applies a readiness outcome.
     *
     * <p>Package-visible intent rather than a general setter: readiness is <em>derived</em> from
     * assessments, blockers and asset status, and the readiness module is the only thing entitled to
     * decide it. A caller that could set {@code READY} directly could bypass the critical-blocker rule,
     * which is the one invariant this whole system exists to enforce.
     */
    public FacilityRoom applyReadiness(LocationReadinessStatus status, String notes, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(at, "assessedAt is required");
        return new FacilityRoom(id, floorId, siteCode, roomCode, name, spaceType, roomType, capacity, areaSqm,
                costCentre, bookable, examinationCapable, status, Site.blankToNull(notes), at,
                readinessLocked, readinessLockedBy, readinessLockedAt,
                lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Engages the examination readiness lock (NFR 23.3). Idempotent by refusal, not by silence. */
    public FacilityRoom lockReadiness(String actorId, Instant at, SourceChannel channel, String correlationId) {
        if (readinessLocked) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Space " + roomCode + " is already locked for examination use.");
        }
        return new FacilityRoom(id, floorId, siteCode, roomCode, name, spaceType, roomType, capacity, areaSqm,
                costCentre, bookable, examinationCapable, readinessStatus, readinessNotes, readinessUpdatedAt,
                true, actorId, at, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Releases the examination readiness lock. */
    public FacilityRoom unlockReadiness(String actorId, Instant at, SourceChannel channel, String correlationId) {
        if (!readinessLocked) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Space " + roomCode + " is not locked.");
        }
        return new FacilityRoom(id, floorId, siteCode, roomCode, name, spaceType, roomType, capacity, areaSqm,
                costCentre, bookable, examinationCapable, readinessStatus, readinessNotes, readinessUpdatedAt,
                false, null, null, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public FacilityRoom changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        requireUnlocked();
        return new FacilityRoom(id, floorId, siteCode, roomCode, name, spaceType, roomType, capacity, areaSqm,
                costCentre, bookable, examinationCapable, readinessStatus, readinessNotes, readinessUpdatedAt,
                readinessLocked, readinessLockedBy, readinessLockedAt,
                lifecycleStatus.transitionTo(target, "Space"),
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * {@code true} when this space can currently be offered for booking.
     *
     * <p>Three conditions, all necessary: it is flagged bookable, its record is operational, and its
     * readiness is not {@code BLOCKED}. {@code DEGRADED} still books — a hall with one failed projector
     * is usable and refusing it would be worse than warning about it.
     */
    public boolean availableForBooking() {
        return bookable && lifecycleStatus.isOperational() && readinessStatus != LocationReadinessStatus.BLOCKED;
    }

    /**
     * {@code true} when this space can currently host an examination.
     *
     * <p>Stricter than booking on purpose: {@code READY} is required outright, because "probably fine"
     * is not a standard an examination centre can run on.
     */
    public boolean availableForExamination() {
        return examinationCapable && lifecycleStatus.isOperational()
                && readinessStatus == LocationReadinessStatus.READY;
    }

    /** Creation time, preserved for the pre-S152 API shape. */
    public Instant createdAt() {
        return metadata.createdAt();
    }

    private void requireUnlocked() {
        if (readinessLocked) {
            throw new FacilitiesException.ReadinessLockedException(id);
        }
    }
}
