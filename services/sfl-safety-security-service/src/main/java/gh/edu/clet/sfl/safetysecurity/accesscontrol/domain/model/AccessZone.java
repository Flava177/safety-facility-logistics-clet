package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A managed access zone, its time schedule and its door groups - SRS-SFL-S160a-05. Door groups are
 * held as simple named sets of door ids on the zone itself rather than as a separate aggregate: the
 * SRS describes them only as "sets of doors governed together" within a zone's own rules, and a
 * standalone door-group lifecycle (its own approvals, its own audit trail) is not asked for anywhere
 * in S160a-01..06 - see the implementation notes for this deliberate scope cut.
 *
 * @param locationRef the S152 facility-register reference for this zone, held by value (a plain id) -
 *        never a cross-schema foreign key; S152 is a different service's schema entirely.
 * @param schedule a plain description of when access is permitted (e.g. "Mon-Fri 07:00-19:00"). Kept
 *        as free text rather than a structured schedule DSL - proportionate for Phase 1, and the
 *        vendor gateway receives exactly this string to enforce, unchanged.
 * @param doorGroups door-group name -> the door ids it governs.
 * @param examinationMode {@code true} while Examination Mode has tightened this zone's schedule and
 *        locked the door groups named in {@link #lockedDoorGroups} for the exam window.
 */
public record AccessZone(UUID id, String siteCode, String zoneCode, String name, String locationRef,
        String schedule, java.util.Map<String, List<String>> doorGroups, boolean examinationMode,
        List<String> lockedDoorGroups, Instant examinationUntil, RecordMetadata metadata) {

    public AccessZone {
        Objects.requireNonNull(id, "id is required");
        require(siteCode, "siteCode");
        require(zoneCode, "zoneCode");
        require(name, "name");
        locationRef = blankToNull(locationRef);
        require(schedule, "schedule");
        doorGroups = doorGroups == null ? java.util.Map.of() : java.util.Map.copyOf(doorGroups);
        lockedDoorGroups = lockedDoorGroups == null ? List.of() : List.copyOf(lockedDoorGroups);
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static AccessZone define(UUID id, String siteCode, String zoneCode, String name, String locationRef,
            String schedule, java.util.Map<String, List<String>> doorGroups, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new AccessZone(id, siteCode, zoneCode, name, locationRef, schedule, doorGroups, false, List.of(), null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /** Redefines the schedule/door groups - SRS-SFL-S160a-05: "changes are versioned and audited". */
    public AccessZone redefine(String schedule, java.util.Map<String, List<String>> doorGroups, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new AccessZone(id, siteCode, zoneCode, name, locationRef, schedule, doorGroups, examinationMode,
                lockedDoorGroups, examinationUntil, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** SRS-SFL-S160a-05: Examination Mode tightens this zone's schedule and locks the named door groups
     * for the exam window, reverting automatically once {@code until} passes. */
    public AccessZone enterExaminationMode(String tightenedSchedule, List<String> doorGroupsToLock, Instant until,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new AccessZone(id, siteCode, zoneCode, name, locationRef, tightenedSchedule, doorGroups, true,
                List.copyOf(doorGroupsToLock), until, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public AccessZone exitExaminationMode(String normalSchedule, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new AccessZone(id, siteCode, zoneCode, name, locationRef, normalSchedule, doorGroups, false, List.of(),
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
