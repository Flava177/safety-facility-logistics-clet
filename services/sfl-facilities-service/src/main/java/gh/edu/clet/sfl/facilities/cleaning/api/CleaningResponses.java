package gh.edu.clet.sfl.facilities.cleaning.api;

import gh.edu.clet.sfl.facilities.cleaning.application.CleaningCapacityService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningDashboardService;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningVendorService;
import gh.edu.clet.sfl.facilities.cleaning.application.ports.VendorMasterPort;
import gh.edu.clet.sfl.facilities.cleaning.domain.AssigneeType;
import gh.edu.clet.sfl.facilities.cleaning.domain.CapacityReservation;
import gh.edu.clet.sfl.facilities.cleaning.domain.ChecklistTemplate;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningFeedback;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningSchedule;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningTask;
import gh.edu.clet.sfl.facilities.cleaning.domain.CleaningVendor;
import gh.edu.clet.sfl.facilities.cleaning.domain.LowRatingFlag;
import gh.edu.clet.sfl.facilities.cleaning.domain.PhotoEvidence;
import gh.edu.clet.sfl.facilities.cleaning.domain.SlaBreach;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskChecklistItem;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskOrigin;
import gh.edu.clet.sfl.facilities.cleaning.domain.TaskStatus;
import gh.edu.clet.sfl.facilities.cleaning.domain.VendorSlaTerms;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The S169 wire types. Each carries what the domain already decided rather than leaving a client to recompute it. */
public final class CleaningResponses {

    private CleaningResponses() {
    }

    public record ScheduleResponse(UUID id, String siteCode, String name, SpaceType spaceType, UUID roomId,
            String frequency, Set<DayOfWeek> daysOfWeek, List<LocalTime> timesOfDay, int durationMinutes,
            boolean active, long version) {

        public static ScheduleResponse from(CleaningSchedule schedule) {
            return new ScheduleResponse(schedule.id(), schedule.siteCode(), schedule.name(), schedule.spaceType(),
                    schedule.roomId(), schedule.frequency().name(), schedule.daysOfWeek(), schedule.timesOfDay(),
                    schedule.durationMinutes(), schedule.active(), schedule.metadata().version());
        }
    }

    public record TemplateItemResponse(UUID id, String itemCode, String label, int sequence, boolean photoRequired) {

        static TemplateItemResponse from(ChecklistTemplate.Item item) {
            return new TemplateItemResponse(item.id(), item.itemCode(), item.label(), item.sequence(),
                    item.photoRequired());
        }
    }

    public record TemplateResponse(UUID id, String siteCode, SpaceType spaceType, String name, int version,
            boolean active, List<TemplateItemResponse> items) {

        public static TemplateResponse from(ChecklistTemplate template) {
            return new TemplateResponse(template.id(), template.siteCode(), template.spaceType(), template.name(),
                    template.version(), template.active(), template.items().stream()
                            .map(TemplateItemResponse::from).toList());
        }
    }

    public record ChecklistItemResponse(UUID id, String itemCode, String label, int sequence, boolean photoRequired,
            boolean done, String doneBy, Instant doneAt, String photoReference, String photoContentHash,
            String notes) {

        public static ChecklistItemResponse from(TaskChecklistItem item) {
            PhotoEvidence photo = item.photo();
            return new ChecklistItemResponse(item.id(), item.itemCode(), item.label(), item.sequence(),
                    item.photoRequired(), item.done(), item.doneBy(), item.doneAt(),
                    photo == null ? null : photo.reference(), photo == null ? null : photo.contentHash(),
                    item.notes());
        }
    }

    public record TaskResponse(UUID id, String taskNumber, String siteCode, UUID roomId, String roomCode,
            SpaceType spaceType, TaskOrigin origin, String title, String description, UUID scheduleId,
            Instant occurrenceStart, UUID bookingId, String bookingReference, UUID reservationId,
            String eventReference, Instant windowStart, Instant dueBy, TaskStatus status, boolean overdue,
            String requestedBy, Instant requestedAt, AssigneeType assigneeType, String assignedTo, UUID vendorId,
            Instant assignedAt, Instant startedAt, Instant completedAt, String completedBy, String completionNotes,
            Instant vendorReportedCompletedAt, Long completionDiscrepancySeconds, Instant cancelledAt,
            String cancellationReason, UUID checklistTemplateId, Integer checklistTemplateVersion, long version) {

        public static TaskResponse from(CleaningTask task, Instant now) {
            return new TaskResponse(task.id(), task.taskNumber(), task.siteCode(), task.roomId(), task.roomCode(),
                    task.spaceType(), task.origin(), task.title(), task.description(), task.scheduleId(),
                    task.occurrenceStart(), task.bookingId(), task.bookingReference(), task.reservationId(),
                    task.eventReference(), task.windowStart(), task.dueBy(), task.status(), task.isOverdueAt(now),
                    task.requestedBy(), task.requestedAt(), task.assigneeType(), task.assignedTo(), task.vendorId(),
                    task.assignedAt(), task.startedAt(), task.completedAt(), task.completedBy(),
                    task.completionNotes(), task.vendorReportedCompletedAt(), task.completionDiscrepancySeconds(),
                    task.cancelledAt(), task.cancellationReason(), task.checklistTemplateId(),
                    task.checklistTemplateVersion(), task.metadata().version());
        }
    }

