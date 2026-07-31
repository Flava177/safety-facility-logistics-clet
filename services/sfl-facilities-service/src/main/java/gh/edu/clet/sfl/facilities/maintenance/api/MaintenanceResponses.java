package gh.edu.clet.sfl.facilities.maintenance.api;

import gh.edu.clet.sfl.facilities.maintenance.application.MaintenanceEvidenceService;
import gh.edu.clet.sfl.facilities.maintenance.domain.EvidenceType;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFault;
import gh.edu.clet.sfl.facilities.maintenance.domain.FacilityFaultStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceEvidence;
import gh.edu.clet.sfl.facilities.maintenance.domain.MaintenanceVendor;
import gh.edu.clet.sfl.facilities.maintenance.domain.PreventiveMaintenanceSchedule;
import gh.edu.clet.sfl.facilities.maintenance.domain.RetentionClass;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrder;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderPart;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderType;
import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses.Metadata;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The S153 wire types.
 *
 * <p>Each carries a derived field or two the client would otherwise recompute — {@code overdue},
 * {@code minutesOverdue}, {@code dueForGeneration}. Those are decisions this service has already
 * made, and a client recomputing them is a client that will eventually disagree with the escalation
 * sweep about whether something is late.
 */
public final class MaintenanceResponses {

    private MaintenanceResponses() {
    }

    public record FaultResponse(
            UUID id,
            String faultNumber,
            String siteCode,
            UUID roomId,
            String locationCode,
            UUID assetId,
            String title,
            String description,
            String category,
            FaultPriority priority,
            FacilityFaultStatus status,
            boolean open,
            String reportedBy,
            Instant reportedAt,
            String triagedBy,
            Instant triagedAt,
            String triageNotes,
            UUID duplicateOfFaultId,
            UUID workOrderId,
            Instant slaDueAt,
            boolean overdue,
            int escalationLevel,
            Instant escalatedAt,
            boolean blockerRaised,
            Instant resolvedAt,
            String resolutionNotes,
            RecordLifecycleStatus lifecycleStatus,
            Metadata metadata) {

        public static FaultResponse from(FacilityFault fault, Clock clock) {
            return new FaultResponse(fault.id(), fault.faultNumber(), fault.siteCode(), fault.roomId(),
                    fault.locationCode(), fault.assetId(), fault.title(), fault.description(),
                    fault.category(), fault.priority(), fault.status(), fault.status().isOpen(),
                    fault.reportedBy(), fault.reportedAt(), fault.triagedBy(), fault.triagedAt(),
                    fault.triageNotes(), fault.duplicateOfFaultId(), fault.workOrderId(), fault.slaDueAt(),
                    fault.isOverdue(clock.instant()), fault.escalationLevel(), fault.escalatedAt(),
                    fault.blockerRaised(), fault.resolvedAt(), fault.resolutionNotes(),
                    fault.lifecycleStatus(), Metadata.from(fault.metadata()));
        }
    }

    public record WorkOrderResponse(
            UUID id,
            String workOrderNumber,
            WorkOrderType workOrderType,
            UUID facilityFaultId,
            String faultNumber,
            UUID scheduleId,
            String siteCode,
            UUID roomId,
            String locationCode,
            UUID assetId,
            String title,
            String description,
            FaultPriority priority,
            WorkOrderStatus status,
            boolean open,
            String assignedTo,
            UUID vendorId,
            Instant assignedAt,
            Instant startedAt,
            String holdReason,
            Instant heldAt,
            long totalHeldSeconds,
            Instant slaDueAt,
            boolean overdue,
            Long minutesOverdue,
            int escalationLevel,
            Instant escalatedAt,
            /** How many pieces of evidence closure needs. The client shows the gap, not the rule. */
            int evidenceRequired,
            Instant completedAt,
            String completionNotes,
            String closureNotes,
            String closedBy,
            Instant closedAt,
            String cancellationReason,
            RecordLifecycleStatus lifecycleStatus,
            Metadata metadata) {

        public static WorkOrderResponse from(WorkOrder order, Clock clock) {
            Instant now = clock.instant();
            var overdueBy = order.overdueBy(now);
            return new WorkOrderResponse(order.id(), order.workOrderNumber(), order.workOrderType(),
                    order.facilityFaultId(), order.faultNumber(), order.scheduleId(), order.siteCode(),
                    order.roomId(), order.locationCode(), order.assetId(), order.title(), order.description(),
                    order.priority(), order.status(), order.status().isOpen(), order.assignedTo(),
                    order.vendorId(), order.assignedAt(), order.startedAt(), order.holdReason(),
                    order.heldAt(), order.totalHeldSeconds(), order.slaDueAt(), order.isOverdue(now),
                    overdueBy == null ? null : overdueBy.toMinutes(), order.escalationLevel(),
                    order.escalatedAt(), order.evidenceRequired(), order.completedAt(),
                    order.completionNotes(), order.closureNotes(), order.closedBy(), order.closedAt(),
                    order.cancellationReason(), order.lifecycleStatus(), Metadata.from(order.metadata()));
        }
    }

