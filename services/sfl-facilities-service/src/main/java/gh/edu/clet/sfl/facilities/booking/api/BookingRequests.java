package gh.edu.clet.sfl.facilities.booking.api;

import gh.edu.clet.sfl.facilities.booking.domain.BookingPurpose;
import gh.edu.clet.sfl.facilities.booking.domain.ResourceCategory;
import gh.edu.clet.sfl.facilities.booking.domain.SetupTaskStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The S159 request bodies, with Bean Validation on every field the SRS constrains.
 *
 * <p>Validation is here as well as in the domain, and the duplication is deliberate — see
 * {@code MaintenanceRequests} for the reasoning. What is <em>not</em> duplicated is anything needing
 * more than one field or a clock: whether the window has already passed, whether it is inside the
 * booking horizon, and whether the space is free are all decisions with context, and putting them in
 * an annotation would put half the rule where nobody looks for it.
 */
public final class BookingRequests {

    private BookingRequests() {
    }

    /**
     * @param resources resource id to quantity. Attached in the same transaction as the booking, so a
     *        booking never briefly exists without the projector it was made for.
     * @param overrideReason only honoured when the space's readiness would otherwise refuse and the
     *        actor holds {@code FACILITIES_BOOKING_OVERRIDE}
     */
    public record RequestBooking(
            @NotNull UUID roomId,
            @NotNull BookingPurpose purpose,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Min(0) Integer setupMinutes,
            @Min(0) Integer teardownMinutes,
            @Min(0) int expectedAttendees,
            @Size(max = 200) String requestedFor,
            Map<UUID, Integer> resources,
            @Size(max = 2000) String overrideReason) {
    }

    public record DecideBooking(
            @NotNull Boolean approve,
            @Size(max = 2000) String reason,
            Long expectedVersion) {
    }

    public record RescheduleBooking(
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @Min(0) Integer setupMinutes,
            @Min(0) Integer teardownMinutes,
            @Size(max = 2000) String overrideReason,
            Long expectedVersion) {
    }

    public record TransitionBooking(
            @Size(max = 2000) String notes,
            Long expectedVersion) {
    }

    public record CancelBooking(
            @NotBlank @Size(max = 2000) String reason,
            Long expectedVersion) {
    }

    public record AllocateResources(
            @NotEmpty Map<UUID, Integer> resources) {
    }

    public record RegisterResource(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String resourceCode,
            @NotBlank @Size(max = 200) String name,
            @NotNull ResourceCategory category,
            @Size(max = 2000) String description,
            @Min(1) int quantity,
            UUID homeRoomId,
            UUID assetId,
            boolean requiresSetup) {
    }

    public record UpdateResource(
            @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @Min(1) Integer quantity,
            UUID homeRoomId,
            Boolean requiresSetup,
            Long expectedVersion) {
    }

    public record ChangeLifecycle(
            @NotNull RecordLifecycleStatus lifecycleStatus,
            Long expectedVersion) {
    }

    public record CreateSetupTasks(
            @NotEmpty List<NewSetupTask> tasks) {

        public record NewSetupTask(
                @NotBlank @Size(max = 500) String description,
                Instant dueBy,
                @Size(max = 160) String assignedTo) {
        }
    }

    public record ResolveSetupTask(
            @NotNull SetupTaskStatus outcome,
            @Size(max = 2000) String notes) {
    }
}