    public record FeedbackResponse(UUID id, UUID taskId, UUID roomId, UUID vendorId, int rating, String comment,
            String submittedBy, Instant submittedAt) {

        public static FeedbackResponse from(CleaningFeedback feedback) {
            return new FeedbackResponse(feedback.id(), feedback.taskId(), feedback.roomId(), feedback.vendorId(),
                    feedback.rating(), feedback.comment(), feedback.submittedBy(), feedback.submittedAt());
        }
    }

    public record FlagResponse(UUID id, String siteCode, String subjectType, UUID subjectId, String subjectLabel,
            int lowRatingCount, Instant windowStart, boolean open, Instant flaggedAt, String reviewedBy,
            Instant reviewedAt, String reviewNotes, long version) {

        public static FlagResponse from(LowRatingFlag flag) {
            return new FlagResponse(flag.id(), flag.siteCode(), flag.subjectType().name(), flag.subjectId(),
                    flag.subjectLabel(), flag.lowRatingCount(), flag.windowStart(), flag.open(), flag.flaggedAt(),
                    flag.reviewedBy(), flag.reviewedAt(), flag.reviewNotes(), flag.metadata().version());
        }
    }

    public record VendorMasterReferenceResponse(String reference, String legalName, boolean active,
            String integrationStatus) {

        public static VendorMasterReferenceResponse from(VendorMasterPort.VendorMasterRecord record,
                String integrationStatus) {
            return new VendorMasterReferenceResponse(record.reference(), record.legalName(), record.active(),
                    integrationStatus);
        }
    }

    public record VendorResponse(UUID id, String siteCode, String vendorMasterReference, String name, String status,
            long version) {

        public static VendorResponse from(CleaningVendor vendor) {
            return new VendorResponse(vendor.id(), vendor.siteCode(), vendor.vendorMasterReference(), vendor.name(),
                    vendor.status().name(), vendor.metadata().version());
        }
    }

    public record SlaTermsResponse(UUID id, UUID vendorId, int version, int responseMinutes, int completionMinutes,
            BigDecimal qualityFloor, Instant effectiveFrom, Instant effectiveTo) {

        public static SlaTermsResponse from(VendorSlaTerms terms) {
            return new SlaTermsResponse(terms.id(), terms.vendorId(), terms.version(), terms.responseMinutes(),
                    terms.completionMinutes(), terms.qualityFloor(), terms.effectiveFrom(), terms.effectiveTo());
        }
    }

    public record BreachResponse(UUID id, UUID vendorId, UUID taskId, String type, BigDecimal contractedValue,
            BigDecimal actualValue, String basis, Instant recordedAt) {

        public static BreachResponse from(SlaBreach breach) {
            return new BreachResponse(breach.id(), breach.vendorId(), breach.taskId(), breach.type().name(),
                    breach.contractedValue(), breach.actualValue(), breach.basis(), breach.recordedAt());
        }
    }

    public record ScorecardResponse(UUID vendorId, String vendorName, SlaTermsResponse currentTerms, Instant from,
            Instant to, int tasksAssigned, int tasksCompleted, int responseBreaches, int completionBreaches,
            int qualityBreaches, BigDecimal compliancePercent, int ratings, BigDecimal averageRating,
            int completionDiscrepancies, List<BreachResponse> breaches, String vendorMasterStatus) {

        public static ScorecardResponse from(CleaningVendorService.Scorecard card) {
            return new ScorecardResponse(card.vendor().id(), card.vendor().name(),
                    card.currentTerms() == null ? null : SlaTermsResponse.from(card.currentTerms()), card.from(),
                    card.to(), card.tasksAssigned(), card.tasksCompleted(), card.responseBreaches(),
                    card.completionBreaches(), card.qualityBreaches(), card.compliancePercent(), card.ratings(),
                    card.averageRating(), card.completionDiscrepancies(),
                    card.breaches().stream().map(BreachResponse::from).toList(), card.vendorMasterStatus());
        }
    }

