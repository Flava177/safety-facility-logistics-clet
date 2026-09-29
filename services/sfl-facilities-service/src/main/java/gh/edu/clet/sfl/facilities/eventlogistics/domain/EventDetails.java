package gh.edu.clet.sfl.facilities.eventlogistics.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The event as S078 describes it on a hand-off - SRS-SFL-S173-01: "the event's date, location, expected
 * attendance and stated requirements".
 *
 * <p>Carried onto the set-up task verbatim, so nobody re-keys it (the acceptance criterion). The room is
 * resolved against S152 before this is built, which is why it carries both the id and the code: the id
 * is what S159 books, the code is what S078 said and what a person reads.
 *
 * @param statedRequirements S078's own free text, kept as the record of what was asked for. The
 *        coordinator's decomposition into typed requests is what gets routed, never this.
 */
public record EventDetails(
        String title,
        String eventCategory,
        Instant startsAt,
        Instant endsAt,
        UUID roomId,
        String roomCode,
        int expectedAttendance,
        String statedRequirements,
        boolean externalContractors,
        boolean temporaryStructures) {

    public EventDetails {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (eventCategory == null || eventCategory.isBlank()) {
            throw new IllegalArgumentException("eventCategory is required");
        }
        Objects.requireNonNull(startsAt, "startsAt is required");
        Objects.requireNonNull(endsAt, "endsAt is required");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("the event must end after it starts");
        }
        Objects.requireNonNull(roomId, "roomId is required");
        if (expectedAttendance < 0) {
            throw new IllegalArgumentException("expectedAttendance cannot be negative");
        }
        title = title.strip();
        eventCategory = eventCategory.strip().toUpperCase(java.util.Locale.ROOT);
        roomCode = roomCode == null ? null : roomCode.strip().toUpperCase(java.util.Locale.ROOT);
        statedRequirements = statedRequirements == null || statedRequirements.isBlank() ? null
                : statedRequirements.strip();
    }

    /**
     * Whether a change from {@code previous} is one a confirmation depended on.
     *
     * <p>A new title or reworded requirements do not un-confirm an event. A new window, venue,
     * attendance, category or risk flag does: every one of them can change what is booked or whether
     * the event is higher-risk (S173-03).
     */
    public boolean materiallyDiffersFrom(EventDetails previous) {
        return !startsAt.equals(previous.startsAt) || !endsAt.equals(previous.endsAt)
                || !roomId.equals(previous.roomId) || expectedAttendance != previous.expectedAttendance
                || !eventCategory.equals(previous.eventCategory)
                || externalContractors != previous.externalContractors
                || temporaryStructures != previous.temporaryStructures;
    }

    /** Whether the booked window or space moved - what invalidates an S159 booking or S169 slot. */
    public boolean movesFrom(EventDetails previous) {
        return !startsAt.equals(previous.startsAt) || !endsAt.equals(previous.endsAt)
                || !roomId.equals(previous.roomId);
    }
}
