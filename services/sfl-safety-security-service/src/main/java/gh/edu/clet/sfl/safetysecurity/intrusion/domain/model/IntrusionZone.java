package gh.edu.clet.sfl.safetysecurity.intrusion.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A protected zone's arming schedule and confirmed state - SRS-SFL-S162-04. {@code armSchedule} is
 * kept as plain text, the same deliberate scope cut {@code accesscontrol.domain.model.AccessZone}
 * documents: a structured schedule DSL that could arm/disarm this zone automatically on a timer does
 * not exist yet in this codebase, so scheduled arming is confirmed explicitly (see
 * {@code IntrusionZoneService#confirmArmed}) rather than computed from the schedule text. Disarming
 * is always the explicit, time-bound, auditable path - see {@link DisarmOverride}.
 *
 * @param locationRef the S152 facility-register reference, held by value - never a cross-schema FK.
 * @param protectedZone {@code true} for vaults/after-hours areas whose alarms should be raised at
 *        elevated severity with a tighter acknowledgement window (SRS-SFL-S162-02).
 * @param armed the last-confirmed state reported by/for the panel - not a live poll.
 */
public record IntrusionZone(UUID id, String siteCode, String zoneCode, String name, String locationRef,
        String armSchedule, boolean protectedZone, boolean armed, boolean examinationMode, Instant examinationUntil,
        RecordMetadata metadata) {

    public IntrusionZone {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(zoneCode, "zoneCode");
        require(name, "name");
        locationRef = blankToNull(locationRef);
        require(armSchedule, "armSchedule");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static IntrusionZone define(UUID id, String siteCode, String zoneCode, String name, String locationRef,
            String armSchedule, boolean protectedZone, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new IntrusionZone(id, siteCode, zoneCode, name, locationRef, armSchedule, protectedZone, false, false,
                null, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** The panel confirms the zone armed - SRS-SFL-S162-04: "the zone is armed and its state confirmed
     * from the panel". Failure to arm is handled by the caller raising a SOC alarm, not by this method. */
    public IntrusionZone confirmArmed(String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new IntrusionZone(id, siteCode, zoneCode, name, locationRef, armSchedule, protectedZone, true,
                examinationMode, examinationUntil, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** Reached from an active {@link DisarmOverride} taking effect, or its automatic expiry restoring
     * the armed state - the same "system moves this, a person does not assert it directly" shape as
     * {@code AccessOverride.expire}. */
    public IntrusionZone setArmed(boolean nowArmed, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new IntrusionZone(id, siteCode, zoneCode, name, locationRef, armSchedule, protectedZone, nowArmed,
                examinationMode, examinationUntil, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** SRS-SFL-S162-04: "Examination Mode can enforce mandatory arming of examination-content zones". */
    public IntrusionZone enterExaminationMode(Instant until, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new IntrusionZone(id, siteCode, zoneCode, name, locationRef, armSchedule, protectedZone, true, true,
                until, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public IntrusionZone exitExaminationMode(String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new IntrusionZone(id, siteCode, zoneCode, name, locationRef, armSchedule, protectedZone, armed, false,
                null, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