    public record CommitmentResponse(UUID taskId, String taskNumber, UUID roomId, String roomCode, String origin,
            Instant from, Instant to, String assignedTo, UUID vendorId, String description) {

        public static CommitmentResponse from(CleaningCapacityService.Commitment commitment) {
            return new CommitmentResponse(commitment.taskId(), commitment.taskNumber(), commitment.roomId(),
                    commitment.roomCode(), commitment.origin().name(), commitment.from(), commitment.to(),
                    commitment.assignedTo(), commitment.vendorId(), commitment.description());
        }
    }

    public record ReservationResponse(UUID id, String siteCode, UUID roomId, String locationCode, Instant windowFrom,
            Instant windowTo, String eventReference, String requestedBy, String status, UUID taskId,
            String competingCommitment) {

        public static ReservationResponse from(CapacityReservation reservation) {
            return new ReservationResponse(reservation.id(), reservation.siteCode(), reservation.roomId(),
                    reservation.locationCode(), reservation.windowFrom(), reservation.windowTo(),
                    reservation.eventReference(), reservation.requestedBy(), reservation.status().name(),
                    reservation.taskId(), reservation.competingCommitment());
        }
    }

    public record CapacityViewResponse(String siteCode, Instant from, Instant to, int crews, int peakConcurrent,
            int crewsFreeAtPeak, List<CommitmentResponse> commitments, List<ReservationResponse> reservations) {

        public static CapacityViewResponse from(CleaningCapacityService.CapacityView view) {
            return new CapacityViewResponse(view.siteCode(), view.from(), view.to(), view.crews(),
                    view.peakConcurrent(), view.crewsFreeAtPeak(),
                    view.commitments().stream().map(CommitmentResponse::from).toList(),
                    view.reservations().stream().map(ReservationResponse::from).toList());
        }
    }

    public record OverdueRequestResponse(String taskNumber, String roomCode, Instant raisedAt, Instant dueBy,
            long minutesOverdue, String status) {

        static OverdueRequestResponse from(CleaningDashboardService.OverdueRequest overdue) {
            return new OverdueRequestResponse(overdue.taskNumber(), overdue.roomCode(), overdue.raisedAt(),
                    overdue.dueBy(), overdue.minutesOverdue(), overdue.status().name());
        }
    }

    public record VendorComplianceResponse(String vendorName, String vendorMasterReference, int tasksCompleted,
            BigDecimal compliancePercent, int responseBreaches, int completionBreaches, int qualityBreaches,
            BigDecimal averageRating) {

        static VendorComplianceResponse from(CleaningDashboardService.VendorCompliance compliance) {
            return new VendorComplianceResponse(compliance.vendorName(), compliance.vendorMasterReference(),
                    compliance.tasksCompleted(), compliance.compliancePercent(), compliance.responseBreaches(),
                    compliance.completionBreaches(), compliance.qualityBreaches(), compliance.averageRating());
        }
    }

    public record FeedbackPointResponse(String day, int ratings, BigDecimal averageRating, int lowRatings) {

        static FeedbackPointResponse from(CleaningDashboardService.FeedbackPoint point) {
            return new FeedbackPointResponse(point.day().toString(), point.ratings(), point.averageRating(),
                    point.lowRatings());
        }
    }

    public record SiteSummaryResponse(String siteCode, int scheduled, int completed, int cancelled,
            int reactiveRaised, int overdueReactiveCount, List<OverdueRequestResponse> overdueReactive,
            int tasksWithoutChecklist, List<VendorComplianceResponse> vendorCompliance,
            List<FeedbackPointResponse> feedbackTrend) {

        static SiteSummaryResponse from(CleaningDashboardService.SiteSummary summary) {
            return new SiteSummaryResponse(summary.siteCode(), summary.scheduled(), summary.completed(),
                    summary.cancelled(), summary.reactiveRaised(), summary.overdueReactiveCount(),
                    summary.overdueReactive().stream().map(OverdueRequestResponse::from).toList(),
                    summary.tasksWithoutChecklist(),
                    summary.vendorCompliance().stream().map(VendorComplianceResponse::from).toList(),
                    summary.feedbackTrend().stream().map(FeedbackPointResponse::from).toList());
        }
    }

    public record DashboardResponse(Instant from, Instant to, Instant generatedAt, List<SiteSummaryResponse> sites) {

        public static DashboardResponse from(CleaningDashboardService.Dashboard dashboard) {
            return new DashboardResponse(dashboard.from(), dashboard.to(), dashboard.generatedAt(),
                    dashboard.sites().stream().map(SiteSummaryResponse::from).toList());
        }
    }
}
