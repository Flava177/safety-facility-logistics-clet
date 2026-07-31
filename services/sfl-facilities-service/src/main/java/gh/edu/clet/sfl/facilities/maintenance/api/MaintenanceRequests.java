package gh.edu.clet.sfl.facilities.maintenance.api;

import gh.edu.clet.sfl.facilities.maintenance.domain.EvidenceType;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.RetentionClass;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderType;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The S153 request bodies, with Bean Validation on every field the SRS constrains.
 *
 * <p>Validation is here as well as in the domain, and the duplication is deliberate. Bean Validation
 * gives a field-level 400 naming the offending property, which is what a form needs; the domain gives
 * an invariant that holds however the aggregate is reached. Removing either would lose something: the
 * first, a usable error; the second, the guarantee.
 */
public final class MaintenanceRequests {

    private MaintenanceRequests() {
    }

    public record ReportFault(
            @Size(max = 40) String siteCode,
            UUID roomId,
            @Size(max = 120) String locationCode,
            UUID assetId,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 4000) String description,
            @Size(max = 120) String category,
            @NotNull FaultPriority priority) {
    }

    public record TriageFault(
            FaultPriority priority,
            @Size(max = 2000) String notes,
            Long expectedVersion) {
    }

    public record DismissFault(
            @NotNull FacilityFaultStatus outcome,
            @NotBlank @Size(max = 2000) String reason,
            UUID duplicateOfFaultId,
            Long expectedVersion) {
    }

    public record ChangeLifecycle(
            @NotNull RecordLifecycleStatus lifecycleStatus,
            Long expectedVersion) {
    }

    public record CreateWorkOrder(
            @NotNull UUID facilityFaultId,
            UUID vendorId,
            @Size(max = 160) String assignTo) {
    }

    public record AssignWorkOrder(
            @NotBlank @Size(max = 160) String assignedTo,
            UUID vendorId,
            Long expectedVersion) {
    }

    /** Start, hold, complete and reopen. {@code notes} is required for hold and reopen. */
    public record TransitionWorkOrder(
            @Size(max = 2000) String notes,
            Long expectedVersion) {
    }

    public record CloseWorkOrder(
            @NotBlank @Size(max = 2000) String closureNotes,
            Long expectedVersion) {
    }

    public record CancelWorkOrder(
            @NotBlank @Size(max = 2000) String reason,
            Long expectedVersion) {
    }

    public record RecordPart(
            @NotBlank @Size(max = 80) String partCode,
            @NotBlank @Size(max = 400) String description,
            @Positive int quantity,
            BigDecimal unitCost,
            @Size(min = 3, max = 3) String currency,
            @Size(max = 200) String supplier) {
    }

    public record AttachEvidence(
            @NotNull EvidenceType evidenceType,
            @NotBlank @Size(max = 500) String fileReference,
            @Size(max = 300) String fileName,
            @Size(max = 120) String mediaType,
            @Min(0) Long sizeBytes,
            // Rejected at the edge as well as in the domain, because a mistyped digest is the one
            // error that would otherwise be discovered years later, by an integrity check, on
            // evidence nobody can now re-hash.
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$",
                    message = "must be a 64-character hex SHA-256 digest") String contentHash,
            @NotNull RetentionClass retentionClass,
            @Size(max = 2000) String notes) {
    }

    public record ExportEvidence(
            @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Size(max = 200) String recipient) {
    }

    public record SetLegalHold(
            boolean legalHold,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record RegisterVendor(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String vendorCode,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String specialisation,
            @Size(max = 200) String contactName,
            @Size(max = 200) String contactEmail,
            @Size(max = 60) String contactPhone,
            @Positive Integer responseHours,
            @Size(max = 120) String contractReference,
            LocalDate contractExpiresOn,
            @Size(max = 120) String externalVendorId) {
    }

    public record UpdateVendor(
            @Size(max = 200) String name,
            @Size(max = 200) String specialisation,
            @Size(max = 200) String contactName,
            @Size(max = 200) String contactEmail,
            @Size(max = 60) String contactPhone,
            @Positive Integer responseHours,
            @Size(max = 120) String contractReference,
            LocalDate contractExpiresOn,
            Long expectedVersion) {
    }

    public record CreateSchedule(
            @NotBlank @Size(max = 40) String siteCode,
            @NotBlank @Size(max = 80) String scheduleCode,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @NotNull UUID assetId,
            @Positive int intervalDays,
            @Min(0) int leadTimeDays,
            @NotNull FaultPriority priority,
            @NotNull WorkOrderType workOrderType,
            @NotNull LocalDate firstDueOn) {
    }

    public record UpdateSchedule(
            @Size(max = 200) String name,
            @Size(max = 2000) String description,
            @Positive Integer intervalDays,
            @Min(0) Integer leadTimeDays,
            FaultPriority priority,
            LocalDate nextDueOn,
            Long expectedVersion) {
    }
}