    public record PartResponse(
            UUID id,
            UUID workOrderId,
            String partCode,
            String description,
            int quantity,
            BigDecimal unitCost,
            BigDecimal lineCost,
            String currency,
            String supplier,
            String recordedBy,
            Instant recordedAt) {

        public static PartResponse from(WorkOrderPart part) {
            return new PartResponse(part.id(), part.workOrderId(), part.partCode(), part.description(),
                    part.quantity(), part.unitCost(), part.lineCost(), part.currency(), part.supplier(),
                    part.recordedBy(), part.recordedAt());
        }
    }

    public record EvidenceResponse(
            UUID id,
            UUID workOrderId,
            String siteCode,
            EvidenceType evidenceType,
            String fileReference,
            String fileName,
            String mediaType,
            Long sizeBytes,
            String contentHash,
            RetentionClass retentionClass,
            boolean legalHold,
            /** {@code null} while a legal hold is in force, which is how a hold reads to a client. */
            LocalDate disposalEligibleFrom,
            boolean supportsClosure,
            String notes,
            String uploadedBy,
            Instant uploadedAt) {

        public static EvidenceResponse from(MaintenanceEvidence evidence) {
            return new EvidenceResponse(evidence.id(), evidence.workOrderId(), evidence.siteCode(),
                    evidence.evidenceType(), evidence.fileReference(), evidence.fileName(),
                    evidence.mediaType(), evidence.sizeBytes(), evidence.contentHash(),
                    evidence.retentionClass(), evidence.legalHold(), evidence.disposalEligibleFrom(),
                    evidence.supportsClosure(), evidence.notes(), evidence.uploadedBy(),
                    evidence.uploadedAt());
        }
    }

    public record ExportGrantResponse(
            UUID evidenceId,
            String fileReference,
            String contentHash,
            RetentionClass retentionClass,
            String recipient,
            String reason,
            String approvedBy,
            Instant approvedAt) {

        public static ExportGrantResponse from(MaintenanceEvidenceService.ExportGrant grant) {
            return new ExportGrantResponse(grant.evidenceId(), grant.fileReference(), grant.contentHash(),
                    grant.retentionClass(), grant.recipient(), grant.reason(), grant.approvedBy(),
                    grant.approvedAt());
        }
    }

    public record VendorResponse(
            UUID id,
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
            boolean assignable,
            String unassignableReason,
            RecordLifecycleStatus lifecycleStatus,
            Metadata metadata) {

        public static VendorResponse from(MaintenanceVendor vendor, LocalDate today) {
            return new VendorResponse(vendor.id(), vendor.siteCode(), vendor.vendorCode(), vendor.name(),
                    vendor.specialisation(), vendor.contactName(), vendor.contactEmail(),
                    vendor.contactPhone(), vendor.responseHours(), vendor.contractReference(),
                    vendor.contractExpiresOn(), vendor.externalVendorId(), vendor.isAssignable(today),
                    vendor.unassignableReason(today), vendor.lifecycleStatus(),
                    Metadata.from(vendor.metadata()));
        }
    }

    public record ScheduleResponse(
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
            LocalDate generateOn,
            LocalDate lastGeneratedFor,
            Instant lastGeneratedAt,
            UUID lastWorkOrderId,
            boolean dueForGeneration,
            RecordLifecycleStatus lifecycleStatus,
            Metadata metadata) {

        public static ScheduleResponse from(PreventiveMaintenanceSchedule schedule, LocalDate today) {
            return new ScheduleResponse(schedule.id(), schedule.siteCode(), schedule.scheduleCode(),
                    schedule.name(), schedule.description(), schedule.assetId(), schedule.roomId(),
                    schedule.intervalDays(), schedule.leadTimeDays(), schedule.priority(),
                    schedule.workOrderType(), schedule.nextDueOn(), schedule.generateOn(),
                    schedule.lastGeneratedFor(), schedule.lastGeneratedAt(), schedule.lastWorkOrderId(),
                    schedule.isDueForGeneration(today), schedule.lifecycleStatus(),
                    Metadata.from(schedule.metadata()));
        }
    }

    /** What one escalation sweep did, for an operator who triggered it by hand. */
    public record EscalationSweepResponse(
            Instant evaluatedAt,
            int faultsEscalated,
            int workOrdersEscalated,
            int total) {
    }

    /** What one preventive-generation run raised. */
    public record GenerationRunResponse(
            LocalDate generatedFor,
            int workOrdersRaised,
            java.util.List<WorkOrderResponse> workOrders) {
    }
}
