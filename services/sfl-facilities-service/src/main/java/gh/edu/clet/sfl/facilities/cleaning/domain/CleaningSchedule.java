package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A routine cleaning schedule - SRS-SFL-S169-01 "configurable by site, space type and frequency".
 *
 * <p>Written against a <em>space type</em> rather than a list of rooms, because that is how a rota is
 * actually specified ("every meeting room, daily at 07:00"), and a room added to the estate next term
 * is then covered without anybody remembering to add it here. {@code roomId} narrows a schedule to one
 * room for the exceptions ("the moot courtroom, twice a day during moots").
 *
 * <p>The frequency and the day list must agree, and the compact constructor refuses a schedule where
 * they do not. A weekly schedule with three days, or a daily one naming Tuesday, is a data-entry error
 * that would otherwise generate a rota nobody asked for.
 *
 * @param timesOfDay local times in the configured schedule time zone; each is one occurrence
 * @param durationMinutes how long one occurrence commits a crew - the task's window, and what S173
 *        capacity counts against
 */
public record CleaningSchedule(
        UUID id,
        String siteCode,
        String name,
        SpaceType spaceType,
        UUID roomId,
        CleaningFrequency frequency,
        Set<DayOfWeek> daysOfWeek,
        List<LocalTime> timesOfDay,
        int durationMinutes,
        boolean active,
        RecordMetadata metadata) {

    public CleaningSchedule {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        if (name == null || name.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("A cleaning schedule needs a name.");
        }
        name = name.strip();
        if (spaceType == null) {
            throw new FacilitiesException.ValidationFailedException("A cleaning schedule needs a space type.");
        }
        if (frequency == null) {
            throw new FacilitiesException.ValidationFailedException("A cleaning schedule needs a frequency.");
        }
        daysOfWeek = daysOfWeek == null || daysOfWeek.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(daysOfWeek));
        switch (frequency) {
            case DAILY -> {
                if (!daysOfWeek.isEmpty()) {
                    throw new FacilitiesException.ValidationFailedException(
                            "A daily schedule runs every day; do not name days of the week.");
                }
            }
            case WEEKLY -> {
                if (daysOfWeek.size() != 1) {
                    throw new FacilitiesException.ValidationFailedException(
                            "A weekly schedule runs on exactly one day of the week.");
                }
            }
            case SPECIFIC_WEEKDAYS -> {
                if (daysOfWeek.isEmpty()) {
                    throw new FacilitiesException.ValidationFailedException(
                            "Name at least one day of the week for a specific-weekdays schedule.");
                }
            }
            default -> throw new IllegalStateException("Unhandled frequency " + frequency);
        }
        if (timesOfDay == null || timesOfDay.isEmpty()) {
            throw new FacilitiesException.ValidationFailedException(
                    "A cleaning schedule needs at least one time of day.");
        }
        timesOfDay = timesOfDay.stream().distinct().sorted().toList();
        if (durationMinutes < 5 || durationMinutes > 1440) {
            throw new FacilitiesException.ValidationFailedException(
                    "A cleaning occurrence must last between 5 minutes and 24 hours.");
        }
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static CleaningSchedule create(UUID id, String siteCode, String name, SpaceType spaceType, UUID roomId,
            CleaningFrequency frequency, Set<DayOfWeek> daysOfWeek, List<LocalTime> timesOfDay,
            int durationMinutes, String actorId, Instant at, SourceChannel channel, String correlationId) {
        return new CleaningSchedule(id, siteCode, name, spaceType, roomId, frequency, daysOfWeek, timesOfDay,
                durationMinutes, true, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * Replaces the rota. Tasks already generated are left as they are: they are commitments somebody may
     * be planning around, and the next sweep generates the new pattern from then onward.
     */
    public CleaningSchedule update(String newName, CleaningFrequency newFrequency, Set<DayOfWeek> newDays,
            List<LocalTime> newTimes, int newDuration, boolean newActive, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        return new CleaningSchedule(id, siteCode, newName, spaceType, roomId, newFrequency, newDays, newTimes,
                newDuration, newActive, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** {@code true} when the schedule covers this room - its type, or exactly it if narrowed to one room. */
    public boolean covers(UUID candidateRoomId, SpaceType candidateType) {
        if (roomId != null) {
            return roomId.equals(candidateRoomId);
        }
        return spaceType == candidateType;
    }
}
