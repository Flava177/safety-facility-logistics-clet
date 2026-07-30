package gh.edu.clet.sfl.facilities.readiness.api;

import gh.edu.clet.sfl.facilities.masterdata.api.FacilitiesResponses;
import gh.edu.clet.sfl.facilities.masterdata.domain.LocationReadinessStatus;
import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSeverity;
import gh.edu.clet.sfl.facilities.readiness.domain.BlockerSource;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessment;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessBlocker;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklist;
import gh.edu.clet.sfl.facilities.readiness.domain.ReadinessOutcome;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The API's view of readiness. */
public final class ReadinessResponses {

    private ReadinessResponses() {
    }

    public record ChecklistResponse(
            UUID id,
            String siteCode,
            String checklistCode,
            String name,
            String description,
            SpaceType spaceType,
            OperatingMode operatingMode,
            int version,
            int totalWeight,
            List<ChecklistItemResponse> items,
            RecordLifecycleStatus lifecycleStatus,
            FacilitiesResponses.Metadata metadata) {

        public static ChecklistResponse from(ReadinessChecklist checklist) {
            return new ChecklistResponse(checklist.id(), checklist.siteCode(), checklist.checklistCode(),
                    checklist.name(), checklist.description(), checklist.spaceType(), checklist.operatingMode(),
                    checklist.version(), checklist.totalWeight(),
                    checklist.items().stream().map(ChecklistItemResponse::from).toList(),
                    checklist.lifecycleStatus(), FacilitiesResponses.Metadata.from(checklist.metadata()));
        }
    }

    public record ChecklistItemResponse(
            UUID id,
            String itemCode,
            String description,
            BlockerSeverity severityIfFailed,
            boolean mandatory,
            int weight,
            int sortOrder) {

        static ChecklistItemResponse from(
                gh.edu.clet.sfl.facilities.readiness.domain.ReadinessChecklistItem item) {
            return new ChecklistItemResponse(item.id(), item.itemCode(), item.description(),
                    item.severityIfFailed(), item.mandatory(), item.weight(), item.sortOrder());
        }
    }

    public record AssessmentResponse(
            UUID id,
            UUID roomId,
            String siteCode,
            UUID checklistId,
            String checklistCode,
            int checklistVersion,
            OperatingMode operatingMode,
            LocationReadinessStatus outcome,
            int score,
            boolean hasMandatoryFailure,
            List<AssessmentItemResponse> items,
            String notes,
            String assessedBy,
            Instant assessedAt) {

        public static AssessmentResponse from(ReadinessAssessment assessment) {
            return new AssessmentResponse(assessment.id(), assessment.roomId(), assessment.siteCode(),
                    assessment.checklistId(), assessment.checklistCode(), assessment.checklistVersion(),
                    assessment.operatingMode(), assessment.outcome(), assessment.score(),
                    assessment.hasMandatoryFailure(),
                    assessment.items().stream().map(AssessmentItemResponse::from).toList(),
                    assessment.notes(), assessment.assessedBy(), assessment.assessedAt());
        }
    }

    public record AssessmentItemResponse(
            UUID id,
            String itemCode,
            String description,
            BlockerSeverity severityIfFailed,
            boolean mandatory,
            int weight,
            boolean passed,
            String comment) {

        static AssessmentItemResponse from(
                gh.edu.clet.sfl.facilities.readiness.domain.ReadinessAssessmentItem item) {
            return new AssessmentItemResponse(item.id(), item.itemCode(), item.description(),
                    item.severityIfFailed(), item.mandatory(), item.weight(), item.passed(), item.comment());
        }
    }

    public record BlockerResponse(
            UUID id,
            UUID roomId,
            String siteCode,
            UUID assessmentId,
            BlockerSource source,
            String sourceReference,
            BlockerSeverity severity,
            String description,
            String raisedBy,
            Instant raisedAt,
            boolean resolved,
            String resolvedBy,
            Instant resolvedAt,
            String resolutionNotes) {

        public static BlockerResponse from(ReadinessBlocker blocker) {
            return new BlockerResponse(blocker.id(), blocker.roomId(), blocker.siteCode(),
                    blocker.assessmentId(), blocker.source(), blocker.sourceReference(), blocker.severity(),
                    blocker.description(), blocker.raisedBy(), blocker.raisedAt(), blocker.resolved(),
                    blocker.resolvedBy(), blocker.resolvedAt(), blocker.resolutionNotes());
        }
    }

    /** A space's current readiness together with the reasons for it. */
    public record OutcomeResponse(
            LocationReadinessStatus status,
            int score,
            String summary,
            int criticalCount,
            int majorCount,
            int minorCount,
            int advisoryCount,
            List<BlockerResponse> openBlockers) {

        public static OutcomeResponse from(ReadinessOutcome outcome) {
            return new OutcomeResponse(outcome.status(), outcome.score(), outcome.summary(),
                    outcome.criticalCount(), outcome.majorCount(), outcome.minorCount(),
                    outcome.advisoryCount(),
                    outcome.openBlockers().stream().map(BlockerResponse::from).toList());
        }
    }
}
