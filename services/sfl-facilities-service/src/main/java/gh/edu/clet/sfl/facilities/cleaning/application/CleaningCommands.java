package gh.edu.clet.sfl.facilities.cleaning.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFrequency;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The S169 commands. Each carries its actor and channel, as every facilities command does. */
public final class CleaningCommands {

    private CleaningCommands() {
    }

    // ---- S169-01 schedules and tasks ----------------------------------------------------------

    public record CreateSchedule(String siteCode, String name, SpaceType spaceType, UUID roomId,
            CleaningFrequency frequency, Set<DayOfWeek> daysOfWeek, List<LocalTime> timesOfDay, int durationMinutes,
            ActorContext actor, SourceChannel channel) {
    }

    public record UpdateSchedule(UUID scheduleId, String name, CleaningFrequency frequency, Set<DayOfWeek> daysOfWeek,
            List<LocalTime> timesOfDay, int durationMinutes, boolean active, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    /**
     * A supervisor-raised task. {@code origin} is ADHOC, or BOOKING_SETUP / BOOKING_TEARDOWN for a booking
     * that needs a clean the automatic path did not raise - and then {@code bookingId} must resolve in
     * S159 (SRS-SFL-S169-01 error state).
     */
    public record RaiseTask(UUID roomId, TaskOrigin origin, String title, String description, UUID bookingId,
            Instant windowStart, Instant dueBy, ActorContext actor, SourceChannel channel, String idempotencyKey,
            Object idempotencyPayload) {
    }

    /** A reactive request from any occupant. {@code bookingId} is optional and, if given, must resolve. */
    public record RaiseRequest(UUID roomId, String description, UUID bookingId, ActorContext actor,
            SourceChannel channel, String idempotencyKey, Object idempotencyPayload) {
    }

    // ---- S169-02 execution --------------------------------------------------------------------

    public record ChecklistItemSpec(String itemCode, String label, boolean photoRequired) {
    }

    public record CreateTemplate(String siteCode, SpaceType spaceType, String name, List<ChecklistItemSpec> items,
            ActorContext actor, SourceChannel channel) {
    }

    public record AssignTask(UUID taskId, AssigneeType assigneeType, String assignedTo, UUID vendorId,
            Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record StartTask(UUID taskId, Long expectedVersion, ActorContext actor, SourceChannel channel) {
    }

    public record RecordChecklistItem(UUID taskId, UUID itemId, boolean done, String photoReference,
            String photoContentHash, String notes, ActorContext actor, SourceChannel channel) {
    }

    /**
     * @param vendorReportedCompletedAt what the vendor says; stored separately, never the completion time
     */
    public record CompleteTask(UUID taskId, String notes, Instant vendorReportedCompletedAt, Long expectedVersion,
            ActorContext actor, SourceChannel channel) {
    }

    public record CancelTask(UUID taskId, String reason, Long expectedVersion, ActorContext actor,
            SourceChannel channel) {
    }

    public record SubmitFeedback(UUID taskId, int rating, String comment, ActorContext actor, SourceChannel channel) {
    }

    public record ReviewFlag(UUID flagId, String notes, ActorContext actor, SourceChannel channel) {
    }

    // ---- S169-03 vendors ----------------------------------------------------------------------

    public record RecordVendorMasterReference(String siteCode, String reference, String legalName,
            String evidenceNote, ActorContext actor, SourceChannel channel) {
    }

    public record RegisterVendor(String siteCode, String vendorMasterReference, ActorContext actor,
            SourceChannel channel) {
    }

    public record ChangeVendorStatus(UUID vendorId, VendorStatus status, ActorContext actor, SourceChannel channel) {
    }

    public record SetSlaTerms(UUID vendorId, int responseMinutes, int completionMinutes, BigDecimal qualityFloor,
            ActorContext actor, SourceChannel channel) {
    }
}
