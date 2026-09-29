package gh.edu.clet.sfl.facilities.cleaning.api;

import gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFrequency;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The S169 request bodies. Single-field rules are annotated here and again in the domain, as in
 * {@code BookingRequests}; anything needing context (the frequency/day agreement, the checklist gate,
 * the booking resolution) is left to the domain and services where it can be tested.
 */
public final class CleaningRequests {

    private CleaningRequests() {
    }

    public record CreateSchedule(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 200) String name,
            @NotNull SpaceType spaceType,
            UUID roomId,
            @NotNull CleaningFrequency frequency,
            Set<DayOfWeek> daysOfWeek,
            @NotEmpty List<LocalTime> timesOfDay,
            @Min(5) @Max(1440) int durationMinutes) {
    }

    public record UpdateSchedule(
            @NotBlank @Size(max = 200) String name,
            @NotNull CleaningFrequency frequency,
            Set<DayOfWeek> daysOfWeek,
            @NotEmpty List<LocalTime> timesOfDay,
            @Min(5) @Max(1440) int durationMinutes,
            boolean active,
            Long expectedVersion) {
    }

    public record ChecklistItem(
            @NotBlank @Size(max = 60) String itemCode,
            @NotBlank @Size(max = 300) String label,
            boolean photoRequired) {
    }

    public record CreateTemplate(
            @NotBlank @Size(max = 40) String siteCode,
            @NotNull SpaceType spaceType,
            @NotBlank @Size(max = 200) String name,
            @NotEmpty List<@Valid ChecklistItem> items) {
    }

    /**
     * @param origin ADHOC, BOOKING_SETUP or BOOKING_TEARDOWN; the booking origins require {@code bookingId}
     *        and take their window from the booking
     */
    public record RaiseTask(
            UUID roomId,
            TaskOrigin origin,
            @Size(max = 200) String title,
            @Size(max = 2000) String description,
            UUID bookingId,
            Instant windowStart,
            Instant dueBy) {
    }

    public record RaiseRequest(
            @NotNull UUID roomId,
            @NotBlank @Size(max = 2000) String description,
            UUID bookingId) {
    }

    public record AssignTask(
            @NotNull AssigneeType assigneeType,
            @NotBlank @Size(max = 160) String assignedTo,
            UUID vendorId,
            Long expectedVersion) {
    }

    public record StartTask(Long expectedVersion) {
    }

    /**
     * @param photoReference a reference to the stored image - never the image itself
     * @param photoContentHash SHA-256 of the stored bytes, 64 hexadecimal characters
     */
    public record RecordChecklistItem(
            boolean done,
            @Size(max = 500) String photoReference,
            @Pattern(regexp = "^[0-9a-fA-F]{64}$") String photoContentHash,
            @Size(max = 1000) String notes) {
    }

    /**
     * @param vendorReportedCompletedAt what the vendor says; stored separately and never used as the
     *        completion time (SRS-SFL-S169-03)
     */
    public record CompleteTask(
            @Size(max = 2000) String notes,
            Instant vendorReportedCompletedAt,
            Long expectedVersion) {
    }

    public record CancelTask(@NotBlank @Size(max = 2000) String reason, Long expectedVersion) {
    }

    public record SubmitFeedback(@Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {
    }

    public record ReviewFlag(@NotBlank @Size(max = 2000) String notes) {
    }

    public record RecordVendorMasterReference(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String reference,
            @NotBlank @Size(max = 200) String legalName,
            @Size(max = 1000) String evidenceNote) {
    }

    public record RegisterVendor(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String vendorMasterReference) {
    }

    public record ChangeVendorStatus(@NotNull VendorStatus status) {
    }

    public record SetSlaTerms(
            @Min(1) int responseMinutes,
            @Min(1) int completionMinutes,
            @NotNull @DecimalMin("1.00") @DecimalMax("5.00") BigDecimal qualityFloor) {
    }
}
