package gh.edu.clet.sfl.facilities.maintenance.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A standing instruction to service an asset every so often.
 *
 * <p>S152 already carries {@code serviceIntervalDays} and {@code lastServicedOn} on
 * {@code FacilityAsset}, and its dashboard already counts what is overdue. What was missing was any
 * way to <em>act</em> on that: nothing generated the work, and nothing recorded that a service had
 * happened, so the interval could only ever be set at registration and then watched.
 *
 * <p>This schedule closes that loop. It generates a work order ahead of the due date, and closing
 * that work order moves the asset's {@code lastServicedOn} — which re-derives {@code serviceDueOn}
 * and updates the dashboard without anybody editing the asset by hand.
 *
 * <h2>Why generation is idempotent, and how</h2>
 *
 * The generator is scheduled and at-least-once. Two runs on the same day, or a restart mid-run, must
 * not produce two work orders for one service. {@link #lastGeneratedFor} records the due date most
 * recently generated for, and {@link #isDueForGeneration} refuses to generate for a date already
 * covered. That makes the key "one schedule, one cycle" rather than "one schedule, one run" —
 * important because a run that fails partway is retried, and a run that is late still generates for
 * the cycle it missed rather than skipping it.
 *
 * @param intervalDays how often the service is due.
 * @param leadTimeDays how far ahead of the due date the work order is raised, so somebody has notice.
 * @param nextDueOn the next date the service is due. Advanced by {@link #intervalDays} on generation.
 */
public record PreventiveMaintenanceSchedule(
        UUID id,
        String siteCode,
        String scheduleCode,
        String name,
        String description,
        UUID assetId,
        UUID roomId,
        int intervalDays,
        int leadTimeDays,
        FaultPriority priority,
        WorkOrderType workOrderType,
        LocalDate nextDueOn,
        LocalDate lastGeneratedFor,
        Instant lastGeneratedAt,
        UUID lastWorkOrderId,
        RecordLifecycleStatus lifecycleStatus,
        RecordMetadata metadata) {

    public PreventiveMaintenanceSchedule {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        scheduleCode = EstateCodes.normalize(scheduleCode);
        EstateCodes.require(name, "name");
        name = name.strip();
        description = EstateCodes.blankToNull(description);
        Objects.requireNonNull(assetId, "assetId is required");
        Objects.requireNonNull(priority, "priority is required");
        Objects.requireNonNull(workOrderType, "workOrderType is required");
        Objects.requireNonNull(nextDueOn, "nextDueOn is required");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (intervalDays <= 0) {
            throw new IllegalArgumentException("intervalDays must be greater than zero");
        }
        if (leadTimeDays < 0) {
            throw new IllegalArgumentException("leadTimeDays cannot be negative");
        }
        if (leadTimeDays >= intervalDays) {
            // A lead time at or beyond the interval means the next order is raised before the last
            // one could have been done, so the queue fills with overlapping duplicates forever.
            throw new IllegalArgumentException("leadTimeDays must be shorter than intervalDays");
        }
        if (workOrderType == WorkOrderType.CORRECTIVE) {
            throw new IllegalArgumentException("a schedule cannot generate corrective work orders");
        }
    }

    public static PreventiveMaintenanceSchedule create(UUID id, String siteCode, String scheduleCode, String name,
            String description, UUID assetId, UUID roomId, int intervalDays, int leadTimeDays,
            FaultPriority priority, WorkOrderType workOrderType, LocalDate firstDueOn, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new PreventiveMaintenanceSchedule(id, siteCode, scheduleCode, name, description, assetId, roomId,
                intervalDays, leadTimeDays, priority, workOrderType, firstDueOn, null, null, null,
                RecordLifecycleStatus.ACTIVE, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    public PreventiveMaintenanceSchedule update(String newName, String newDescription, Integer newIntervalDays,
            Integer newLeadTimeDays, FaultPriority newPriority, LocalDate newNextDueOn, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        return new PreventiveMaintenanceSchedule(id, siteCode, scheduleCode,
                newName == null || newName.isBlank() ? name : newName,
                newDescription == null ? description : newDescription,
                assetId, roomId,
                newIntervalDays == null ? intervalDays : newIntervalDays,
                newLeadTimeDays == null ? leadTimeDays : newLeadTimeDays,
                newPriority == null ? priority : newPriority,
                workOrderType,
                newNextDueOn == null ? nextDueOn : newNextDueOn,
                lastGeneratedFor, lastGeneratedAt, lastWorkOrderId, lifecycleStatus,
                metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /**
     * Whether a work order should be generated today.
     *
     * <p>Two conditions, and the second is the idempotency: the due date must be within the lead-time
     * window, <em>and</em> this cycle must not already have been generated for. A schedule that has
     * generated for {@link #nextDueOn} answers {@code false} however often it is asked.
     */
    public boolean isDueForGeneration(LocalDate today) {
        if (!lifecycleStatus.isOperational()) {
            return false;
        }
        if (lastGeneratedFor != null && !lastGeneratedFor.isBefore(nextDueOn)) {
            return false;
        }
        return !today.isBefore(nextDueOn.minusDays(leadTimeDays));
    }

    /**
     * Records that this cycle has been generated, and advances to the next one.
     *
     * <p>The next due date is computed from the cycle just generated, not from today. A generator
     * that ran three days late must not push every subsequent service three days later — a quarterly
     * inspection would drift out of its quarter within a year.
     */
    public PreventiveMaintenanceSchedule markGenerated(UUID workOrderId, Instant at, String actorId,
            SourceChannel channel, String correlationId) {
        return new PreventiveMaintenanceSchedule(id, siteCode, scheduleCode, name, description, assetId, roomId,
                intervalDays, leadTimeDays, priority, workOrderType, nextDueOn.plusDays(intervalDays), nextDueOn,
                at, workOrderId, lifecycleStatus, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    public PreventiveMaintenanceSchedule changeLifecycle(RecordLifecycleStatus target, String actorId, Instant at,
            SourceChannel channel, String correlationId) {
        RecordLifecycleStatus next = lifecycleStatus.transitionTo(target, "Preventive schedule");
        return new PreventiveMaintenanceSchedule(id, siteCode, scheduleCode, name, description, assetId, roomId,
                intervalDays, leadTimeDays, priority, workOrderType, nextDueOn, lastGeneratedFor, lastGeneratedAt,
                lastWorkOrderId, next, metadata.modifiedBy(actorId, at, channel, correlationId));
    }

    /** The date the work order should be raised on. */
    public LocalDate generateOn() {
        return nextDueOn.minusDays(leadTimeDays);
    }
}
