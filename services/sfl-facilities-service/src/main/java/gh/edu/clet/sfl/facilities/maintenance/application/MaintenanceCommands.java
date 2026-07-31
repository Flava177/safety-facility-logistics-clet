package gh.edu.clet.sfl.facilities.maintenance.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.maintenance.domain.EvidenceType;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.RetentionClass;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderType;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Every write this module accepts, as one command per use case.
 *
 * <p>Same shape as {@code ReadinessCommands}: each carries its own {@link ActorContext} and
 * {@link SourceChannel} rather than taking them as extra parameters, so a command is a complete
 * description of "who asked for what, through which channel" and can be logged, replayed or audited
 * without reassembling it from a method signature.
 *
 * <p>{@code idempotencyKey} and {@code idempotencyPayload} appear only on the state-<em>creating</em>
 * commands. A transition is already guarded by the record's version and its state machine, so a
 * repeated PATCH is either a no-op or an invalid-transition error; a key there would be ceremony with
 * no failure mode behind it.
 */
public final class MaintenanceCommands {

    private MaintenanceCommands() {
    }

    // ---- faults -------------------------------------------------------------------------------

    public record ReportFault(
            String siteCode,
            UUID roomId,
            String locationCode,
            UUID assetId,
            String title,
            String description,
            String category,
            FaultPriority priority,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey,
            Object idempotencyPayload) {
    }

    /** Confirms or corrects the priority and starts the SLA clock. SRS-SFL-S153-02. */
    public record TriageFault(
            UUID faultId,
            FaultPriority priority,
            String notes,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    /** Rejection, duplication or withdrawal. The reason is mandatory in all three cases. */
    public record DismissFault(
            UUID faultId,
            FacilityFaultStatus outcome,
            String reason,
            UUID duplicateOfFaultId,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ChangeFaultLifecycle(
            UUID faultId,
            RecordLifecycleStatus lifecycleStatus,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- work orders --------------------------------------------------------------------------

    public record CreateWorkOrderFromFault(
            UUID faultId,
            UUID vendorId,
            String assignTo,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey,
            Object idempotencyPayload) {
    }

    public record AssignWorkOrder(
            UUID workOrderId,
            String assignedTo,
            UUID vendorId,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    /**
     * The transitions that carry no payload beyond a note: start, hold, complete, reopen.
     *
     * <p>One command for four moves because the authorisation, the version check, the audit record
     * and the event are identical, and only the aggregate method differs. Four near-identical
     * commands would be four places to forget the version check.
     */
    public record TransitionWorkOrder(
            UUID workOrderId,
            Transition transition,
            String notes,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {

        public enum Transition {
            START, HOLD, COMPLETE, REOPEN
        }
    }

    public record CloseWorkOrder(
            UUID workOrderId,
            String closureNotes,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record CancelWorkOrder(
            UUID workOrderId,
            String reason,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- parts --------------------------------------------------------------------------------

    public record RecordPart(
            UUID workOrderId,
            String partCode,
            String description,
            int quantity,
            BigDecimal unitCost,
            String currency,
            String supplier,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record RemovePart(
            UUID workOrderId,
            UUID partId,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- evidence -----------------------------------------------------------------------------

    /**
     * Attaches evidence by reference. SRS-SFL-S153-03.
     *
     * <p>{@code contentHash} is supplied by the caller because this service never holds the bytes —
     * the upload goes to object storage, which returns the reference and the digest. Recomputing it
     * here would mean downloading every file this service is deliberately not storing.
     */
    public record AttachEvidence(
            UUID workOrderId,
            EvidenceType evidenceType,
            String fileReference,
            String fileName,
            String mediaType,
            Long sizeBytes,
            String contentHash,
            RetentionClass retentionClass,
            String notes,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey,
            Object idempotencyPayload) {
    }

    /** Export requires an approved reason, and the export itself is audited. SRS-SFL-S153-03. */
    public record ExportEvidence(
            UUID evidenceId,
            String reason,
            String recipient,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record SetLegalHold(
            UUID evidenceId,
            boolean legalHold,
            String reason,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- vendors ------------------------------------------------------------------------------

    public record RegisterVendor(
            String siteCode,
            String vendorCode,
            String name,
            String specialisation,
            String contactName,
            String contactEmail,
            String contactPhone,
            Integer responseHours,
            String contractReference,
            LocalDate contractExpiresOn,
            String externalVendorId,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey,
            Object idempotencyPayload) {
    }

    public record UpdateVendor(
            UUID vendorId,
            String name,
            String specialisation,
            String contactName,
            String contactEmail,
            String contactPhone,
            Integer responseHours,
            String contractReference,
            LocalDate contractExpiresOn,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ChangeVendorLifecycle(
            UUID vendorId,
            RecordLifecycleStatus lifecycleStatus,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    // ---- preventive schedules -------------------------------------------------------------------

    public record CreateSchedule(
            String siteCode,
            String scheduleCode,
            String name,
            String description,
            UUID assetId,
            int intervalDays,
            int leadTimeDays,
            FaultPriority priority,
            WorkOrderType workOrderType,
            LocalDate firstDueOn,
            ActorContext actor,
            SourceChannel channel,
            String idempotencyKey,
            Object idempotencyPayload) {
    }

    public record UpdateSchedule(
            UUID scheduleId,
            String name,
            String description,
            Integer intervalDays,
            Integer leadTimeDays,
            FaultPriority priority,
            LocalDate nextDueOn,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }

    public record ChangeScheduleLifecycle(
            UUID scheduleId,
            RecordLifecycleStatus lifecycleStatus,
            Long expectedVersion,
            ActorContext actor,
            SourceChannel channel) {
    }
}
